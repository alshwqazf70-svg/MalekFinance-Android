package com.malekfinance

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceRoomIntegrityTest {
    private lateinit var db: FinanceDb

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinanceDb::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun transferRejectsOverspendingAndConcurrentTransfersCannotSpendSameFundsTwice() = runBlocking {
        val sourceId = db.walletDao().insert(
            FinanceWallet(name = "المصدر", currencyCode = "YER", openingBalanceMinor = 1_000)
        )
        val firstDestinationId = db.walletDao().insert(
            FinanceWallet(name = "الوجهة الأولى", currencyCode = "YER")
        )
        val secondDestinationId = db.walletDao().insert(
            FinanceWallet(name = "الوجهة الثانية", currencyCode = "YER")
        )

        expectFailure {
            FinanceTransferOperations.transfer(db, sourceId, firstDestinationId, 1_001)
        }
        assertEquals(0, rowCount("transfers"))

        val outcomes = coroutineScope {
            listOf(
                async { runCatching { FinanceTransferOperations.transfer(db, sourceId, firstDestinationId, 700) } },
                async { runCatching { FinanceTransferOperations.transfer(db, sourceId, secondDestinationId, 700) } }
            ).awaitAll()
        }

        assertEquals("Only one concurrent withdrawal may consume the available balance", 1, outcomes.count { it.isSuccess })
        assertEquals(1, rowCount("transfers"))
        val source = db.walletDao().findById(sourceId)!!
        val sourceBalance = FinanceLedger.calculateBalance(
            source,
            db.dao().forWallet(sourceId),
            db.transferDao().forWallet(sourceId)
        )
        assertEquals(java.math.BigInteger.valueOf(300), sourceBalance)
    }

    @Test
    fun failedMultiStepRoomTransactionRollsBackEveryInsertedRow() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "محفظة اختبار", currencyCode = "YER")
        )
        expectFailure {
            db.withTransaction {
                db.dao().insert(
                    FinanceTransaction(
                        amountMinor = 250,
                        kind = "EXPENSE",
                        note = "must roll back",
                        walletId = walletId,
                        currencyCode = "YER"
                    )
                )
                error("Simulated failure after the first database write")
            }
        }

        assertEquals(0, rowCount("transactions"))
    }

    @Test
    fun recurringIncomeExpenseAndBillPaymentsRejectDuplicateOccurrencesAtomically() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "المحفظة", currencyCode = "YER"))
        val incomeRuleId = db.recurringIncomeRuleDao().insert(
            FinanceRecurringIncomeRule(
                title = "راتب",
                incomeType = "SALARY",
                amountMinor = 10_000,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "MONTHLY",
                anchorDay = 1,
                nextDueAt = 1_800_000_000_000
            )
        )
        val expenseRuleId = db.recurringExpenseRuleDao().insert(
            FinanceRecurringExpenseRule(
                title = "إيجار",
                categoryName = "سكن",
                amountMinor = 5_000,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "MONTHLY",
                anchorDay = 1,
                nextDueAt = 1_800_000_000_000
            )
        )
        val billId = db.billDao().insert(
            FinanceBill(
                title = "فاتورة",
                categoryName = "فواتير",
                amountMinor = 2_000,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "MONTHLY",
                anchorDay = 1,
                anchorMonth = 0,
                nextDueAt = 1_800_000_000_000
            )
        )

        db.recurringIncomeEntryDao().insert(
            FinanceRecurringIncomeEntry(ruleId = incomeRuleId, occurrenceKey = "2030-01-01", transactionId = 1001, amountMinor = 10_000)
        )
        db.recurringExpenseEntryDao().insert(
            FinanceRecurringExpenseEntry(ruleId = expenseRuleId, occurrenceKey = "2030-01-01", transactionId = 2001, amountMinor = 5_000)
        )
        db.billPaymentDao().insert(
            FinanceBillPayment(billId = billId, occurrenceKey = "2030-01-01", transactionId = 3001, amountMinor = 2_000)
        )
        val before = rowCount("transactions")

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(amountMinor = 100, kind = "INCOME", note = "duplicate salary", walletId = walletId)
                )
                db.recurringIncomeEntryDao().insert(
                    FinanceRecurringIncomeEntry(ruleId = incomeRuleId, occurrenceKey = "2030-01-01", transactionId = transactionId, amountMinor = 100)
                )
            }
        }
        assertEquals(before, rowCount("transactions"))

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(amountMinor = 100, kind = "EXPENSE", note = "duplicate rent", walletId = walletId)
                )
                db.recurringExpenseEntryDao().insert(
                    FinanceRecurringExpenseEntry(ruleId = expenseRuleId, occurrenceKey = "2030-01-01", transactionId = transactionId, amountMinor = 100)
                )
            }
        }
        assertEquals(before, rowCount("transactions"))

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(amountMinor = 100, kind = "EXPENSE", note = "duplicate bill", walletId = walletId)
                )
                db.billPaymentDao().insert(
                    FinanceBillPayment(billId = billId, occurrenceKey = "2030-01-01", transactionId = transactionId, amountMinor = 100)
                )
            }
        }
        assertEquals(before, rowCount("transactions"))
    }


    @Test
    fun debtAndSavingsLedgerLinksRollbackWhenTheirFinalInsertFails() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "المحفظة", currencyCode = "YER"))
        val debtId = db.debtDao().insert(
            FinanceDebt(
                personName = "شخص",
                description = "دين اختباري",
                debtType = "OWED_BY_ME",
                principalMinor = 1_000,
                currencyCode = "YER",
                walletId = walletId
            )
        )
        val goalId = db.savingsGoalDao().insert(
            FinanceSavingsGoal(
                name = "هدف",
                targetMinor = 2_000,
                currencyCode = "YER",
                walletId = walletId
            )
        )
        db.debtPaymentDao().insert(
            FinanceDebtPayment(debtId = debtId, transactionId = 7001, amountMinor = 100)
        )
        db.savingsEntryDao().insert(
            FinanceSavingsEntry(
                goalId = goalId,
                transactionId = 8001,
                entryType = "CONTRIBUTION",
                amountMinor = 200
            )
        )
        val before = rowCount("transactions")

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(amountMinor = 100, kind = "EXPENSE", note = "debt repayment", walletId = walletId)
                )
                db.debtPaymentDao().insert(
                    FinanceDebtPayment(debtId = debtId, transactionId = 7001, amountMinor = 100)
                )
            }
        }
        assertEquals(before, rowCount("transactions"))

        expectFailure {
            db.withTransaction {
                val transactionId = db.dao().insert(
                    FinanceTransaction(amountMinor = 200, kind = "EXPENSE", note = "savings contribution", walletId = walletId)
                )
                db.savingsEntryDao().insert(
                    FinanceSavingsEntry(
                        goalId = goalId,
                        transactionId = 8001,
                        entryType = "CONTRIBUTION",
                        amountMinor = 200
                    )
                )
            }
        }
        assertEquals(before, rowCount("transactions"))
    }


    @Test
    fun transferRejectsNonPositiveSameWalletAndCrossCurrencyRequests() = runBlocking {
        val yer = db.walletDao().insert(FinanceWallet(name = "ريال", currencyCode = "YER", openingBalanceMinor = 10_000))
        val usd = db.walletDao().insert(FinanceWallet(name = "دولار", currencyCode = "USD", openingBalanceMinor = 10_000))

        expectFailure { FinanceTransferOperations.transfer(db, yer, usd, 100) }
        expectFailure { FinanceTransferOperations.transfer(db, yer, yer, 100) }
        expectFailure { FinanceTransferOperations.transfer(db, yer, usd, 0) }
        expectFailure { FinanceTransferOperations.transfer(db, yer, usd, -1) }

        assertEquals(0, rowCount("transfers"))
    }


    @Test
    fun debtCreationIsAtomicAndRefusesUnfundedLending() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 1_000)
        )

        expectFailure {
            FinanceDebtOperations.create(
                db = db,
                personName = "أحمد",
                description = "سلفة",
                debtType = "OWED_TO_ME",
                principalMinor = 1_001,
                walletId = walletId,
                recordCashMovement = true
            )
        }
        assertEquals(0, rowCount("debts"))
        assertEquals(0, rowCount("transactions"))

        val debtId = FinanceDebtOperations.create(
            db = db,
            personName = "خالد",
            description = "قرض",
            debtType = "OWED_BY_ME",
            principalMinor = 500,
            walletId = walletId,
            recordCashMovement = true
        )
        val debt = db.debtDao().findById(debtId)!!
        val linked = db.dao().forWallet(walletId).single()
        assertEquals("OWED_BY_ME", debt.debtType)
        assertEquals(debtId, linked.debtId)
        assertEquals("DEBT_RECEIVED", linked.kind)
        assertEquals(500L, linked.amountMinor)
    }

    @Test
    fun debtCreationRollsBackDebtWhenLinkedCashMovementInsertFails() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 10_000)
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_debt_cash_movement BEFORE INSERT ON transactions " +
                "WHEN NEW.kind = 'DEBT_RECEIVED' BEGIN SELECT RAISE(ABORT, 'forced ledger failure'); END"
        )

        expectFailure {
            FinanceDebtOperations.create(
                db = db,
                personName = "أحمد",
                description = "اختبار التراجع الذري",
                debtType = "OWED_BY_ME",
                principalMinor = 500,
                walletId = walletId,
                recordCashMovement = true
            )
        }

        assertEquals(0, rowCount("debts"))
        assertEquals(0, rowCount("transactions"))
    }


    @Test
    fun debtInsertRollsBackWhenLinkedCashTransactionFails() = runBlocking {
        val walletId = db.walletDao().insert(FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 5_000))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_debt_cash_insert BEFORE INSERT ON transactions " +
                "BEGIN SELECT RAISE(ABORT, 'injected transaction failure'); END"
        )

        expectFailure {
            FinanceDebtOperations.create(
                db = db,
                personName = "أحمد",
                description = "قرض",
                debtType = "OWED_BY_ME",
                principalMinor = 1_000,
                walletId = walletId,
                recordCashMovement = true
            )
        }
        assertEquals(0, rowCount("debts"))
        assertEquals(0, rowCount("transactions"))
    }


    @Test
    fun savingsOperationsKeepGoalEntriesAndWalletLedgerAtomic() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 1_000)
        )
        val goalId = db.savingsGoalDao().insert(
            FinanceSavingsGoal(name = "طوارئ", targetMinor = 1_000, currencyCode = "YER", walletId = walletId)
        )

        expectFailure { FinanceSavingsOperations.contribute(db, goalId, 1_001) }
        assertEquals(0, rowCount("savings_entries"))
        assertEquals(0, rowCount("transactions"))

        FinanceSavingsOperations.contribute(db, goalId, 500)
        assertEquals("500", FinanceSavingsPolicy.balance(db.savingsEntryDao().forGoal(goalId)).toString())

        expectFailure { FinanceSavingsOperations.withdraw(db, goalId, 600) }
        assertEquals(1, rowCount("savings_entries"))

        FinanceSavingsOperations.withdraw(db, goalId, 200)
        assertEquals("300", FinanceSavingsPolicy.balance(db.savingsEntryDao().forGoal(goalId)).toString())
        assertEquals(2, rowCount("savings_entries"))
        assertEquals(2, rowCount("transactions"))
    }

    @Test
    fun savingsContributionRollsBackLedgerWhenEntryInsertFails() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 2_000)
        )
        val goalId = db.savingsGoalDao().insert(
            FinanceSavingsGoal(name = "هدف", targetMinor = 1_000, currencyCode = "YER", walletId = walletId)
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_savings_entry BEFORE INSERT ON savings_entries " +
                "BEGIN SELECT RAISE(ABORT, 'injected savings entry failure'); END"
        )

        expectFailure { FinanceSavingsOperations.contribute(db, goalId, 300) }
        assertEquals(0, rowCount("savings_entries"))
        assertEquals(0, rowCount("transactions"))
    }

    @Test
    fun debtPaymentServiceRejectsOverpaymentAndRecordsCashMovementAtomically() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 1_000)
        )
        val debtId = db.debtDao().insert(
            FinanceDebt(
                personName = "أحمد",
                description = "دين سابق",
                debtType = "OWED_BY_ME",
                principalMinor = 500,
                currencyCode = "YER",
                walletId = walletId,
                cashMovementRecorded = false
            )
        )

        FinanceDebtPaymentOperations.applyPayment(db, debtId, 300)
        expectFailure { FinanceDebtPaymentOperations.applyPayment(db, debtId, 201) }

        assertEquals(1, rowCount("debt_payments"))
        assertEquals(1, rowCount("transactions"))
        assertEquals("200", FinanceDebtPolicy.outstanding(500, db.debtPaymentDao().forDebt(debtId)).toString())
        val paymentTransaction = db.dao().forWallet(walletId).single()
        assertEquals("EXPENSE", paymentTransaction.kind)
        assertEquals(700L, FinanceLedger.calculateBalance(
            db.walletDao().findById(walletId)!!,
            db.dao().forWallet(walletId),
            emptyList()
        ).toLong())
    }

    @Test
    fun concurrentDebtPaymentsCannotSettleTheSameOutstandingAmountTwice() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 2_000)
        )
        val debtId = db.debtDao().insert(
            FinanceDebt(
                personName = "أحمد",
                description = "اختبار تزامن",
                debtType = "OWED_BY_ME",
                principalMinor = 500,
                currencyCode = "YER",
                walletId = walletId,
                cashMovementRecorded = false
            )
        )

        val outcomes = coroutineScope {
            listOf(
                async { runCatching { FinanceDebtPaymentOperations.applyPayment(db, debtId, 400) } },
                async { runCatching { FinanceDebtPaymentOperations.applyPayment(db, debtId, 400) } }
            ).awaitAll()
        }

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, rowCount("debt_payments"))
        assertEquals(1, rowCount("transactions"))
        assertEquals("100", FinanceDebtPolicy.outstanding(500, db.debtPaymentDao().forDebt(debtId)).toString())
    }

    @Test
    fun debtPaymentServiceRollsBackTransactionWhenPaymentLinkInsertFails() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 2_000)
        )
        val debtId = db.debtDao().insert(
            FinanceDebt(
                personName = "خالد",
                description = "دين",
                debtType = "OWED_BY_ME",
                principalMinor = 1_000,
                currencyCode = "YER",
                walletId = walletId,
                cashMovementRecorded = false
            )
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_debt_payment_link BEFORE INSERT ON debt_payments " +
                "BEGIN SELECT RAISE(ABORT, 'injected payment link failure'); END"
        )

        expectFailure { FinanceDebtPaymentOperations.applyPayment(db, debtId, 250) }
        assertEquals(0, rowCount("transactions"))
        assertEquals(0, rowCount("debt_payments"))
        assertEquals("1000", FinanceDebtPolicy.outstanding(1_000, db.debtPaymentDao().forDebt(debtId)).toString())
    }


    @Test
    fun billPaymentIsAtomicAndCannotBeRepeatedForTheSameOccurrence() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 1_000)
        )
        val dueAt = System.currentTimeMillis() - 86_400_000L
        val billId = db.billDao().insert(
            FinanceBill(
                title = "فاتورة كهرباء",
                categoryName = "فواتير",
                amountMinor = 800,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "ONCE",
                anchorDay = 1,
                anchorMonth = 0,
                nextDueAt = dueAt
            )
        )

        FinanceBillOperations.pay(db, billId)
        assertEquals(1, rowCount("bill_payments"))
        assertEquals(1, rowCount("transactions"))
        assertEquals(false, db.billDao().findById(billId)!!.isActive)
        expectFailure { FinanceBillOperations.pay(db, billId) }
        assertEquals(1, rowCount("bill_payments"))
        assertEquals(1, rowCount("transactions"))
    }

    @Test
    fun billPaymentRollsBackExpenseWhenPaymentRecordInsertFails() = runBlocking {
        val walletId = db.walletDao().insert(
            FinanceWallet(name = "النقد", currencyCode = "YER", openingBalanceMinor = 2_000)
        )
        val billId = db.billDao().insert(
            FinanceBill(
                title = "فاتورة ماء",
                categoryName = "فواتير",
                amountMinor = 500,
                currencyCode = "YER",
                walletId = walletId,
                frequency = "ONCE",
                anchorDay = 1,
                anchorMonth = 0,
                nextDueAt = System.currentTimeMillis() - 86_400_000L
            )
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_bill_payment BEFORE INSERT ON bill_payments " +
                "BEGIN SELECT RAISE(ABORT, 'injected bill payment failure'); END"
        )

        expectFailure { FinanceBillOperations.pay(db, billId) }
        assertEquals(0, rowCount("bill_payments"))
        assertEquals(0, rowCount("transactions"))
        assertEquals(true, db.billDao().findById(billId)!!.isActive)
    }

    @Test
    fun creditPurchaseHasTimestampAndNoCashMovementThenAccountSettlementUsesFundingWallet() = runBlocking {
        val reference = db.walletDao().insert(FinanceWallet(name = "مرجعية", openingBalanceMinor = 0))
        val funding = db.walletDao().insert(FinanceWallet(name = "الدفع", openingBalanceMinor = 500000))
        val yesterday = System.currentTimeMillis() - 86400000
        val first = FinanceDebtOperations.create(db, "عبده مهدي", "شراء آجل: قات", "OWED_BY_ME", 100000, reference, false, yesterday)
        val second = FinanceDebtOperations.create(db, "عبده مهدي", "شراء آجل: قات", "OWED_BY_ME", 200000, reference, false)
        assertEquals(yesterday, db.debtDao().findById(first)!!.createdAt)
        assertEquals(0, rowCount("transactions"))
        FinancePersonSettlementOperations.pay(db, "عبده مهدي", "YER", funding, 150000)
        assertEquals(2, rowCount("debt_payments"))
        assertEquals(0, db.dao().forWallet(reference).size)
        assertEquals(150000L, db.dao().forWallet(funding).sumOf { it.amountMinor })
        assertEquals(100000L, db.debtPaymentDao().forDebt(first).single().amountMinor)
        assertEquals(50000L, db.debtPaymentDao().forDebt(second).single().amountMinor)
    }

    @Test
    fun accountSettlementRollsBackEveryAllocationIfLaterPaymentFails() = runBlocking {
        val wallet = db.walletDao().insert(FinanceWallet(name = "نقد", openingBalanceMinor = 500000))
        FinanceDebtOperations.create(db, "عبده مهدي", "شراء آجل: قات", "OWED_BY_ME", 100000, wallet, false)
        val second = FinanceDebtOperations.create(db, "عبده مهدي", "شراء آجل: قات", "OWED_BY_ME", 100000, wallet, false)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_second_person_payment BEFORE INSERT ON debt_payments WHEN NEW.debtId = $second BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        expectFailure { FinancePersonSettlementOperations.pay(db, "عبده مهدي", "YER", wallet, 150000) }
        assertEquals(0, rowCount("transactions"))
        assertEquals(0, rowCount("debt_payments"))
    }

    @Test
    fun accountSettlementRejectsInsufficientBalanceAndOtherCurrencies() = runBlocking {
        val wallet = db.walletDao().insert(FinanceWallet(name = "نقد", openingBalanceMinor = 10))
        val usd = db.walletDao().insert(FinanceWallet(name = "دولار", currencyCode = "USD", openingBalanceMinor = 500000))
        FinanceDebtOperations.create(db, "عبده مهدي", "شراء آجل: قات", "OWED_BY_ME", 100000, wallet, false)
        expectFailure { FinancePersonSettlementOperations.pay(db, "عبده مهدي", "YER", wallet, 100000) }
        expectFailure { FinancePersonSettlementOperations.pay(db, "عبده مهدي", "YER", usd, 100000) }
        assertEquals(0, rowCount("transactions"))
    }

    @Test fun ordinaryMutationServiceRejectsLinkedAndStaleRows() = runBlocking {
        val wallet = db.walletDao().insert(FinanceWallet(name = "نقد"))
        val original = FinanceTransaction(amountMinor = 100, kind = "EXPENSE", note = "عادية", walletId = wallet)
        val id = db.dao().insert(original)
        val expected = original.copy(id = id)
        db.dao().update(expected.copy(note = "تغيرت"))
        expectFailure { FinanceTransactionOperations.delete(db, expected) }
        assertEquals("تغيرت", db.dao().findById(id)!!.note)
        val linked = db.dao().findById(id)!!.copy(debtId = 1L)
        db.dao().update(linked)
        expectFailure { FinanceTransactionOperations.delete(db, linked) }
        expectFailure { FinanceTransactionOperations.update(db, linked, 200, "INCOME", "محاولة تعديل", "عام") }
        assertEquals(100L, db.dao().findById(id)!!.amountMinor)
        db.dao().update(expected)
        FinanceTransactionOperations.delete(db, expected)
        assertEquals(0, rowCount("transactions"))
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Expected the database operation to fail", failed)
    }

    private fun rowCount(table: String): Int {
        require(table in setOf("transactions", "transfers", "debts", "savings_entries", "debt_payments", "bill_payments"))
        return db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }
}
