package com.maksym.financetracker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksym.financetracker.domain.*
import com.maksym.financetracker.presentation.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Editorial Dialog Container with clean styling and discard protection
 */
@Composable
fun EditorDialog(
    title: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var discard by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) discard = true },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 520.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                content()
                error?.let {
                    Text(
                        text = "✕ $it",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !busy,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (busy) "Saving…" else "Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { discard = true },
                enabled = !busy
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }
        }
    )

    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard unsaved changes?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Any entered details will be lost.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) {
                    Text("Keep editing")
                }
            }
        )
    }
}

private fun parseDate(value: String): LocalDate = try {
    LocalDate.parse(value.trim())
} catch (_: Exception) {
    throw FinanceException("Enter a valid date as YYYY-MM-DD")
}

/**
 * Editorial Account Editor
 */
@Composable
fun AccountEditor(
    existing: Account?,
    ledger: Ledger,
    model: FinanceViewModel,
    state: FinanceUiState,
    onDismiss: () -> Unit
) {
    val revision = remember { ledger.revision }
    val id = remember { existing?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var currency by rememberSaveable { mutableStateOf((existing?.currency ?: ledger.preferences.currency).name) }
    var type by rememberSaveable { mutableStateOf((existing?.type ?: AccountType.BANK).name) }
    var opening by rememberSaveable { mutableStateOf(existing?.let { Money(it.openingMinor, it.currency).decimal() } ?: "0") }
    var date by rememberSaveable { mutableStateOf((existing?.openingDate ?: today(ledger.preferences.zone)).toString()) }
    var archived by rememberSaveable { mutableStateOf(existing?.archived ?: false) }

    EditorDialog(
        title = if (existing == null) "New Financial Account" else "Edit Account Details",
        busy = state.busy,
        error = state.error,
        onDismiss = onDismiss,
        onSave = {
            model.execute("Account saved.", onDismiss) {
                val now = Clock.System.now().toEpochMilliseconds()
                val code = Currency.valueOf(currency)
                model.service.saveAccount(
                    Account(
                        id = id,
                        name = name,
                        currency = code,
                        type = AccountType.valueOf(type),
                        openingMinor = Money.parse(opening, code).minor,
                        openingDate = parseDate(date),
                        archived = archived,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now
                    ),
                    revision
                )
            }
        }
    ) {
        Input("Account Name", name, { name = it })

        // Type Segmented Pills
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("ACCOUNT TYPE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccountType.entries.forEach { t ->
                    EditorialChip(
                        selected = type == t.name,
                        onClick = { type = t.name },
                        label = t.name.lowercase().replaceFirstChar { it.uppercase() }
                    )
                }
            }
        }

        Picker("Currency", currency, Currency.entries.map { it.name }) { currency = it }

        Input("Opening Balance ($currency)", opening, { opening = it })

        Input("Opening Date (YYYY-MM-DD)", date, { date = it })
        Text(
            "The opening balance establishes your historical baseline. Recorded transactions cannot precede this date.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (existing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = archived, onCheckedChange = { archived = it })
                Text("Archive account (retains historical transactions)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Editorial Transaction Editor: Fast, Focused, Tactile
 */
@Composable
fun TransactionEditor(
    existing: Transaction?,
    rule: RecurringRule?,
    scheduling: Boolean,
    ledger: Ledger,
    model: FinanceViewModel,
    state: FinanceUiState,
    onDismiss: () -> Unit
) {
    val revision = remember { ledger.revision }
    val id = remember { existing?.id ?: newId() }
    val ruleId = remember { rule?.id ?: newId() }

    var typeName by rememberSaveable { mutableStateOf((existing?.type ?: TransactionType.EXPENSE).name) }
    val type = TransactionType.valueOf(typeName)

    var accountId by rememberSaveable { mutableStateOf(existing?.accountId ?: ledger.accounts.firstOrNull { !it.archived }?.id.orEmpty()) }
    var destinationId by rememberSaveable { mutableStateOf(existing?.destinationId ?: ledger.accounts.firstOrNull { !it.archived && it.id != accountId }?.id.orEmpty()) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: ledger.categories.firstOrNull { it.type == type && !it.archived }?.id.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.decimal().orEmpty()) }
    var received by rememberSaveable { mutableStateOf(existing?.received?.decimal().orEmpty()) }
    val todayDate = today(ledger.preferences.zone)
    var date by rememberSaveable { mutableStateOf((rule?.anchor ?: existing?.date ?: todayDate).toString()) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(existing?.tags?.sorted()?.joinToString(", ").orEmpty()) }

    var frequency by rememberSaveable { mutableStateOf((rule?.frequency ?: Frequency.MONTHLY).name) }
    var interval by rememberSaveable { mutableStateOf((rule?.interval ?: 1).toString()) }
    var zone by rememberSaveable { mutableStateOf(rule?.zone ?: ledger.preferences.zone) }
    var endDate by rememberSaveable { mutableStateOf(rule?.endDate?.toString().orEmpty()) }
    var rateSource by rememberSaveable { mutableStateOf(existing?.rateSource ?: "Bank-confirmed amounts") }

    val account = ledger.accounts.find { it.id == accountId }
    val destination = ledger.accounts.find { it.id == destinationId }
    val options = ledger.accounts.filter { !it.archived || it.id == existing?.accountId || it.id == existing?.destinationId }
    val categories = ledger.categories.filter { it.type == type && (!it.archived || it.id == existing?.categoryId) }

    EditorDialog(
        title = if (scheduling) "Recurring Schedule" else if (existing == null) "Record Entry" else "Edit Transaction",
        busy = state.busy,
        error = state.error,
        onDismiss = onDismiss,
        onSave = {
            model.execute(
                if (scheduling) "Schedule saved." else "Transaction recorded.",
                {
                    onDismiss()
                    if (scheduling) model.catchUp()
                }
            ) {
                val selected = account ?: throw FinanceException("Select an account first")
                val now = Clock.System.now().toEpochMilliseconds()
                val money = Money.parse(amount, selected.currency)
                val crossCurrency = type == TransactionType.TRANSFER && destination?.currency != selected.currency

                val tx = Transaction(
                    id = id,
                    type = type,
                    accountId = selected.id,
                    amount = money,
                    date = parseDate(date),
                    categoryId = if (type != TransactionType.TRANSFER) categoryId else null,
                    destinationId = if (type == TransactionType.TRANSFER) destinationId else null,
                    received = if (type == TransactionType.TRANSFER) {
                        val target = destination ?: throw FinanceException("Select destination account")
                        if (crossCurrency) Money.parse(received, target.currency) else money
                    } else null,
                    description = description,
                    notes = notes,
                    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = maxOf(now, existing?.createdAt ?: now),
                    rateSource = if (crossCurrency) rateSource else null,
                    rateObservedAt = if (crossCurrency) existing?.rateObservedAt ?: now else null
                )

                if (scheduling) {
                    model.service.saveRule(
                        RecurringRule(
                            id = ruleId,
                            template = tx,
                            frequency = Frequency.valueOf(frequency),
                            interval = interval.toIntOrNull() ?: throw FinanceException("Enter a whole recurrence interval"),
                            anchor = parseDate(date),
                            nextIndex = rule?.nextIndex ?: 0,
                            zone = zone,
                            endDate = endDate.takeIf { it.isNotBlank() }?.let(::parseDate),
                            active = rule?.active ?: true
                        ),
                        revision
                    )
                } else {
                    model.service.saveTransaction(tx, revision)
                }
            }
        }
    ) {
        // 1. TRANSACTION TYPE SEGMENTED SWITCHER
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionType.entries.forEach { entry ->
                EditorialChip(
                    selected = type == entry,
                    onClick = {
                        typeName = entry.name
                        categoryId = ledger.categories.firstOrNull { it.type == entry && !it.archived }?.id.orEmpty()
                    },
                    label = when (entry) {
                        TransactionType.EXPENSE -> "Expense"
                        TransactionType.INCOME -> "Income"
                        TransactionType.TRANSFER -> "Transfer"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. HERO AMOUNT INPUT
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "AMOUNT (${account?.currency?.name ?: ledger.preferences.currency.name})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = { Text("0.00", style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)), textAlign = TextAlign.Center) },
                    textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, textAlign = TextAlign.Center),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. ACCOUNT SELECTOR
        Picker(
            label = if (type == TransactionType.TRANSFER) "Source Account" else "Account",
            value = accountId,
            options = options.map { it.id },
            text = { key -> options.find { it.id == key }?.let { "${it.name} (${it.currency})" } ?: "Select account" }
        ) { accountId = it }

        // 4. TRANSFER DESTINATION OR CATEGORY SELECTOR
        if (type == TransactionType.TRANSFER) {
            Picker(
                label = "Destination Account",
                value = destinationId,
                options = options.filter { it.id != accountId }.map { it.id },
                text = { key -> options.find { it.id == key }?.let { "${it.name} (${it.currency})" } ?: "Select destination" }
            ) { destinationId = it }

            if (destination != null && destination.currency != account?.currency) {
                Input("Actual Amount Received (${destination.currency})", received, { received = it })
                Input("Conversion Source", rateSource, { rateSource = it })
                Text(
                    "Cross-currency transfer: Both executed amounts are preserved atomically in a single ledger row.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // CATEGORY CHIP GRID
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        EditorialChip(
                            selected = categoryId == cat.id,
                            onClick = { categoryId = cat.id },
                            label = "${cat.icon} ${cat.name}"
                        )
                    }
                }
            }
        }

        // 5. DATE WITH QUICK SELECTORS
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (scheduling) "ANCHOR DATE" else "DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rule == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { date = todayDate.toString() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Today", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = { date = todayDate.minus(1, DateTimeUnit.DAY).toString() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Yesterday", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Input("Date (YYYY-MM-DD)", date, { date = it }, enabled = rule == null)
        }

        // 6. DESCRIPTION & METADATA
        Input("Description (e.g. Weekly grocery, Coffee)", description, { description = it })
        Input("Tags (comma separated, e.g. food, cafe)", tags, { tags = it })
        Input("Notes (optional)", notes, { notes = it }, singleLine = false)

        // 7. SCHEDULE RECURRENCE OPTIONS
        if (scheduling) {
            Picker("Repeat Frequency", frequency, Frequency.entries.map { it.name }, enabled = rule == null) { frequency = it }
            Input("Interval (e.g. 1 = every period, 2 = bi-period)", interval, { interval = it }, enabled = rule == null)
            Input("Schedule Timezone", zone, { zone = it })
            Input("End Date (optional, YYYY-MM-DD)", endDate, { endDate = it })
            Text(
                "Due occurrences are generated idempotently when Personal Finance Tracker opens. Anchor calendars remain aligned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Editorial Transaction Details Modal
 */
@Composable
fun TransactionDetails(
    tx: Transaction,
    ledger: Ledger,
    model: FinanceViewModel,
    state: FinanceUiState,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    var deleting by remember { mutableStateOf(false) }
    val account = ledger.accounts.find { it.id == tx.accountId }
    val destAccount = ledger.accounts.find { it.id == tx.destinationId }
    val category = ledger.categories.find { it.id == tx.categoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = tx.description.ifBlank { category?.name ?: "Transaction Details" },
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Large amount
                val isIncome = tx.type == TransactionType.INCOME
                val sign = when (tx.type) {
                    TransactionType.INCOME -> "+"
                    TransactionType.EXPENSE -> "−"
                    TransactionType.TRANSFER -> ""
                }
                Text(
                    text = "$sign${tx.amount.display()}",
                    style = MaterialTheme.typography.displayLarge,
                    color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                EditorialDivider()

                // Metadata rows
                DetailRow("Type", tx.type.name.lowercase().replaceFirstChar { it.uppercase() })
                DetailRow("Date", formatEditorialDate(tx.date, ledger.preferences.zone))
                DetailRow("Account", account?.name ?: "Unknown")

                if (category != null) {
                    DetailRow("Category", "${category.icon} ${category.name}")
                }

                val rec = tx.received
                if (destAccount != null) {
                    DetailRow("Destination", destAccount.name)
                    if (rec != null) {
                        DetailRow("Received", rec.display())
                    }
                }

                val src = tx.rateSource
                val obs = tx.rateObservedAt
                if (src != null && obs != null) {
                    DetailRow("Rate Source", src)
                    DetailRow("Rate Observed", Instant.fromEpochMilliseconds(obs).toString())
                }

                if (tx.tags.isNotEmpty()) {
                    DetailRow("Tags", tx.tags.joinToString("  ") { "#$it" })
                }

                if (tx.notes.isNotBlank()) {
                    DetailRow("Notes", tx.notes)
                }

                Text(
                    text = "Recorded ${Instant.fromEpochMilliseconds(tx.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(
                    onClick = { deleting = true },
                    enabled = !state.busy,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Delete transaction", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onEdit,
                enabled = !state.busy,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Edit", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        }
    )

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete transaction?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Account balances and cash flow reports will update automatically. Recurring catch-up will not duplicate deleted entries.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        model.execute("Transaction deleted.", onDismiss) {
                            model.service.deleteTransaction(tx.id, ledger.revision)
                        }
                    },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = false }) {
                    Text("Keep transaction")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
    }
}
