package com.maksym.financetracker.domain

import kotlinx.datetime.*
import kotlin.test.*

class FinancialTest {
    private val date = LocalDate(2024, 2, 29)
    private val account = Account("a", "Bank", Currency.USD, openingMinor = 10000, openingDate = LocalDate(2024, 1, 1), createdAt = 0)
    private fun expense(minor: Long = 2500, day: LocalDate = date) = Transaction(type = TransactionType.EXPENSE, accountId = "a", amount = Money(minor, Currency.USD), date = day, categoryId = "food", createdAt = 0)

    @Test fun exactDecimalParsing() {
        assertEquals(12345L, Money.parse("123.45", Currency.USD).minor)
        assertEquals(-1L, Money.parse("-0.001", Currency.KWD).minor)
        assertEquals("JPY 100", Money.parse("100", Currency.JPY).display())
        assertEquals("0.00", Money.parse("-0.00", Currency.USD).decimal())
        assertEquals(Long.MAX_VALUE, Money.parse("92233720368547758.07", Currency.USD).minor)
    }
    @Test fun rejectPrecisionAndOverflow() {
        listOf("1.001", "NaN", "1e2", "1,23", "", "92233720368547758.08").forEach { assertFailsWith<FinanceException> { Money.parse(it, Currency.USD) } }
        assertFailsWith<FinanceException> { Money.parse("1.0", Currency.JPY) }
        assertFailsWith<FinanceException> { checkedAdd(Long.MAX_VALUE, 1) }
        assertFailsWith<FinanceException> { checkedNegate(Long.MIN_VALUE) }
        assertFailsWith<FinanceException> { checkedMultiply(Long.MAX_VALUE, 2) }
        assertFailsWith<FinanceException> { Money(1, Currency.USD) + Money(1, Currency.EUR) }
    }
    @Test fun currencyScaleAndHalfEvenConversion() {
        val rate = Rate(Currency.USD, Currency.JPY, 1, 1, 0)
        assertEquals(2, rate.convert(Money(250, Currency.USD)).minor)
        assertEquals(4, rate.convert(Money(350, Currency.USD)).minor)
        assertEquals(-2, rate.convert(Money(-250, Currency.USD)).minor)
        assertEquals(1230, Rate(Currency.USD, Currency.KWD, 1, 1, 0).convert(Money(123, Currency.USD)).minor)
        assertFailsWith<FinanceException> { Rate(Currency.USD, Currency.EUR, 0, 1, 0) }
    }
    @Test fun transferAffectsBothBalancesButNotCashFlow() {
        val destination = account.copy(id = "b", name = "Cash", openingMinor = 0)
        val transfer = Transaction(type = TransactionType.TRANSFER, accountId = "a", amount = Money(4000, Currency.USD), date = date, destinationId = "b", received = Money(4000, Currency.USD), createdAt = 0)
        val ledger = Ledger(accounts = listOf(account, destination), transactions = listOf(transfer, expense()))
        validateLedger(ledger)
        assertEquals(3500, ledger.balance("a").minor)
        assertEquals(4000, ledger.balance("b").minor)
        assertEquals(2500, ledger.cashFlow(Period.month(date), Currency.USD).expense.minor)
        assertEquals(0, ledger.cashFlow(Period.month(date), Currency.USD).income.minor)
        assertFailsWith<FinanceException> { validateTransaction(transfer.copy(received = Money(3999, Currency.USD)), ledger) }
        assertFailsWith<FinanceException> { validateTransaction(transfer.copy(destinationId = "a"), ledger) }
    }
    @Test fun crossCurrencyTransferNeedsExecutionMetadata() {
        val destination = account.copy(id = "b", currency = Currency.EUR)
        val ledger = Ledger(accounts = listOf(account, destination))
        val tx = Transaction(type = TransactionType.TRANSFER, accountId = "a", amount = Money(100, Currency.USD), date = date, destinationId = "b", received = Money(92, Currency.EUR), createdAt = 0)
        assertFailsWith<FinanceException> { validateTransaction(tx, ledger) }
        validateTransaction(tx.copy(rateSource = "Bank confirmation", rateObservedAt = 0), ledger)
    }
    @Test fun zeroNegativeAndWrongCategoryAreRejected() {
        val ledger = Ledger(accounts = listOf(account))
        listOf(0L, -1L).forEach { assertFailsWith<FinanceException> { validateTransaction(expense(it), ledger) } }
        assertFailsWith<FinanceException> { validateTransaction(expense().copy(categoryId = "salary"), ledger) }
        assertFailsWith<FinanceException> { validateTransaction(expense().copy(amount = Money(20, Currency.EUR)), ledger) }
        assertEquals(-5000, ledger.copy(transactions = listOf(expense(15000))).balance("a").minor)
    }
    @Test fun budgetsUseMonthCategoryAndCurrency() {
        val budget = Budget(categoryId = "food", month = LocalDate(2024, 2, 1), limit = Money(2000, Currency.USD))
        val ledger = Ledger(accounts = listOf(account), transactions = listOf(expense(), expense(100, LocalDate(2024, 3, 1)), expense(100, LocalDate(2024, 1, 31)), expense(200).copy(categoryId = "home")))
        assertEquals(2500, ledger.progress(budget).spent.minor)
        assertEquals(-500, ledger.progress(budget).remaining.minor)
        assertTrue(ledger.progress(budget).warning)
    }
    @Test fun monthEndAndLeapYearPreserveAnchor() {
        val monthly = RecurringRule(template = expense(), frequency = Frequency.MONTHLY, anchor = LocalDate(2024, 1, 31), zone = "Europe/Kyiv")
        assertEquals(LocalDate(2024, 2, 29), monthly.dueDate(1))
        assertEquals(LocalDate(2024, 3, 31), monthly.dueDate(2))
        val yearly = monthly.copy(frequency = Frequency.YEARLY, anchor = date)
        assertEquals(LocalDate(2025, 2, 28), yearly.dueDate(1))
        assertEquals(LocalDate(2028, 2, 29), yearly.dueDate(4))
        assertEquals(LocalDate(2024, 2, 14), monthly.copy(frequency = Frequency.WEEKLY, interval = 2).dueDate(1))
    }
    @Test fun accountingDatesDoNotDependOnTimezone() {
        val tx = expense(day = LocalDate(2024, 3, 31))
        val ledger = Ledger(accounts = listOf(account), transactions = listOf(tx))
        assertEquals(ledger.cashFlow(Period.month(tx.date), Currency.USD), ledger.copy(preferences = Preferences(zone = "Pacific/Honolulu")).cashFlow(Period.month(tx.date), Currency.USD))
        val instant = kotlin.time.Instant.parse("2024-03-31T22:30:00Z").toEpochMilliseconds()
        assertEquals(LocalDate(2024, 4, 1), instantDate(instant, "Europe/Kyiv"))
        assertEquals(LocalDate(2024, 3, 31), instantDate(instant, "America/New_York"))
    }
    @Test fun valuationNeverHidesMissingRates() {
        val ledger = Ledger(accounts = listOf(account, account.copy(id = "b", currency = Currency.EUR)))
        assertNull(ledger.valuation(Currency.USD, 10).total)
        assertEquals(setOf(Currency.EUR), ledger.valuation(Currency.USD, 10).missing)
        val rated = ledger.copy(rates = listOf(Rate(Currency.EUR, Currency.USD, 11, 10, 0)))
        assertEquals(21000, rated.valuation(Currency.USD, 10).total!!.minor)
    }
    @Test fun historySearchAndSortAreDeterministic() {
        val ledger = Ledger(accounts = listOf(account), transactions = listOf(expense(50).copy(id = "a", tags = setOf("trip")), expense(100).copy(id = "b")))
        assertEquals(listOf("a"), ledger.history(TransactionFilter(search = "TRIP")).map { it.id })
        assertEquals(listOf("b", "a"), ledger.history(TransactionFilter(currency = Currency.USD, sort = TransactionSort.AMOUNT)).map { it.id })
        assertFailsWith<FinanceException> { ledger.history(TransactionFilter(sort = TransactionSort.AMOUNT)) }
    }
}
