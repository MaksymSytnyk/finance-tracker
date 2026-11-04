package com.maksym.financetracker.data

import com.maksym.financetracker.domain.*
import kotlinx.datetime.LocalDate
import kotlin.test.*

class BackupTest {
    private fun sample(): Ledger {
        val date = LocalDate(2024, 2, 29)
        val account = Account("a", "Savings, \"main\"", Currency.KWD, openingMinor = Long.MAX_VALUE - 1000, openingDate = date, createdAt = 10)
        val tx = Transaction("t", TransactionType.EXPENSE, "a", Money(1, Currency.KWD), date, "food", description = "=SUM(A1:A2)", notes = "Line one\nПривіт", tags = setOf("travel", "food"), createdAt = 10)
        val rule = RecurringRule("r", tx, Frequency.MONTHLY, anchor = date, nextIndex = 1, zone = "Europe/Kyiv")
        return Ledger(accounts = listOf(account), transactions = listOf(tx), rules = listOf(rule), occurrences = listOf(Occurrence("r", date, "t")), budgets = listOf(Budget("b", "food", monthStart(date), Money(1000, Currency.KWD))), rates = listOf(Rate(Currency.KWD, Currency.USD, 325, 100, 10)), preferences = Preferences(Currency.KWD, "Europe/Kyiv", ThemeMode.DARK))
    }
    @Test fun losslessRoundTripWithLargeAmountsUnicodeAndRecurrence() {
        val original = sample()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
        assertTrue(BackupCodec.encode(original).contains("\"9223372036854774807\""))
    }
    @Test fun csvQuotesTextAndNeutralizesFormulas() {
        val csv = CsvExport.encode(sample())
        assertTrue(csv.contains("\"Savings, \"\"main\"\"\""))
        assertTrue(csv.contains("\"'=SUM(A1:A2)\""))
        assertTrue(csv.contains("\"Line one\nПривіт\""))
        assertTrue(csv.contains("\"0.001\""))
    }
    @Test fun futureFormatIsRejected() {
        assertFailsWith<FinanceException> { BackupCodec.decode(BackupCodec.encode(sample()).replace("\"format\": 1", "\"format\": 999")) }
    }
    @Test fun invalidReferencesAreRejected() {
        assertFailsWith<FinanceException> { BackupCodec.decode(BackupCodec.encode(sample().copy(transactions = sample().transactions.map { it.copy(accountId = "missing") }))) }
    }
    @Test fun changedCurrencyScaleIsRejected() {
        assertFailsWith<FinanceException> { BackupCodec.decode(BackupCodec.encode(sample()).replace("\"KWD\": 3", "\"KWD\": 2")) }
    }
    @Test fun duplicateIdentifiersAreRejected() {
        val sample = sample()
        assertFailsWith<FinanceException> { BackupCodec.decode(BackupCodec.encode(sample.copy(accounts = sample.accounts + sample.accounts))) }
    }
    @Test fun malformedInputIsRejected() {
        listOf("", "null", "{}", "[]", "{bad-json}").forEach { assertFailsWith<FinanceException> { BackupCodec.decode(it) } }
    }
    @Test fun sizeIsBounded() {
        assertFailsWith<FinanceException> { BackupCodec.decode(" ".repeat(20 * 1024 * 1024 + 1)) }
    }
}
