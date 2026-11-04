package com.maksym.financetracker.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.maksym.financetracker.db.FinanceDatabase

fun iosRepository(): SqlLedgerRepository = SqlLedgerRepository(
    NativeSqliteDriver(FinanceDatabase.Schema, "finance.db", onConfiguration = { config ->
        config.copy(extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true))
    })
)
