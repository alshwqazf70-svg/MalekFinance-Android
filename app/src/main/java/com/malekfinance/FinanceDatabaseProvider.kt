package com.malekfinance

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** One process-wide database shared by activities and workers; never retains an Activity. */
object FinanceDatabaseProvider {
    @Volatile private var instance: FinanceDb? = null

    fun get(context: Context): FinanceDb = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, FinanceDb::class.java, "malek-finance.db")
            .addMigrations(*FinanceDb.ALL_MIGRATIONS)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    FinanceDb.seedDefaultWallet(db)
                    FinanceDb.seedDefaultCategories(db)
                }
            }).build().also { instance = it }
    }
}
