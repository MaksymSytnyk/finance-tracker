package com.maksym.financetracker.domain

import kotlinx.datetime.*

data class Period(val start: LocalDate, val endExclusive: LocalDate) {
    init { ensure(start < endExclusive, "Period end must follow its start") }
    operator fun contains(date: LocalDate) = date >= start && date < endExclusive
    companion object { fun month(date: LocalDate) = Period(monthStart(date), nextMonth(date)) }
}

data class CashFlow(val income: Money, val expense: Money) { val net: Money get() = income - expense }
data class BudgetProgress(val budget: Budget, val spent: Money) {
    val remaining get() = budget.limit - spent
    val warning get() = spent.minor >= budget.limit.minor - budget.limit.minor / 5
}
data class Valuation(val total: Money?, val missing: Set<Currency>, val stale: Set<Currency>, val rates: List<Rate>)

fun Ledger.cashFlow(period: Period, currency: Currency): CashFlow {
    var income = 0L
    var expense = 0L
    transactions.filter { it.date in period && it.amount.currency == currency }.forEach {
        when (it.type) {
            TransactionType.INCOME -> income = checkedAdd(income, it.amount.minor)
            TransactionType.EXPENSE -> expense = checkedAdd(expense, it.amount.minor)
            TransactionType.TRANSFER -> Unit
        }
    }
    return CashFlow(Money(income, currency), Money(expense, currency))
}

fun Ledger.spending(period: Period, currency: Currency): Map<String, Money> = transactions
    .filter { it.type == TransactionType.EXPENSE && it.date in period && it.amount.currency == currency }
    .groupBy { it.categoryId!! }.mapValues { (_, rows) -> Money(rows.fold(0L) { sum, row -> checkedAdd(sum, row.amount.minor) }, currency) }

fun Ledger.progress(budget: Budget): BudgetProgress {
    val spent = transactions.filter { it.type == TransactionType.EXPENSE && it.categoryId == budget.categoryId && it.amount.currency == budget.limit.currency && it.date in Period.month(budget.month) }
        .fold(0L) { sum, tx -> checkedAdd(sum, tx.amount.minor) }
    return BudgetProgress(budget, Money(spent, budget.limit.currency))
}

fun Ledger.valuation(currency: Currency, at: Long, provider: ExchangeRateProvider = RecordedRateProvider(rates)): Valuation {
    val missing = mutableSetOf<Currency>()
    val stale = mutableSetOf<Currency>()
    val used = mutableListOf<Rate>()
    var sum = Money(0, currency)
    // Aggregate each currency first, so splitting an account never changes conversion rounding.
    val balances = accounts.groupBy { it.currency }.mapValues { (_, rows) -> rows.map { balance(it.id) }.reduce { a, b -> a + b } }
    balances.forEach { (code, balance) ->
        if (code == currency) sum += balance
        else if (balance.minor != 0L) {
            val rate = provider.find(code, currency, at)
            if (rate == null) missing += code else {
                sum += rate.convert(balance)
                used += rate
                if (at - rate.observedAt > 7L * 86_400_000) stale += code
            }
        }
    }
    return Valuation(if (missing.isEmpty()) sum else null, missing, stale, used)
}

enum class TransactionSort { NEWEST, OLDEST, AMOUNT, CATEGORY, TYPE }
data class TransactionFilter(val search: String = "", val accountId: String? = null, val categoryId: String? = null, val type: TransactionType? = null, val currency: Currency? = null, val period: Period? = null, val sort: TransactionSort = TransactionSort.NEWEST)

fun Ledger.history(filter: TransactionFilter): List<Transaction> {
    ensure(filter.sort != TransactionSort.AMOUNT || filter.currency != null, "Choose a currency before sorting by amount")
    val needle = filter.search.trim().lowercase()
    val rows = transactions.filter { tx ->
        (filter.accountId == null || filter.accountId == tx.accountId || filter.accountId == tx.destinationId) &&
            (filter.categoryId == null || filter.categoryId == tx.categoryId) &&
            (filter.type == null || filter.type == tx.type) && (filter.currency == null || filter.currency == tx.amount.currency) &&
            (filter.period == null || tx.date in filter.period) &&
            (needle.isEmpty() || (tx.description + " " + tx.notes + " " + tx.tags.joinToString(" ") + " " + categories.find { it.id == tx.categoryId }?.name).lowercase().contains(needle))
    }
    val comparator = when (filter.sort) {
        TransactionSort.NEWEST -> compareByDescending<Transaction> { it.date }
        TransactionSort.OLDEST -> compareBy { it.date }
        TransactionSort.AMOUNT -> compareByDescending { it.amount.minor }
        TransactionSort.CATEGORY -> compareBy { tx -> categories.find { it.id == tx.categoryId }?.name ?: "Transfer" }
        TransactionSort.TYPE -> compareBy { it.type.ordinal }
    }
    return rows.sortedWith(comparator.thenByDescending { it.createdAt }.thenBy { it.id })
}

interface ExchangeRateProvider {
    fun find(from: Currency, to: Currency, at: Long): Rate?
}

class RecordedRateProvider(private val rates: List<Rate>) : ExchangeRateProvider {
    override fun find(from: Currency, to: Currency, at: Long): Rate? = rates
        .filter { it.from == from && it.to == to && it.observedAt <= at }
        .maxByOrNull { it.observedAt }
}
