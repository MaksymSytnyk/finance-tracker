package com.maksym.financetracker.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.maksym.financetracker.db.FinanceDatabase
import com.maksym.financetracker.domain.*
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.*

/** These tests require the normal Gradle build and SQLDelight generation. */
class SqlRepositoryTest {
    @Test fun databasePersistsAcrossReopen() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("personal-finance-tracker-db").toFile()
        try {
            val first = desktopRepository(directory); first.open()
            val account = Account("a", "Bank", Currency.USD, openingMinor = 12000, openingDate = LocalDate(2024, 1, 1), createdAt = 0)
            FinanceService(first).saveAccount(account, 0); first.close()
            val second = desktopRepository(directory); second.open()
            assertEquals(12000L, second.state.value!!.balance("a").minor)
            assertEquals(1L, second.state.value!!.revision); second.close()
        } finally { directory.deleteRecursively() }
    }
    @Test fun transactionTagsNotesAndDeletionRoundTrip() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FinanceDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        val repo = SqlLedgerRepository(driver); repo.open()
        try {
            val service = FinanceService(repo)
            service.saveAccount(Account("a", "Bank", Currency.USD, openingDate = LocalDate(2024, 1, 1), createdAt = 0), 0)
            val tx = Transaction("t", TransactionType.EXPENSE, "a", Money(200, Currency.USD), LocalDate(2024, 2, 1), "food", notes = "Hello\nworld", tags = setOf("a", "b"), createdAt = 0)
            service.saveTransaction(tx, 1); repo.open()
            assertEquals(tx.tags, repo.state.value!!.transactions.single().tags)
            assertEquals(tx.notes, repo.state.value!!.transactions.single().notes)
            assertFails { driver.execute(null, "DELETE FROM account", 0) }
            service.deleteTransaction("t", 2); repo.open()
            assertTrue(repo.state.value!!.transactions.isEmpty())
        } finally { repo.close() }
    }
    @Test fun concurrentRepositoryWritersRejectStaleRevision() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("personal-finance-tracker-conflict").toFile()
        try {
            val first = desktopRepository(directory); first.open()
            val second = desktopRepository(directory); second.open()
            first.update { it.copy(preferences = it.preferences.copy(theme = ThemeMode.DARK)) }
            assertFailsWith<FinanceException> { second.update { it.copy(preferences = it.preferences.copy(theme = ThemeMode.LIGHT)) } }
            second.open(); assertEquals(ThemeMode.DARK, second.state.value!!.preferences.theme)
            first.close(); second.close()
        } finally { directory.deleteRecursively() }
    }
}
