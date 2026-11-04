package com.maksym.financetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksym.financetracker.domain.*
import com.maksym.financetracker.presentation.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Editorial Budgets Screen
 */
@Composable
fun BudgetsScreen(ledger: Ledger, model: FinanceViewModel, state: FinanceUiState) {
    var month by rememberSaveable { mutableStateOf(monthStart(today(ledger.preferences.zone)).toString()) }
    var currency by rememberSaveable { mutableStateOf(ledger.preferences.currency.name) }
    var editor by remember { mutableStateOf(false) }
    var existing by remember { mutableStateOf<Budget?>(null) }
    var deleting by remember { mutableStateOf<Budget?>(null) }

    ScreenHeader(
        title = "Budgets",
        subtitle = "Category limits strictly apply to calendar months without artificial carryover",
        action = {
            Button(
                onClick = {
                    existing = null
                    editor = true
                    model.clearNotice()
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("+ New Budget", style = MaterialTheme.typography.labelLarge)
            }
        }
    )

    // Month & Currency Selector
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MonthPicker(LocalDate.parse(month)) { month = it.toString() }
            Picker("Budget Currency", currency, Currency.entries.map { it.name }) { currency = it }
        }
    }

    val budgets = ledger.budgets.filter { it.month.toString() == month && it.limit.currency.name == currency }

    if (budgets.isEmpty()) {
        EmptyState(
            title = "No monthly limits set",
            text = "Create category limits to monitor spending. Automatic visual alerts trigger at 80% and 100% utilization.",
            action = "Create budget limit",
            onAction = {
                existing = null
                editor = true
            }
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            budgets.forEach { budget ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SafeBudgetBar(ledger, budget)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                existing = budget
                                editor = true
                                model.clearNotice()
                            }) {
                                Text("Edit limit", style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(onClick = { deleting = budget }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    // BUDGET EDITOR MODAL
    if (editor) {
        val revision = remember { ledger.revision }
        val id = remember { existing?.id ?: newId() }
        var category by rememberSaveable { mutableStateOf(existing?.categoryId ?: ledger.categories.firstOrNull { it.type == TransactionType.EXPENSE && !it.archived }?.id.orEmpty()) }
        var amount by rememberSaveable { mutableStateOf(existing?.limit?.decimal().orEmpty()) }

        EditorDialog(
            title = if (existing == null) "New Budget Limit" else "Edit Budget Limit",
            busy = state.busy,
            error = state.error,
            onDismiss = { editor = false },
            onSave = {
                model.execute("Budget saved.", { editor = false }) {
                    model.service.saveBudget(
                        Budget(id, category, LocalDate.parse(month), Money.parse(amount, Currency.valueOf(currency))),
                        revision
                    )
                }
            }
        ) {
            Text(
                text = "Period: ${month.take(7)} · $currency",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Picker(
                label = "Expense Category",
                value = category,
                options = ledger.categories.filter { it.type == TransactionType.EXPENSE && !it.archived }.map { it.id },
                text = { key -> ledger.categories.find { it.id == key }?.let { "${it.icon} ${it.name}" } ?: "Select category" }
            ) { category = it }

            Input("Monthly Spending Limit ($currency)", amount, { amount = it })

            Text(
                "Spending alerts appear at 80% (soft warning) and 100% (limit reached). Unspent balances remain tied to their calendar month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // REMOVE CONFIRMATION DIALOG
    deleting?.let { budget ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove budget limit?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("The monthly threshold will be removed. All underlying transactions remain completely safe and untouched.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        model.execute("Budget removed.", { deleting = null }) {
                            model.service.deleteBudget(budget.id, ledger.revision)
                        }
                    },
                    enabled = !state.busy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Editorial Recurring / Scheduled Screen
 */
@Composable
fun RecurringScreen(
    ledger: Ledger,
    model: FinanceViewModel,
    state: FinanceUiState,
    onAdd: () -> Unit,
    onEdit: (RecurringRule) -> Unit
) {
    ScreenHeader(
        title = "Scheduled",
        subtitle = "Deterministic recurring schedules with automatic catch-up for past due dates",
        action = {
            Button(
                onClick = onAdd,
                enabled = ledger.accounts.any { !it.archived },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("+ New Schedule", style = MaterialTheme.typography.labelLarge)
            }
        }
    )

    // CATCH UP PROCESSOR CARD
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "IDEMPOTENT RECURRENCE ENGINE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Schedules generate entries deterministically when the app opens or resumes. Calendar anchor dates are preserved across variable month ends (e.g. Jan 31 → Feb 28 → Mar 31).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = model::catchUp,
                enabled = !state.busy,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Process due transactions now", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (ledger.rules.isEmpty()) {
        EmptyState(
            title = "No recurring schedules",
            text = "Schedule regular rent payments, paychecks, subscription fees, or recurring savings transfers."
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ledger.rules.forEach { rule ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (rule.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = if (rule.active) "ACTIVE" else "PAUSED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (rule.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Text(
                                    text = "Every ${rule.interval} ${rule.frequency.name.lowercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = rule.template.amount.display(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = rule.template.description.ifBlank { "Scheduled ${rule.template.type.name.lowercase()}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif
                        )

                        Text(
                            text = if (rule.active) "Next occurrence: ${rule.dueDate()} · Zone: ${rule.zone}" else "Schedule is paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        EditorialDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                onEdit(rule)
                                model.clearNotice()
                            }) {
                                Text("Edit schedule", style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(
                                onClick = {
                                    model.execute("Schedule updated.") {
                                        model.service.toggleRule(rule.id, ledger.revision)
                                    }
                                },
                                enabled = !state.busy
                            ) {
                                Text(
                                    text = if (rule.active) "Pause" else "Resume",
                                    color = if (rule.active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Editorial Settings Screen
 */
@Composable
fun SettingsScreen(
    ledger: Ledger,
    model: FinanceViewModel,
    files: FileGateway,
    state: FinanceUiState
) {
    var categoryEditor by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<Category?>(null) }
    var rateEditor by remember { mutableStateOf(false) }
    var zone by rememberSaveable(ledger.preferences.zone) { mutableStateOf(ledger.preferences.zone) }

    ScreenHeader(
        title = "Settings",
        subtitle = "Preferences, category taxonomy, directed exchange rates, and data sovereignty"
    )

    // APPEARANCE & PREFERENCES
    Panel {
        SectionTitle("Appearance & Ledger Reporting")

        Picker("Application Theme", ledger.preferences.theme, ThemeMode.entries, enabled = !state.busy) { mode ->
            model.execute("Theme updated.") {
                model.service.savePreferences(ledger.preferences.copy(theme = mode), ledger.revision)
            }
        }

        Picker("Default Reporting Currency", ledger.preferences.currency, Currency.entries, enabled = !state.busy) { code ->
            model.execute("Reporting currency updated.") {
                model.service.savePreferences(ledger.preferences.copy(currency = code), ledger.revision)
            }
        }

        Input("Reporting Timezone Identifier", zone, { zone = it })
        Text(
            "IANA timezone ID, for example Europe/Kyiv or America/New_York. Financial dates stay unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                model.execute("Timezone updated.") {
                    try {
                        TimeZone.of(zone)
                    } catch (_: Exception) {
                        throw FinanceException("Enter a valid timezone identifier")
                    }
                    model.service.savePreferences(ledger.preferences.copy(zone = zone), ledger.revision)
                }
            },
            enabled = !state.busy,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Save Timezone", style = MaterialTheme.typography.labelMedium)
        }
    }

    // CATEGORIES MANAGEMENT
    Panel {
        SectionTitle(
            title = "Categories",
            subtitle = "Transaction categorization taxonomy",
            action = {
                TextButton(onClick = {
                    category = null
                    categoryEditor = true
                    model.clearNotice()
                }) {
                    Text("+ Add Category", style = MaterialTheme.typography.labelLarge)
                }
            }
        )

        ledger.categories.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(item.color).copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(item.icon, fontSize = 14.sp, color = Color(item.color))
                        }
                    }
                    Column {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            item.type.name.lowercase() + if (item.archived) " · archived" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = {
                    category = item
                    categoryEditor = true
                    model.clearNotice()
                }) {
                    Text("Edit", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // EXCHANGE RATES
    Panel {
        SectionTitle(
            title = "Directed Exchange Rates",
            subtitle = "Manual conversion rates with half-even rounding",
            action = {
                TextButton(onClick = {
                    rateEditor = true
                    model.clearNotice()
                }) {
                    Text("+ Record Rate", style = MaterialTheme.typography.labelLarge)
                }
            }
        )

        if (ledger.rates.isEmpty()) {
            Text(
                "No recorded exchange rates. Add rates to value multi-currency portfolios in ${ledger.preferences.currency.name}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ledger.rates.groupBy { it.from to it.to }.values.map { rows -> rows.maxBy { it.observedAt } }.forEach { rate ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${rate.from} → ${rate.to}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Rate: ${rate.numerator} / ${rate.denominator}", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rate.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            Instant.fromEpochMilliseconds(rate.observedAt).toString().take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // DATA OWNERSHIP & BACKUPS
    Panel {
        SectionTitle(
            title = "Data Ownership & Security",
            subtitle = "Offline-first local persistence"
        )
        Text(
            "All financial records are stored exclusively in your local SQLite database. Exports and backups are plaintext JSON/CSV files containing your sensitive financial transactions. Export them to secure locations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { model.export(files, false) },
                enabled = !state.busy,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Export Activity (CSV)")
            }
            OutlinedButton(
                onClick = { model.export(files, true) },
                enabled = !state.busy,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save Full Backup (JSON)")
            }
            Button(
                onClick = { model.inspectBackup(files) },
                enabled = !state.busy,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Restore Backup…")
            }
        }
    }

    // CATEGORY EDITOR DIALOG
    if (categoryEditor) {
        val revision = remember { ledger.revision }
        val id = remember { category?.id ?: newId() }
        var name by rememberSaveable { mutableStateOf(category?.name.orEmpty()) }
        var type by rememberSaveable { mutableStateOf((category?.type ?: TransactionType.EXPENSE).name) }
        var icon by rememberSaveable { mutableStateOf(category?.icon ?: "●") }
        var color by rememberSaveable { mutableStateOf(category?.color ?: 0xFF277D70L) }
        var archived by rememberSaveable { mutableStateOf(category?.archived ?: false) }

        EditorDialog(
            title = if (category == null) "New Category" else "Edit Category",
            busy = state.busy,
            error = state.error,
            onDismiss = { categoryEditor = false },
            onSave = {
                model.execute("Category saved.", { categoryEditor = false }) {
                    model.service.saveCategory(
                        Category(id, name, TransactionType.valueOf(type), icon, color, archived),
                        revision
                    )
                }
            }
        ) {
            Input("Category Name", name, { name = it })
            Picker("Category Type", type, listOf("EXPENSE", "INCOME")) { type = it }
            Picker("Glyph Icon", icon, listOf("●", "◒", "▤", "⌂", "↗", "+", "◇", "✦", "◆")) { icon = it }
            Picker(
                label = "Color Accent",
                value = color,
                options = listOf(0xFF277D70L, 0xFFB5733CL, 0xFF537DADL, 0xFF8170A6L, 0xFFBA687BL),
                text = {
                    when (it) {
                        0xFF277D70L -> "Forest Green"
                        0xFFB5733CL -> "Terracotta"
                        0xFF537DADL -> "Slate Blue"
                        0xFF8170A6L -> "Muted Purple"
                        else -> "Rose"
                    }
                }
            ) { color = it }

            if (category != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = archived, onCheckedChange = { archived = it })
                    Text("Archive category (preserves historical activity)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // EXCHANGE RATE EDITOR DIALOG
    if (rateEditor) {
        val revision = remember { ledger.revision }
        var from by rememberSaveable { mutableStateOf(Currency.EUR.name) }
        var to by rememberSaveable { mutableStateOf(ledger.preferences.currency.name) }
        var rate by rememberSaveable { mutableStateOf("") }
        var source by rememberSaveable { mutableStateOf("Manual rate") }
        var observed by rememberSaveable { mutableStateOf(Clock.System.now().toString()) }

        EditorDialog(
            title = "Record Exchange Rate",
            busy = state.busy,
            error = state.error,
            onDismiss = { rateEditor = false },
            onSave = {
                model.execute("Rate recorded.", { rateEditor = false }) {
                    val at = try {
                        Instant.parse(observed).toEpochMilliseconds()
                    } catch (_: Exception) {
                        throw FinanceException("Enter a UTC timestamp such as 2026-11-04T12:00:00Z")
                    }
                    model.service.saveRate(
                        Rate.decimal(Currency.valueOf(from), Currency.valueOf(to), rate, at).copy(source = source),
                        revision
                    )
                }
            }
        ) {
            Picker("Source Currency (From)", from, Currency.entries.map { it.name }) { from = it }
            Picker("Target Currency (To)", to, Currency.entries.map { it.name }) { to = it }
            Input("1 $from equals how many $to?", rate, { rate = it })
            Input("Source / Provenance", source, { source = it })
            Input("Observed UTC Timestamp", observed, { observed = it })
            Text(
                "Directed conversion uses exact rational arithmetic with half-even rounding at the destination precision.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
