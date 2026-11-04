package com.maksym.financetracker.data

import com.maksym.financetracker.domain.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

abstract class AtomicLedgerRepository(private val dispatcher: CoroutineDispatcher = Dispatchers.Default) : LedgerRepository {
    private val mutable = MutableStateFlow<Ledger?>(null)
    final override val state = mutable.asStateFlow()
    private val mutex = Mutex()
    protected abstract fun read(): Ledger
    protected abstract fun persist(previous: Ledger, next: Ledger, replacing: Boolean)
    final override suspend fun open() = withContext(dispatcher) {
        mutex.withLock { mutable.value = read().also(::validateLedger) }
    }
    final override suspend fun update(expectedRevision: Long?, transform: (Ledger) -> Ledger) = commit(expectedRevision, false, transform)
    final override suspend fun replace(expectedRevision: Long, snapshot: Ledger) = commit(expectedRevision, true) { snapshot }
    private suspend fun commit(expectedRevision: Long?, replacing: Boolean, transform: (Ledger) -> Ledger) = withContext(dispatcher) {
        mutex.withLock {
            val previous = mutable.value ?: throw FinanceException("Database is not open")
            ensure(expectedRevision == null || previous.revision == expectedRevision, "Data changed while editing. Close this form and try again.")
            val transformed = transform(previous)
            if (transformed != previous) {
                val next = transformed.copy(revision = checkedAdd(previous.revision, 1))
                validateLedger(next)
                persist(previous, next, replacing)
                mutable.value = next
            }
        }
    }
}
