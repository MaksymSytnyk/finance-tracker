package com.maksym.financetracker.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.maksym.financetracker.db.FinanceDatabase
import java.io.File
import java.util.Properties

fun desktopRepository(directory: File = File(System.getProperty("user.home"), ".local/share/personal-finance-tracker")): SqlLedgerRepository {
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create the application data directory" }
    val properties = Properties().apply { setProperty("foreign_keys", "true"); setProperty("busy_timeout", "5000") }
    return SqlLedgerRepository(JdbcSqliteDriver("jdbc:sqlite:${File(directory, "finance.db").absolutePath}", properties, FinanceDatabase.Schema))
}
