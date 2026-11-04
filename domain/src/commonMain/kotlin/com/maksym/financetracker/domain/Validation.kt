package com.maksym.financetracker.domain

import kotlinx.datetime.TimeZone

fun validateTransaction(tx: Transaction, ledger: Ledger, allowArchived: Boolean = false) {
    val account = ledger.accounts.find { it.id == tx.accountId } ?: throw FinanceException("Select an account")
    ensure(allowArchived || !account.archived, "This account is archived")
    ensure(tx.amount.minor > 0, "Amount must be greater than zero")
    ensure(tx.amount.currency == account.currency, "Amount currency must match the account")
    ensure(tx.date >= account.openingDate, "Date precedes the account opening date")
    ensure(tx.description.length <= 200 && tx.notes.length <= 4000, "Description or notes are too long")
    ensure(tx.tags.size <= 20 && tx.tags.all { it.isNotBlank() && it.length <= 40 }, "Use up to 20 tags of at most 40 characters")
    ensure(tx.updatedAt >= tx.createdAt, "Invalid transaction timestamps")
    if (tx.type == TransactionType.TRANSFER) {
        ensure(tx.categoryId == null, "Transfers do not have a spending category")
        val destination = ledger.accounts.find { it.id == tx.destinationId } ?: throw FinanceException("Select a destination account")
        ensure(destination.id != account.id, "Choose a different destination account")
        ensure(allowArchived || !destination.archived, "Destination account is archived")
        ensure(tx.date >= destination.openingDate, "Date precedes the destination account opening date")
        val received = tx.received ?: throw FinanceException("Enter the amount received")
        ensure(received.minor > 0 && received.currency == destination.currency, "Received amount must match the destination currency")
        if (account.currency == destination.currency) ensure(received == tx.amount, "Same-currency transfers must balance exactly")
        else ensure(!tx.rateSource.isNullOrBlank() && tx.rateObservedAt != null, "Record the conversion source and timestamp")
    } else {
        val category = ledger.categories.find { it.id == tx.categoryId } ?: throw FinanceException("Select a category")
        ensure(category.type == tx.type, "Category does not match the transaction type")
        ensure(allowArchived || !category.archived, "Category is archived")
        ensure(tx.destinationId == null && tx.received == null && tx.rateSource == null && tx.rateObservedAt == null, "Only transfers can have destination amounts")
    }
}

fun validateLedger(ledger: Ledger) {
    fun unique(values: List<String>, label: String) {
        ensure(values.all { it.isNotBlank() && it.length <= 100 } && values.distinct().size == values.size, "Invalid or duplicate $label identifiers")
    }
    unique(ledger.accounts.map { it.id }, "account")
    unique(ledger.categories.map { it.id }, "category")
    unique(ledger.transactions.map { it.id }, "transaction")
    unique(ledger.budgets.map { it.id }, "budget")
    unique(ledger.rules.map { it.id }, "rule")
    ensure(ledger.revision >= 0, "Invalid revision")
    TimeZone.of(ledger.preferences.zone)
    ledger.accounts.forEach {
        ensure(it.name.isNotBlank() && it.name.length <= 60, "Account name must contain 1–60 characters")
        ensure(it.updatedAt >= it.createdAt, "Invalid account timestamps")
        Money(it.openingMinor, it.currency)
    }
    ledger.categories.forEach {
        ensure(it.name.isNotBlank() && it.name.length <= 60 && it.icon.length in 1..8, "Invalid category name or icon")
        ensure(it.type != TransactionType.TRANSFER, "Categories must be income or expense")
        ensure(it.color in 0..0xFFFFFFFF, "Invalid category color")
    }
    ledger.transactions.forEach { validateTransaction(it, ledger, allowArchived = true) }
    ensure(ledger.budgets.map { Triple(it.categoryId, it.month, it.limit.currency) }.distinct().size == ledger.budgets.size, "A budget already exists for this category, month, and currency")
    ledger.budgets.forEach {
        ensure(it.limit.minor > 0 && it.month.day == 1, "Budget must have a positive limit and a month start date")
        ensure(ledger.categories.any { category -> category.id == it.categoryId && category.type == TransactionType.EXPENSE }, "Budget needs an expense category")
    }
    ledger.rules.forEach {
        TimeZone.of(it.zone)
        ensure(it.nextIndex >= 0 && it.interval in 1..365, "Invalid recurrence interval")
        ensure(it.endDate == null || it.endDate >= it.anchor, "Schedule end precedes its start")
        validateTransaction(it.template.copy(date = it.anchor), ledger, allowArchived = true)
        it.dueDate()
    }
    ensure(ledger.occurrences.map { it.ruleId to it.date }.distinct().size == ledger.occurrences.size, "Duplicate recurring occurrence")
    val generated = ledger.occurrences.mapNotNull { it.transactionId }
    ensure(generated.distinct().size == generated.size, "Transaction belongs to multiple occurrences")
    val ruleIds = ledger.rules.map { it.id }.toSet()
    val transactionIds = ledger.transactions.map { it.id }.toSet()
    ledger.occurrences.forEach {
        ensure(it.ruleId in ruleIds, "Occurrence refers to a missing rule")
        ensure(it.transactionId == null || it.transactionId in transactionIds, "Occurrence refers to a missing transaction")
    }
    ledger.accounts.forEach { ledger.balance(it.id) }
}
