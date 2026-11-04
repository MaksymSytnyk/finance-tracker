package com.maksym.financetracker.domain

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

interface LedgerRepository {
    val state: StateFlow<Ledger?>
    suspend fun open()
    /** Transform and persist atomically. A stale editor must not overwrite a newer commit. */
    suspend fun update(expectedRevision: Long? = null, transform: (Ledger) -> Ledger)
    suspend fun replace(expectedRevision: Long, snapshot: Ledger)
    fun close()
}

class FinanceService(private val repository: LedgerRepository, private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    suspend fun saveAccount(account: Account, revision: Long) = repository.update(revision) { ledger ->
        ensure(account.openingDate <= instantDate(now(), ledger.preferences.zone), "Opening date cannot be in the future")
        val existing = ledger.accounts.find { it.id == account.id }
        if (existing != null && existing.currency != account.currency) {
            ensure(ledger.transactions.none { it.accountId == account.id || it.destinationId == account.id } && ledger.rules.none { it.template.accountId == account.id || it.template.destinationId == account.id }, "Currency cannot change after the account is used")
        }
        ledger.copy(accounts = ledger.accounts.upsert(account.copy(name = account.name.trim(), updatedAt = maxOf(now(), account.createdAt))) { it.id })
    }

    suspend fun saveTransaction(tx: Transaction, revision: Long) = repository.update(revision) { ledger ->
        ensure(tx.date <= instantDate(now(), ledger.preferences.zone), "Use a recurring schedule for a future transaction")
        validateTransaction(tx, ledger)
        ledger.copy(transactions = ledger.transactions.upsert(tx.copy(description = tx.description.trim(), updatedAt = maxOf(now(), tx.createdAt))) { it.id })
    }

    suspend fun deleteTransaction(id: String, revision: Long) = repository.update(revision) { ledger ->
        ensure(ledger.transactions.any { it.id == id }, "Transaction no longer exists")
        ledger.copy(transactions = ledger.transactions.filterNot { it.id == id }, occurrences = ledger.occurrences.map { if (it.transactionId == id) it.copy(transactionId = null) else it })
    }

    suspend fun saveCategory(category: Category, revision: Long) = repository.update(revision) { ledger ->
        val old = ledger.categories.find { it.id == category.id }
        if (old?.type != category.type) ensure(ledger.transactions.none { it.categoryId == category.id } && ledger.rules.none { it.template.categoryId == category.id } && ledger.budgets.none { it.categoryId == category.id }, "Used category type cannot change")
        ledger.copy(categories = ledger.categories.upsert(category.copy(name = category.name.trim())) { it.id })
    }

    suspend fun saveBudget(budget: Budget, revision: Long) = repository.update(revision) { it.copy(budgets = it.budgets.upsert(budget) { b -> b.id }) }
    suspend fun deleteBudget(id: String, revision: Long) = repository.update(revision) { it.copy(budgets = it.budgets.filterNot { b -> b.id == id }) }
    suspend fun savePreferences(preferences: Preferences, revision: Long) = repository.update(revision) { it.copy(preferences = preferences) }
    suspend fun saveRate(rate: Rate, revision: Long) = repository.update(revision) { ledger ->
        ensure(rate.observedAt <= now(), "Rate timestamp cannot be in the future")
        ledger.copy(rates = ledger.rates.filterNot { it.from == rate.from && it.to == rate.to && it.observedAt == rate.observedAt } + rate)
    }

    suspend fun saveRule(rule: RecurringRule, revision: Long) = repository.update(revision) { ledger ->
        val old = ledger.rules.find { it.id == rule.id }
        ensure(old == null || (old.anchor == rule.anchor && old.frequency == rule.frequency && old.interval == rule.interval && old.nextIndex == rule.nextIndex), "Pause this schedule and create a new one to change its calendar")
        validateTransaction(rule.template.copy(date = rule.anchor), ledger)
        ledger.copy(rules = ledger.rules.upsert(rule.copy(active = rule.active && (rule.endDate == null || rule.dueDate() <= rule.endDate))) { it.id })
    }
    suspend fun toggleRule(id: String, revision: Long) = repository.update(revision) { ledger ->
        val rule = ledger.rules.first { it.id == id }
        ensure(rule.active || rule.endDate == null || rule.dueDate() <= rule.endDate, "This schedule has finished. Create a new schedule to continue.")
        ledger.copy(rules = ledger.rules.map { if (it.id == id) it.copy(active = !it.active) else it })
    }

    data class Generation(val generated: Int, val errors: List<String>, val moreDue: Boolean)

    suspend fun generateDue(limit: Int = 366): Generation {
        ensure(limit in 1..1000, "Invalid generation batch size")
        var generated = 0
        val blocked = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        var processed = 0
        while (processed < limit) {
            val snapshot = repository.state.value ?: return Generation(0, listOf("Database is not open"), false)
            val rule = snapshot.rules.firstOrNull { it.id !in blocked && it.isDue(now()) } ?: break
            try {
                var wrote = false
                repository.update { current ->
                    val latest = current.rules.first { it.id == rule.id }
                    if (!latest.isDue(now())) current else {
                        val due = latest.dueDate()
                        val known = current.occurrences.any { it.ruleId == latest.id && it.date == due }
                        val tx = latest.template.copy(id = newId(), date = due, createdAt = now(), updatedAt = now())
                        if (!known) validateTransaction(tx, current)
                        val advanced = latest.copy(nextIndex = latest.nextIndex + 1)
                        val finished = latest.endDate != null && advanced.dueDate() > latest.endDate
                        wrote = !known
                        current.copy(
                            rules = current.rules.upsert(advanced.copy(active = !finished)) { it.id },
                            transactions = if (known) current.transactions else current.transactions + tx,
                            occurrences = if (known) current.occurrences else current.occurrences + Occurrence(latest.id, due, tx.id),
                        )
                    }
                }
                if (wrote) generated++
            } catch (e: FinanceException) {
                blocked += rule.id
                errors += "${rule.template.description.ifBlank { "Recurring transaction" }}: ${e.message}"
            }
            processed++
        }
        return Generation(generated, errors, repository.state.value?.rules?.any { it.id !in blocked && it.isDue(now()) } == true)
    }

    private fun RecurringRule.isDue(at: Long) = active && dueDate() <= instantDate(at, zone) && (endDate == null || dueDate() <= endDate)
}

private fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> = if (any { key(it) == key(item) }) map { if (key(it) == key(item)) item else it } else this + item
