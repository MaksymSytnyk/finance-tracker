package com.maksym.financetracker.data

import com.maksym.financetracker.domain.*
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDate
import kotlin.test.*
import kotlin.time.Instant

class MemoryRepository(initial: Ledger = Ledger()) : AtomicLedgerRepository(Dispatchers.Unconfined) {
    var disk = initial
    var failWrites = false
    override fun read() = disk
    override fun persist(previous: Ledger, next: Ledger, replacing: Boolean) { if (failWrites) throw FinanceException("Disk full"); disk = next }
    override fun close() {}
}

class RepositoryTest {
    private val now = Instant.parse("2024-03-31T12:00:00Z").toEpochMilliseconds()
    private fun account(id: String = "a") = Account(id, "Bank", Currency.USD, openingMinor = 10000, openingDate = LocalDate(2024, 1, 1), createdAt = 0)
    private fun tx(id: String = "tx", amount: Long = 1000) = Transaction(id, TransactionType.EXPENSE, "a", Money(amount, Currency.USD), LocalDate(2024, 1, 31), "food", createdAt = 0)
    private fun ledger() = Ledger(accounts = listOf(account()), preferences = Preferences(zone = "UTC"))

    @Test fun successfulCommitPublishesAndReopens() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        FinanceService(repo) { now }.saveTransaction(tx(), 0)
        assertEquals(9000L, repo.state.value!!.balance("a").minor)
        assertEquals(1L, repo.state.value!!.revision)
        repo.open()
        assertEquals(1, repo.state.value!!.transactions.size)
    }
    @Test fun failedPersistenceDoesNotPublish() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open(); repo.failWrites = true
        assertFailsWith<FinanceException> { FinanceService(repo) { now }.saveTransaction(tx(), 0) }
        assertEquals(ledger(), repo.state.value)
        assertEquals(0L, repo.disk.revision)
    }
    @Test fun staleEditorCannotOverwrite() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveTransaction(tx(), 0)
        assertFailsWith<FinanceException> { service.saveTransaction(tx(amount = 500), 0) }
        assertEquals(1000L, repo.state.value!!.transactions.single().amount.minor)
    }
    @Test fun editingAndDeletingRecalculateAccounts() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger().copy(accounts = listOf(account(), account("b")))); repo.open()
        val service = FinanceService(repo) { now }
        service.saveTransaction(tx(), 0)
        service.saveTransaction(tx().copy(accountId = "b", amount = Money(1500, Currency.USD)), 1)
        assertEquals(10000L, repo.state.value!!.balance("a").minor)
        assertEquals(8500L, repo.state.value!!.balance("b").minor)
        service.deleteTransaction("tx", 2)
        assertEquals(10000L, repo.state.value!!.balance("b").minor)
    }
    @Test fun catchesUpAndPreservesMonthEnd() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveRule(RecurringRule("rent", tx(), Frequency.MONTHLY, anchor = LocalDate(2024, 1, 31), zone = "UTC"), 0)
        assertEquals(3, service.generateDue().generated)
        assertEquals(setOf("2024-01-31", "2024-02-29", "2024-03-31"), repo.state.value!!.transactions.map { it.date.toString() }.toSet())
        assertEquals(0, service.generateDue().generated)
        assertEquals(3, repo.state.value!!.occurrences.size)
    }
    @Test fun concurrentCatchUpDoesNotDuplicate() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveRule(RecurringRule("rent", tx(), Frequency.MONTHLY, anchor = LocalDate(2024, 1, 31), zone = "UTC"), 0)
        coroutineScope { repeat(8) { launch(Dispatchers.Default) { service.generateDue() } } }
        assertEquals(3, repo.state.value!!.transactions.size)
        assertEquals(3, repo.state.value!!.occurrences.size)
    }
    @Test fun deletedOccurrenceRemainsDeletedAfterRestore() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveRule(RecurringRule("rent", tx(), Frequency.MONTHLY, anchor = LocalDate(2024, 1, 31), zone = "UTC"), 0)
        service.generateDue()
        service.deleteTransaction(repo.state.value!!.transactions.first().id, repo.state.value!!.revision)
        val restored = BackupCodec.decode(BackupCodec.encode(repo.state.value!!))
        val nextRepo = MemoryRepository(restored); nextRepo.open()
        assertEquals(0, FinanceService(nextRepo) { now }.generateDue().generated)
        assertEquals(2, nextRepo.state.value!!.transactions.size)
        assertEquals(1, nextRepo.state.value!!.occurrences.count { it.transactionId == null })
    }
    @Test fun dueBatchesAreBoundedAndResumable() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveRule(RecurringRule("daily", tx(), Frequency.DAILY, anchor = LocalDate(2024, 1, 1), zone = "UTC"), 0)
        val first = service.generateDue(10)
        assertEquals(10, first.generated); assertTrue(first.moreDue)
        assertEquals(81, service.generateDue().generated)
        assertEquals(91, repo.state.value!!.transactions.size)
    }
    @Test fun archivedAccountBlocksScheduleWithoutAdvancing() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveRule(RecurringRule("rent", tx(), Frequency.MONTHLY, anchor = LocalDate(2024, 1, 31), zone = "UTC"), 0)
        service.saveAccount(account().copy(archived = true), 1)
        val result = service.generateDue()
        assertEquals(0, result.generated); assertEquals(1, result.errors.size)
        assertEquals(0, repo.state.value!!.rules.single().nextIndex)
    }
    @Test fun failedOccurrenceRollsBackCursorAndIdentity() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveRule(RecurringRule("rent", tx(), Frequency.MONTHLY, anchor = LocalDate(2024, 1, 31), zone = "UTC"), 0)
        repo.failWrites = true
        val result = service.generateDue()
        assertEquals(0, result.generated)
        assertTrue(repo.state.value!!.transactions.isEmpty())
        assertTrue(repo.state.value!!.occurrences.isEmpty())
        assertEquals(0, repo.state.value!!.rules.single().nextIndex)
        repo.failWrites = false
        assertEquals(3, service.generateDue().generated)
    }
    @Test fun futurePostingIsRejected() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        assertFailsWith<FinanceException> { FinanceService(repo) { now }.saveTransaction(tx().copy(date = LocalDate(2024, 4, 1)), 0) }
    }
    @Test fun endDateIsInclusiveAndPauseWorks() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveRule(RecurringRule("rent", tx(), Frequency.MONTHLY, anchor = LocalDate(2024, 1, 31), zone = "UTC", endDate = LocalDate(2024, 2, 29)), 0)
        service.toggleRule("rent", 1)
        assertEquals(0, service.generateDue().generated)
        service.toggleRule("rent", 2)
        assertEquals(2, service.generateDue().generated)
        assertFalse(repo.state.value!!.rules.single().active)
    }
    @Test fun openingCurrencyCannotChangeAfterUse() = runBlocking<Unit> {
        val repo = MemoryRepository(ledger()); repo.open()
        val service = FinanceService(repo) { now }
        service.saveTransaction(tx(), 0)
        assertFailsWith<FinanceException> { service.saveAccount(account().copy(currency = Currency.EUR), 1) }
    }
}
