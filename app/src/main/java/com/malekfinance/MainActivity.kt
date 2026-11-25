package com.malekfinance

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.malekfinance.ui.FinanceApp

@Entity(tableName = "transactions", indices = [Index(value = ["walletId"]), Index(value = ["debtId"]), Index(value = ["savingsGoalId"]), Index(value = ["recurringIncomeRuleId"]), Index(value = ["recurringExpenseRuleId"]), Index(value = ["billId"])])
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val kind: String,
    val note: String,
    @ColumnInfo(defaultValue = "'عام'") val category: String = "عام",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val walletId: Long = 1,
    @ColumnInfo(defaultValue = "'YER'") val currencyCode: String = "YER",
    @ColumnInfo(defaultValue = "NULL") val debtId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val savingsGoalId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val recurringIncomeRuleId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val recurringExpenseRuleId: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val billId: Long? = null
)

@Entity(tableName = "wallets")
data class FinanceWallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currencyCode: String = "YER",
    val openingBalanceMinor: Long = 0,
    val walletType: String = "CASH",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transfers",
    indices = [Index(value = ["fromWalletId"]), Index(value = ["toWalletId"])]
)
data class FinanceTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromWalletId: Long,
    val toWalletId: Long,
    val amountMinor: Long,
    val currencyCode: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observe(): Flow<List<FinanceTransaction>>

    @Query("SELECT * FROM transactions WHERE walletId = :walletId")
    suspend fun forWallet(walletId: Long): List<FinanceTransaction>
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FinanceTransaction?
    @Insert suspend fun insert(item: FinanceTransaction): Long
    @Update suspend fun update(item: FinanceTransaction)
    @Delete suspend fun delete(item: FinanceTransaction)
}

@Dao
interface FinanceWalletDao {
    @Query("SELECT * FROM wallets ORDER BY createdAt ASC, id ASC")
    fun observe(): Flow<List<FinanceWallet>>

    @Query("SELECT * FROM wallets WHERE id = :walletId LIMIT 1")
    suspend fun findById(walletId: Long): FinanceWallet?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wallet: FinanceWallet): Long

    @Update
    suspend fun update(wallet: FinanceWallet)
}

@Dao
interface FinanceTransferDao {
    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observe(): Flow<List<FinanceTransfer>>

    @Query("SELECT * FROM transfers WHERE fromWalletId = :walletId OR toWalletId = :walletId")
    suspend fun forWallet(walletId: Long): List<FinanceTransfer>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: FinanceTransfer): Long
}

@Database(
    entities = [FinanceTransaction::class, FinanceWallet::class, FinanceTransfer::class, FinanceCategory::class, FinanceBudget::class, FinanceDebt::class, FinanceDebtPayment::class, FinanceSavingsGoal::class, FinanceSavingsEntry::class, FinanceRecurringIncomeRule::class, FinanceRecurringIncomeEntry::class, FinanceRecurringExpenseRule::class, FinanceRecurringExpenseEntry::class, FinanceBill::class, FinanceBillPayment::class],
    version = 12,
    exportSchema = false
)
abstract class FinanceDb : RoomDatabase() {
    abstract fun dao(): FinanceDao
    abstract fun walletDao(): FinanceWalletDao
    abstract fun transferDao(): FinanceTransferDao
    abstract fun categoryDao(): FinanceCategoryDao
    abstract fun budgetDao(): FinanceBudgetDao
    abstract fun debtDao(): FinanceDebtDao
    abstract fun debtPaymentDao(): FinanceDebtPaymentDao
    abstract fun savingsGoalDao(): FinanceSavingsGoalDao
    abstract fun savingsEntryDao(): FinanceSavingsEntryDao
    abstract fun recurringIncomeRuleDao(): FinanceRecurringIncomeRuleDao
    abstract fun recurringIncomeEntryDao(): FinanceRecurringIncomeEntryDao
    abstract fun recurringExpenseRuleDao(): FinanceRecurringExpenseRuleDao
    abstract fun recurringExpenseEntryDao(): FinanceRecurringExpenseEntryDao
    abstract fun billDao(): FinanceBillDao
    abstract fun billPaymentDao(): FinanceBillPaymentDao

    companion object {
        private const val DEFAULT_WALLET_SQL =
            "INSERT OR IGNORE INTO wallets(id, name, currencyCode, openingBalanceMinor, walletType, createdAt) " +
                "VALUES(1, 'المحفظة الرئيسية', 'YER', 0, 'CASH', CAST(strftime('%s','now') AS INTEGER) * 1000)"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT NOT NULL DEFAULT 'عام'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS wallets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, currencyCode TEXT NOT NULL, " +
                        "openingBalanceMinor INTEGER NOT NULL, walletType TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(DEFAULT_WALLET_SQL)
                db.execSQL("ALTER TABLE transactions ADD COLUMN walletId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE transactions ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'YER'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_walletId ON transactions(walletId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS transfers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "fromWalletId INTEGER NOT NULL, toWalletId INTEGER NOT NULL, " +
                        "amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL, " +
                        "note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_fromWalletId ON transfers(fromWalletId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transfers_toWalletId ON transfers(toWalletId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, kind TEXT NOT NULL, colorHex TEXT NOT NULL, " +
                        "isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name_kind ON categories(name, kind)")
                seedDefaultCategories(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Category names are labels referenced by transactions; merge duplicates safely.
                db.execSQL("DELETE FROM categories WHERE id NOT IN (SELECT MIN(id) FROM categories GROUP BY name COLLATE NOCASE)")
                db.execSQL("DROP INDEX IF EXISTS index_categories_name_kind")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories(name)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budgets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "categoryName TEXT NOT NULL, currencyCode TEXT NOT NULL, " +
                        "periodKey TEXT NOT NULL, limitMinor INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_categoryName_currencyCode_periodKey " +
                        "ON budgets(categoryName, currencyCode, periodKey)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN debtId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_debtId ON transactions(debtId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personName TEXT NOT NULL, " +
                        "description TEXT NOT NULL, debtType TEXT NOT NULL, principalMinor INTEGER NOT NULL, " +
                        "currencyCode TEXT NOT NULL, walletId INTEGER NOT NULL, cashMovementRecorded INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debts_walletId ON debts(walletId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debt_payments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, debtId INTEGER NOT NULL, " +
                        "transactionId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_debt_payments_debtId ON debt_payments(debtId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_debt_payments_transactionId ON debt_payments(transactionId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN savingsGoalId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_savingsGoalId ON transactions(savingsGoalId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS savings_goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, targetMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL, walletId INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_goals_walletId ON savings_goals(walletId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS savings_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, goalId INTEGER NOT NULL, transactionId INTEGER NOT NULL, entryType TEXT NOT NULL, amountMinor INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_savings_entries_goalId ON savings_entries(goalId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_savings_entries_transactionId ON savings_entries(transactionId)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN recurringIncomeRuleId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_recurringIncomeRuleId ON transactions(recurringIncomeRuleId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_income_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, incomeType TEXT NOT NULL, amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL, walletId INTEGER NOT NULL, frequency TEXT NOT NULL, anchorDay INTEGER NOT NULL, nextDueAt INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_income_rules_walletId ON recurring_income_rules(walletId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_income_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ruleId INTEGER NOT NULL, occurrenceKey TEXT NOT NULL, transactionId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, recordedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_income_entries_ruleId ON recurring_income_entries(ruleId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_income_entries_ruleId_occurrenceKey ON recurring_income_entries(ruleId, occurrenceKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_income_entries_transactionId ON recurring_income_entries(transactionId)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN recurringExpenseRuleId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_recurringExpenseRuleId ON transactions(recurringExpenseRuleId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_expense_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, categoryName TEXT NOT NULL, amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL, walletId INTEGER NOT NULL, frequency TEXT NOT NULL, anchorDay INTEGER NOT NULL, nextDueAt INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expense_rules_walletId ON recurring_expense_rules(walletId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS recurring_expense_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ruleId INTEGER NOT NULL, occurrenceKey TEXT NOT NULL, transactionId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, recordedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_expense_entries_ruleId ON recurring_expense_entries(ruleId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_expense_entries_ruleId_occurrenceKey ON recurring_expense_entries(ruleId, occurrenceKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_expense_entries_transactionId ON recurring_expense_entries(transactionId)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN billId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_billId ON transactions(billId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS bills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, categoryName TEXT NOT NULL, amountMinor INTEGER NOT NULL, currencyCode TEXT NOT NULL, walletId INTEGER NOT NULL, frequency TEXT NOT NULL, anchorDay INTEGER NOT NULL, anchorMonth INTEGER NOT NULL, nextDueAt INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_walletId ON bills(walletId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_nextDueAt ON bills(nextDueAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS bill_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, billId INTEGER NOT NULL, occurrenceKey TEXT NOT NULL, transactionId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, paidAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_payments_billId ON bill_payments(billId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bill_payments_billId_occurrenceKey ON bill_payments(billId, occurrenceKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bill_payments_transactionId ON bill_payments(transactionId)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN lastReminderOccurrenceKey TEXT DEFAULT NULL")
            }
        }

        /** Single migration registry shared by the app, workers, and migration tests. */
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
        )

        fun seedDefaultWallet(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS wallets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, currencyCode TEXT NOT NULL, " +
                    "openingBalanceMinor INTEGER NOT NULL, walletType TEXT NOT NULL, createdAt INTEGER NOT NULL)"
            )
            db.execSQL(DEFAULT_WALLET_SQL)
        }

        fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, kind TEXT NOT NULL, colorHex TEXT NOT NULL, " +
                    "isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
            )
            val defaults = listOf(
                Triple("عام", "BOTH", "#E3C68D"),
                Triple("بقالة", "EXPENSE", "#E3C68D"),
                Triple("مطاعم", "EXPENSE", "#E3C68D"),
                Triple("مواصلات", "EXPENSE", "#E3C68D"),
                Triple("بنزين", "EXPENSE", "#E3C68D"),
                Triple("فواتير", "EXPENSE", "#E3C68D"),
                Triple("راتب", "INCOME", "#E3C68D")
            )
            defaults.forEach { (name, kind, color) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO categories(name, kind, colorHex, isActive, createdAt) VALUES(?, ?, ?, 1, ?)",
                    arrayOf(name, kind, color, System.currentTimeMillis())
                )
            }
            // Preserve legacy free-text categories so old transactions remain editable.
            db.execSQL(
                "INSERT OR IGNORE INTO categories(name, kind, colorHex, isActive, createdAt) " +
                    "SELECT DISTINCT TRIM(category), 'BOTH', '#E3C68D', 1, CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                    "FROM transactions WHERE TRIM(category) <> ''"
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemScrim = android.graphics.Color.rgb(8, 15, 19)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(systemScrim), navigationBarStyle = SystemBarStyle.dark(systemScrim))
        val db = FinanceDatabaseProvider.get(applicationContext)
        setContent { FinanceApp(db) }
    }
}
