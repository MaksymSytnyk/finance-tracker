package com.maksym.financetracker.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.maksym.financetracker.db.FinanceDatabase

fun androidRepository(context: Context): SqlLedgerRepository = SqlLedgerRepository(
    AndroidSqliteDriver(FinanceDatabase.Schema, context.applicationContext, "finance.db", callback = object : AndroidSqliteDriver.Callback(FinanceDatabase.Schema) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }
    })
)
