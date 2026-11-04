package com.maksym.financetracker.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.maksym.financetracker.db.FinanceDatabase
import com.maksym.financetracker.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.*

class NativeRepositoryTest {
    @Test fun nativeDriverPersistsAtomicTransfer() = runBlocking<Unit> {
        val driver = NativeSqliteDriver(FinanceDatabase.Schema, "personal-finance-tracker-test-${newId()}.db", onConfiguration = { config ->
            config.copy(inMemory = true, extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true))
        })
        val repo = SqlLedgerRepository(driver)
        try {
            repo.open()
            val service = FinanceService(repo)
            val date = LocalDate(2024, 1, 1)
            service.saveAccount(Account("a", "Bank", Currency.USD, openingMinor = 10000, openingDate = date, createdAt = 0), 0)
            service.saveAccount(Account("b", "Cash", Currency.USD, openingDate = date, createdAt = 0), 1)
            service.saveTransaction(Transaction("t", TransactionType.TRANSFER, "a", Money(3000, Currency.USD), date, destinationId = "b", received = Money(3000, Currency.USD), createdAt = 0), 2)
            repo.open()
            assertEquals(7000L, repo.state.value!!.balance("a").minor)
            assertEquals(3000L, repo.state.value!!.balance("b").minor)
            assertEquals(0L, repo.state.value!!.cashFlow(Period.month(date), Currency.USD).expense.minor)
            assertFails { driver.execute(null, "DELETE FROM account WHERE id = 'a'", 0) }
        } finally { repo.close() }
    }
}
