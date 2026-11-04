package com.maksym.financetracker.domain

import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun newId(): String = Uuid.random().toString()
fun today(zone: String): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.of(zone)).date
fun monthStart(date: LocalDate) = LocalDate(date.year, date.month, 1)
fun nextMonth(date: LocalDate) = monthStart(date).plus(1, DateTimeUnit.MONTH)
fun instantDate(epochMillis: Long, zone: String) = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.of(zone)).date

enum class AccountType { CASH, BANK, SAVINGS, CUSTOM }
enum class TransactionType { INCOME, EXPENSE, TRANSFER }
enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Account(
    val id: String = newId(), val name: String, val currency: Currency,
    val type: AccountType = AccountType.BANK, val openingMinor: Long = 0,
    val openingDate: LocalDate, val archived: Boolean = false,
    val createdAt: Long, val updatedAt: Long = createdAt,
)

data class Category(
    val id: String = newId(), val name: String, val type: TransactionType,
    val icon: String = "●", val color: Long = 0xFF277D70,
    val archived: Boolean = false,
)

data class Transaction(
    val id: String = newId(), val type: TransactionType, val accountId: String,
    val amount: Money, val date: LocalDate, val categoryId: String? = null,
    val destinationId: String? = null, val received: Money? = null,
    val description: String = "", val notes: String = "", val tags: Set<String> = emptySet(),
    val createdAt: Long, val updatedAt: Long = createdAt,
    val rateSource: String? = null, val rateObservedAt: Long? = null,
)

data class Budget(val id: String = newId(), val categoryId: String, val month: LocalDate, val limit: Money)

data class RecurringRule(
    val id: String = newId(), val template: Transaction, val frequency: Frequency,
    val interval: Int = 1, val anchor: LocalDate, val nextIndex: Int = 0,
    val zone: String, val endDate: LocalDate? = null, val active: Boolean = true,
) {
    fun dueDate(index: Int = nextIndex): LocalDate {
        ensure(index >= 0 && interval in 1..365, "Invalid recurrence interval")
        val count = checkedMultiply(index.toLong(), interval.toLong())
        ensure(count <= Int.MAX_VALUE, "Schedule exceeds the supported range")
        return try {
            when (frequency) {
                Frequency.DAILY -> anchor.plus(count.toInt(), DateTimeUnit.DAY)
                Frequency.WEEKLY -> anchor.plus(count.toInt(), DateTimeUnit.WEEK)
                Frequency.MONTHLY -> anchor.plus(count.toInt(), DateTimeUnit.MONTH)
                Frequency.YEARLY -> anchor.plus(count.toInt(), DateTimeUnit.YEAR)
            }
        } catch (_: IllegalArgumentException) { throw FinanceException("Schedule exceeds the supported date range") }
    }
}

data class Occurrence(val ruleId: String, val date: LocalDate, val transactionId: String?)
data class Preferences(
    val currency: Currency = Currency.USD,
    val zone: String = TimeZone.currentSystemDefault().id,
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

data class Ledger(
    val accounts: List<Account> = emptyList(), val categories: List<Category> = defaultCategories(),
    val transactions: List<Transaction> = emptyList(), val budgets: List<Budget> = emptyList(),
    val rules: List<RecurringRule> = emptyList(), val occurrences: List<Occurrence> = emptyList(),
    val rates: List<Rate> = emptyList(), val preferences: Preferences = Preferences(),
    val revision: Long = 0,
) {
    fun balance(accountId: String): Money {
        val account = accounts.firstOrNull { it.id == accountId } ?: throw FinanceException("Account no longer exists")
        val value = transactions.fold(account.openingMinor) { sum, tx ->
            val movement = when {
                tx.accountId == accountId -> if (tx.type == TransactionType.INCOME) tx.amount.minor else checkedNegate(tx.amount.minor)
                tx.destinationId == accountId -> tx.received!!.minor
                else -> 0
            }
            checkedAdd(sum, movement)
        }
        return Money(value, account.currency)
    }
}

fun defaultCategories() = listOf(
    Category("food", "Food & dining", TransactionType.EXPENSE, "◒", 0xFFB5733C),
    Category("groceries", "Groceries", TransactionType.EXPENSE, "▤", 0xFF778942),
    Category("home", "Home & bills", TransactionType.EXPENSE, "⌂", 0xFF537DAD),
    Category("transport", "Transport", TransactionType.EXPENSE, "↗", 0xFF8170A6),
    Category("health", "Health", TransactionType.EXPENSE, "+", 0xFFBA687B),
    Category("shopping", "Shopping", TransactionType.EXPENSE, "◇", 0xFFAC8245),
    Category("leisure", "Leisure", TransactionType.EXPENSE, "✦", 0xFF5E9295),
    Category("other", "Other expenses", TransactionType.EXPENSE, "●", 0xFF7E858D),
    Category("salary", "Salary", TransactionType.INCOME, "↙", 0xFF277D70),
    Category("freelance", "Freelance", TransactionType.INCOME, "◆", 0xFF438D89),
    Category("other-income", "Other income", TransactionType.INCOME, "+", 0xFF718D50),
)
