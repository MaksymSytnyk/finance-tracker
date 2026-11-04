package com.maksym.financetracker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksym.financetracker.data.BackupCodec
import com.maksym.financetracker.data.CsvExport
import com.maksym.financetracker.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock

interface FileGateway {
    suspend fun save(name: String, content: String): Boolean
    suspend fun readBackup(): String?
}

data class FinanceUiState(
    val ledger: Ledger? = null, val loading: Boolean = true, val busy: Boolean = false,
    val error: String? = null, val message: String? = null,
    val restorePreview: Ledger? = null, val restoreRevision: Long? = null,
    val nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
)

class FinanceViewModel private constructor(
    private val factory: () -> LedgerRepository,
    private val testScope: CoroutineScope?,
    private val initializationDispatcher: CoroutineDispatcher,
) : ViewModel() {
    constructor(factory: () -> LedgerRepository) : this(factory, null, Dispatchers.Default)
    constructor(repository: LedgerRepository, testScope: CoroutineScope? = null) : this({ repository }, testScope, Dispatchers.Unconfined)
    private var repository: LedgerRepository? = null
    private var observer: Job? = null
    private val scope get() = testScope ?: viewModelScope
    val service: FinanceService get() = FinanceService(repository ?: throw FinanceException("Database is not ready"))
    private val mutable = MutableStateFlow(FinanceUiState())
    val state = mutable.asStateFlow()
    init {
        reload()
        scope.launch {
            while (isActive) {
                delay(60_000)
                mutable.update { it.copy(nowMillis = Clock.System.now().toEpochMilliseconds()) }
                if (state.value.ledger != null && !state.value.busy && state.value.restorePreview == null) catchUp()
            }
        }
    }
    fun reload() = execute("", { catchUp() }) {
        val repo = repository ?: withContext(initializationDispatcher) { factory() }.also { repository = it }
        repo.open()
        if (observer == null) observer = scope.launch { repo.state.collect { ledger -> mutable.update { it.copy(ledger = ledger, loading = ledger == null) } } }
    }
    fun clearNotice() { mutable.update { it.copy(error = null, message = null) } }
    fun execute(success: String, onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        scope.launch {
            try {
                action()
                mutable.update { it.copy(busy = false, loading = false, message = success.takeIf(String::isNotBlank) ?: it.message) }
                onSuccess()
            } catch (e: CancellationException) { mutable.update { it.copy(busy = false) }; throw e }
            catch (e: Exception) {
                val message = if (e is FinanceException) e.message else "Could not complete this operation. Check available storage, then retry."
                mutable.update { it.copy(busy = false, loading = false, error = message) }
            }
        }
    }
    fun catchUp() = execute("") {
        val result = service.generateDue()
        mutable.update { it.copy(message = when {
            result.errors.isNotEmpty() -> result.errors.joinToString("\n")
            result.moreDue -> "Generated ${result.generated} entries. More are due; run catch-up again."
            result.generated > 0 -> "Added ${result.generated} scheduled transactions."
            else -> it.message
        }) }
    }
    fun export(files: FileGateway, backup: Boolean) = execute("") {
        val snapshot = state.value.ledger ?: throw FinanceException("Database is not ready")
        val text = withContext(Dispatchers.Default) { if (backup) BackupCodec.encode(snapshot) else CsvExport.encode(snapshot) }
        val saved = files.save(if (backup) "personal-finance-tracker-backup.json" else "personal-finance-tracker-transactions.csv", text)
        mutable.update { it.copy(message = if (saved) "File saved." else "Export cancelled.") }
    }
    fun inspectBackup(files: FileGateway) = execute("") {
        val revision = state.value.ledger?.revision ?: throw FinanceException("Database is not ready")
        val text = files.readBackup()
        if (text != null) {
            val preview = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
            mutable.update { it.copy(restorePreview = preview, restoreRevision = revision) }
        }
    }
    fun cancelRestore() { mutable.update { it.copy(restorePreview = null, restoreRevision = null) } }
    fun confirmRestore() {
        val preview = state.value.restorePreview ?: return
        val revision = state.value.restoreRevision ?: return
        execute("Backup restored.", ::cancelRestore) { repository!!.replace(revision, preview) }
    }
    override fun onCleared() {
        val job = scope.coroutineContext[Job]
        if (job == null) repository?.close() else job.invokeOnCompletion { repository?.close() }
    }
}
