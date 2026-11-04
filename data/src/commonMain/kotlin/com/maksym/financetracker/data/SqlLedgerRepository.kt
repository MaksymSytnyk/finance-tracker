package com.maksym.financetracker.data

import app.cash.sqldelight.db.SqlDriver
import com.maksym.financetracker.db.FinanceDatabase
import com.maksym.financetracker.domain.*
import kotlinx.datetime.LocalDate

class SqlLedgerRepository(private val driver: SqlDriver) : AtomicLedgerRepository() {
    private val database = FinanceDatabase(driver)
    private val q = database.financeQueries

    override fun read(): Ledger = database.transactionWithResult {
        val meta = q.selectMeta().executeAsOneOrNull()
        if (meta == null) {
            val initial = Ledger()
            q.insertMeta(0, initial.preferences.currency.name, initial.preferences.zone, initial.preferences.theme.name)
            initial.categories.forEach(::insertCategory)
            initial
        } else Ledger(
            accounts = q.selectAccounts().executeAsList().map { Account(it.id, it.name, Currency.valueOf(it.currency), AccountType.valueOf(it.type), it.opening_minor, LocalDate.parse(it.opening_date), it.archived == 1L, it.created_at, it.updated_at) },
            categories = q.selectCategories().executeAsList().map { Category(it.id, it.name, TransactionType.valueOf(it.type), it.icon, it.color, it.archived == 1L) },
            transactions = q.selectTransactions().executeAsList().map { Transaction(it.id, TransactionType.valueOf(it.type), it.account_id, Money(it.amount_minor, Currency.valueOf(it.currency)), LocalDate.parse(it.business_date), it.category_id, it.destination_id, it.received_minor?.let { amount -> Money(amount, Currency.valueOf(it.received_currency!!)) }, it.description, it.notes, BackupCodec.parseTags(it.tags), it.created_at, it.updated_at, it.rate_source, it.rate_observed_at) },
            budgets = q.selectBudgets().executeAsList().map { Budget(it.id, it.category_id, LocalDate.parse(it.month), Money(it.limit_minor, Currency.valueOf(it.currency))) },
            rules = q.selectRules().executeAsList().map { RecurringRule(it.id, BackupCodec.parseTransaction(it.template), Frequency.valueOf(it.frequency), it.interval_count.toInt(), LocalDate.parse(it.anchor), it.next_index.toInt(), it.zone, it.end_date?.let(LocalDate::parse), it.active == 1L) },
            occurrences = q.selectOccurrences().executeAsList().map { Occurrence(it.rule_id, LocalDate.parse(it.date), it.transaction_id) },
            rates = q.selectRates().executeAsList().map { Rate(Currency.valueOf(it.from_currency), Currency.valueOf(it.to_currency), it.numerator, it.denominator, it.observed_at, it.source) },
            preferences = Preferences(Currency.valueOf(meta.currency), meta.zone, ThemeMode.valueOf(meta.theme)), revision = meta.revision,
        )
    }

    override fun persist(previous: Ledger, next: Ledger, replacing: Boolean) = database.transaction {
        ensure(q.selectMeta().executeAsOne().revision == previous.revision, "Another app instance changed the database. Reload before editing.")
        // Removing structural identities means a replace-restore. Normal operations archive them.
        val reset = replacing || removed(previous.accounts, next.accounts) { it.id } ||
            removed(previous.categories, next.categories) { it.id } ||
            removed(previous.rules, next.rules) { it.id } ||
            removed(previous.occurrences, next.occurrences) { it.ruleId + "/" + it.date } ||
            removed(previous.rates, next.rates) { it.rateKey() }
        val before = if (reset) {
            q.clearOccurrences(); q.clearTransactions(); q.clearBudgets(); q.clearRules(); q.clearRates(); q.clearAccounts(); q.clearCategories()
            Ledger(categories = emptyList())
        } else previous
        changed(before.accounts, next.accounts, { it.id }, ::insertAccount, ::updateAccount)
        changed(before.categories, next.categories, { it.id }, ::insertCategory, ::updateCategory)
        changed(before.rules, next.rules, { it.id }, ::insertRule, ::updateRule)
        // Detach generated occurrences before deleting their transactions.
        next.occurrences.filter { it.transactionId == null && before.occurrences.any { old -> old.ruleId == it.ruleId && old.date == it.date && old.transactionId != null } }
            .forEach { q.updateOccurrence(null, it.ruleId, it.date.toString()) }
        val transactionIds = next.transactions.map { it.id }.toSet()
        before.transactions.filter { it.id !in transactionIds }.forEach { q.deleteTransaction(it.id) }
        val budgetIds = next.budgets.map { it.id }.toSet()
        before.budgets.filter { it.id !in budgetIds }.forEach { q.deleteBudget(it.id) }
        changed(before.transactions, next.transactions, { it.id }, ::insertTransaction, ::updateTransaction)
        changed(before.budgets, next.budgets, { it.id }, { q.insertBudget(it.id, it.categoryId, it.month.toString(), it.limit.currency.name, it.limit.minor) }, { q.updateBudget(it.categoryId, it.month.toString(), it.limit.currency.name, it.limit.minor, it.id) })
        changed(before.occurrences, next.occurrences, { it.ruleId + "/" + it.date }, { q.insertOccurrence(it.ruleId, it.date.toString(), it.transactionId) }, { q.updateOccurrence(it.transactionId, it.ruleId, it.date.toString()) })
        changed(before.rates, next.rates, { it.rateKey() }, { q.insertRate(it.from.name, it.to.name, it.numerator, it.denominator, it.observedAt, it.source) }, { q.updateRate(it.numerator, it.denominator, it.source, it.from.name, it.to.name, it.observedAt) })
        q.updateMeta(next.revision, next.preferences.currency.name, next.preferences.zone, next.preferences.theme.name)
    }

    private fun insertAccount(a: Account) = q.insertAccount(a.id, a.name, a.currency.name, a.type.name, a.openingMinor, a.openingDate.toString(), a.archived.flag(), a.createdAt, a.updatedAt)
    private fun updateAccount(a: Account) = q.updateAccount(a.name, a.currency.name, a.type.name, a.openingMinor, a.openingDate.toString(), a.archived.flag(), a.createdAt, a.updatedAt, a.id)
    private fun insertCategory(c: Category) = q.insertCategory(c.id, c.name, c.type.name, c.icon, c.color, c.archived.flag())
    private fun updateCategory(c: Category) = q.updateCategory(c.name, c.type.name, c.icon, c.color, c.archived.flag(), c.id)
    private fun insertTransaction(t: Transaction) = q.insertTransaction(t.id, t.type.name, t.accountId, t.amount.currency.name, t.amount.minor, t.date.toString(), t.categoryId, t.destinationId, t.received?.currency?.name, t.received?.minor, t.description, BackupCodec.tagsText(t.tags), t.createdAt, t.updatedAt, t.rateSource, t.rateObservedAt, t.notes)
    private fun updateTransaction(t: Transaction) = q.updateTransaction(t.type.name, t.accountId, t.amount.currency.name, t.amount.minor, t.date.toString(), t.categoryId, t.destinationId, t.received?.currency?.name, t.received?.minor, t.description, BackupCodec.tagsText(t.tags), t.createdAt, t.updatedAt, t.rateSource, t.rateObservedAt, t.notes, t.id)
    private fun insertRule(r: RecurringRule) = q.insertRule(r.id, BackupCodec.transactionText(r.template), r.frequency.name, r.interval.toLong(), r.anchor.toString(), r.nextIndex.toLong(), r.zone, r.endDate?.toString(), r.active.flag())
    private fun updateRule(r: RecurringRule) = q.updateRule(BackupCodec.transactionText(r.template), r.frequency.name, r.interval.toLong(), r.anchor.toString(), r.nextIndex.toLong(), r.zone, r.endDate?.toString(), r.active.flag(), r.id)
    override fun close() = driver.close()
}

private fun Boolean.flag() = if (this) 1L else 0L
private fun Rate.rateKey() = "${from.name}/${to.name}/$observedAt"
private fun <T> changed(old: List<T>, new: List<T>, key: (T) -> String, insert: (T) -> Unit, update: (T) -> Unit) {
    val index = old.associateBy(key)
    new.forEach { item -> val previous = index[key(item)]; if (previous == null) insert(item) else if (previous != item) update(item) }
}

private fun <T> removed(old: List<T>, new: List<T>, key: (T) -> String): Boolean {
    val keys = new.map(key).toSet()
    return old.any { key(it) !in keys }
}
