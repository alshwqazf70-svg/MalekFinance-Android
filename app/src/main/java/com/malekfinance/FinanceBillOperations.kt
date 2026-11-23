package com.malekfinance

import androidx.room.withTransaction

/** Atomically records a due bill payment, expense ledger row, and next occurrence. */
object FinanceBillOperations {
    suspend fun pay(db: FinanceDb, billId: Long, paidAt: Long = System.currentTimeMillis()): Long =
        db.withTransaction {
            val bill = db.billDao().findById(billId) ?: error("Bill not found")
            check(bill.isActive) { "Bill is inactive" }
            check(FinanceBillPolicy.isDue(bill.nextDueAt, paidAt)) { "Bill is not due yet" }
            val occurrence = FinanceBillPolicy.occurrenceKey(bill.nextDueAt)
            check(!db.billPaymentDao().exists(bill.id, occurrence)) { "Bill occurrence already paid" }
            val wallet = db.walletDao().findById(bill.walletId) ?: error("Wallet not found")
            check(wallet.currencyCode == bill.currencyCode)
            val transactions = db.dao().forWallet(wallet.id)
            val transfers = db.transferDao().forWallet(wallet.id)
            if (!FinanceLedger.hasSufficientFunds(wallet, bill.amountMinor, transactions, transfers)) {
                throw InsufficientWalletFundsException()
            }
            val transactionId = db.dao().insert(
                FinanceTransaction(
                    amountMinor = bill.amountMinor,
                    kind = "EXPENSE",
                    note = "دفع فاتورة: " + bill.title,
                    category = bill.categoryName,
                    createdAt = paidAt,
                    walletId = bill.walletId,
                    currencyCode = bill.currencyCode,
                    billId = bill.id
                )
            )
            db.billPaymentDao().insert(
                FinanceBillPayment(
                    billId = bill.id,
                    occurrenceKey = occurrence,
                    transactionId = transactionId,
                    amountMinor = bill.amountMinor,
                    paidAt = paidAt
                )
            )
            if (bill.frequency == "ONCE") {
                db.billDao().update(bill.copy(isActive = false))
            } else {
                val next = FinanceBillPolicy.nextDueAt(
                    bill.nextDueAt, bill.frequency, bill.anchorDay, bill.anchorMonth
                ) ?: error("Unsupported bill frequency")
                db.billDao().update(bill.copy(nextDueAt = next))
            }
            transactionId
        }
}
