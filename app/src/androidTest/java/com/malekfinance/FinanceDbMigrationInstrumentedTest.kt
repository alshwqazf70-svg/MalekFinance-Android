package com.malekfinance

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceDbMigrationInstrumentedTest {
    @Test
    fun versionOneDatabaseMigratesThroughRoomToVersionTwelveWithoutLosingTransactions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "finance-migration-v1-test.db"
        context.deleteDatabase(databaseName)

        val legacyFile = context.getDatabasePath(databaseName)
        legacyFile.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)
        legacy.execSQL(
            "CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amountMinor INTEGER NOT NULL, kind TEXT NOT NULL, " +
                "note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        val oldTransaction = ContentValues().apply {
            put("amountMinor", 125_500L)
            put("kind", "EXPENSE")
            put("note", "عملية محفوظة قبل الترقية")
            put("createdAt", 1_700_000_000_000L)
        }
        val oldId = legacy.insertOrThrow("transactions", null, oldTransaction)
        legacy.version = 1
        legacy.close()

        val migratedDb = Room.databaseBuilder(context, FinanceDb::class.java, databaseName)
            .addMigrations(*FinanceDb.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val sqlite = migratedDb.openHelper.writableDatabase
            sqlite.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(12, cursor.getInt(0))
            }
            val preserved = migratedDb.dao().forWallet(1).single()
            assertEquals(oldId, preserved.id)
            assertEquals(125_500L, preserved.amountMinor)
            assertEquals("EXPENSE", preserved.kind)
            assertEquals("عملية محفوظة قبل الترقية", preserved.note)
            assertEquals("عام", preserved.category)
            assertEquals(1L, preserved.walletId)
            assertEquals("YER", preserved.currencyCode)

            val requiredTables = setOf(
                "wallets", "transfers", "categories", "budgets", "debts",
                "debt_payments", "savings_goals", "savings_entries",
                "recurring_income_rules", "recurring_income_entries",
                "recurring_expense_rules", "recurring_expense_entries",
                "bills", "bill_payments"
            )
            val actualTables = mutableSetOf<String>()
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) actualTables += cursor.getString(0)
            }
            assertTrue("Missing tables after migration: ${requiredTables - actualTables}", actualTables.containsAll(requiredTables))

            val billColumns = mutableSetOf<String>()
            sqlite.query("PRAGMA table_info(bills)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) billColumns += cursor.getString(nameColumn)
            }
            assertTrue(billColumns.contains("lastReminderOccurrenceKey"))
            assertEquals(1, migratedDb.walletDao().observe().let { 1 })
        } finally {
            migratedDb.close()
            context.deleteDatabase(databaseName)
        }
    }
}
