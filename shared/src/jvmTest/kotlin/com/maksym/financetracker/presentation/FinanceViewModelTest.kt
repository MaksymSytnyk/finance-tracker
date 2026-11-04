package com.maksym.financetracker.presentation

import com.maksym.financetracker.data.*
import com.maksym.financetracker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.test.*

class FinanceViewModelTest {
    private fun awaitIdle(model: FinanceViewModel) = runBlocking { withTimeout(5000) { model.state.first { !it.busy } } }
    private class Repository : AtomicLedgerRepository(Dispatchers.Unconfined) {
        var disk = Ledger(preferences = Preferences(zone = "UTC"))
        var fail = false
        override fun read(): Ledger { if (fail) throw FinanceException("Cannot open storage"); return disk }
        override fun persist(previous: Ledger, next: Ledger, replacing: Boolean) { if (fail) throw FinanceException("Cannot write storage"); disk = next }
        override fun close() {}
    }
    private class Files(var text: String? = null, val cancelled: Boolean = false) : FileGateway {
        var saved: String? = null
        override suspend fun save(name: String, content: String): Boolean { saved = content; return !cancelled }
        override suspend fun readBackup() = text
    }
    @Test fun loadAndMutationPublishState() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repository(); val model = FinanceViewModel(repo, scope)
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.ledger)
            var completed = false
            model.execute("Saved", { completed = true }) { repo.update { it.copy(preferences = Preferences(theme = ThemeMode.DARK)) } }
            assertTrue(completed); assertEquals("Saved", model.state.value.message)
            assertEquals(ThemeMode.DARK, model.state.value.ledger!!.preferences.theme)
        } finally { scope.cancel() }
    }
    @Test fun failedLoadCanBeRetried() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repository().apply { fail = true }; val model = FinanceViewModel(repo, scope)
            assertNotNull(model.state.value.error); assertFalse(model.state.value.loading)
            repo.fail = false; model.reload()
            assertNotNull(model.state.value.ledger); assertNull(model.state.value.error)
        } finally { scope.cancel() }
    }
    @Test fun saveFailureDoesNotInvokeCloseCallback() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repository(); val model = FinanceViewModel(repo, scope); repo.fail = true
            var closed = false
            model.execute("Saved", { closed = true }) { repo.update { it.copy(preferences = Preferences(theme = ThemeMode.DARK)) } }
            assertFalse(closed); assertFalse(model.state.value.busy); assertNotNull(model.state.value.error)
            assertEquals(0L, model.state.value.ledger!!.revision)
        } finally { scope.cancel() }
    }
    @Test fun duplicateSubmitIsIgnoredWhileBusy() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = FinanceViewModel(Repository(), scope)
            val gate = CompletableDeferred<Unit>(); var saves = 0
            model.execute("Done") { saves++; gate.await() }
            assertTrue(model.state.value.busy)
            model.execute("Done") { saves++ }
            assertEquals(1, saves)
            gate.complete(Unit)
            assertFalse(model.state.value.busy)
        } finally { scope.cancel() }
    }
    @Test fun restoreRequiresExplicitConfirmation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repository(); val model = FinanceViewModel(repo, scope)
            val incoming = Ledger(accounts = listOf(Account(name = "Restored", currency = Currency.USD, openingDate = LocalDate(2024, 1, 1), createdAt = 0)))
            model.inspectBackup(Files(BackupCodec.encode(incoming))); awaitIdle(model)
            assertNotNull(model.state.value.restorePreview); assertTrue(repo.disk.accounts.isEmpty())
            model.confirmRestore()
            assertEquals("Restored", repo.disk.accounts.single().name); assertNull(model.state.value.restorePreview)
        } finally { scope.cancel() }
    }
    @Test fun invalidBackupAndCancelledExportLeaveRecordsUntouched() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repo = Repository(); val model = FinanceViewModel(repo, scope)
            model.inspectBackup(Files("broken")); awaitIdle(model)
            assertNotNull(model.state.value.error); assertEquals(0L, repo.disk.revision)
            model.export(Files(cancelled = true), true); awaitIdle(model)
            assertEquals("Export cancelled.", model.state.value.message); assertEquals(0L, repo.disk.revision)
        } finally { scope.cancel() }
    }
}
