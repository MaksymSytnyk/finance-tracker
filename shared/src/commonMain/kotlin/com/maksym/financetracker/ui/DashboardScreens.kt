package com.maksym.financetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maksym.financetracker.domain.*
import kotlinx.datetime.*
import kotlin.time.Clock

@Composable
fun OverviewScreen(
    ledger: Ledger,
    onAddAccount: () -> Unit,
    onAddTransaction: () -> Unit,
    onTransaction: (Transaction) -> Unit,
    onAccounts: () -> Unit,
    onRates: () -> Unit
) {
    val date = today(ledger.preferences.zone)
    val currency = ledger.preferences.currency

    if (ledger.accounts.isEmpty()) {
        EmptyState(
            title = "A fresh canvas for your finances",
            text = "Begin by adding your first account. Every number, chart, and insight will be generated entirely from your own private records.",
            action = "Create first account",
            onAction = onAddAccount
        )
        return
    }

    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    val valuation = runCatching { ledger.valuation(currency, Clock.System.now().toEpochMilliseconds()) }
    val flow = runCatching { ledger.cashFlow(Period.month(date), currency).also { it.net } }.getOrNull()

    // 1. HERO NET WORTH & VALUATION SECTION
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL NET BALANCE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$monthName ${date.year} · ${currency.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status chip
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${ledger.accounts.count { !it.archived }} Accounts",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hero Balance Figure
            val result = valuation.getOrNull()
            if (result?.total != null) {
                Text(
                    text = result.total!!.display(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "Valuation incomplete",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Valuation contextual status
            Text(
                text = when {
                    valuation.isFailure -> "Some amounts exceed the supported range. Balances remain available below."
                    result!!.missing.isNotEmpty() -> "Missing rates for ${result.missing.joinToString()} → $currency to value all accounts."
                    result.stale.isNotEmpty() -> "Uses rates over 7 days old for ${result.stale.joinToString()}."
                    result.rates.isNotEmpty() -> "Converted using your manual exchange rates."
                    else -> "All accounts reconciled in ${currency.name} · Local ledger"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cash Flow Canvas visualization
            val chartPoints = remember(ledger, date, currency) {
                val daysInMonth = date.day.coerceAtLeast(5)
                val step = (daysInMonth / 8).coerceAtLeast(1)
                (1..daysInMonth step step).map { d ->
                    val dayDate = LocalDate(date.year, date.month, d)
                    val txs = ledger.transactions.filter { it.date <= dayDate && it.amount.currency == currency }
                    val inc = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minor }
                    val exp = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.minor }
                    (inc - exp).toFloat() / 100f
                }
            }

            if (chartPoints.size >= 2) {
                CashFlowLineChart(
                    points = chartPoints,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                )
            }

            if (result?.rates?.isNotEmpty() == true || result?.missing?.isNotEmpty() == true) {
                TextButton(
                    onClick = onRates,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Review exchange rates →", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // 2. CASH FLOW SUMMARY CARDS (INFLOW, OUTFLOW, NET)
    if (flow != null) {
        FlowCards(flow)
    }

    // 3. ACCOUNTS OVERVIEW (Editorial Cards)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "Accounts",
            subtitle = "Active balances",
            action = {
                TextButton(onClick = onAccounts) {
                    Text("View all →", style = MaterialTheme.typography.labelLarge)
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ledger.accounts.take(5).forEach { account ->
                val balance = ledger.balance(account.id)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .width(220.dp)
                        .clickable(onClick = onAccounts)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = account.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = account.currency.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = balance.display(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Quick Add Account Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .width(140.dp)
                    .height(115.dp)
                    .clickable(onClick = onAddAccount)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("+", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Add Account", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    // 4. BUDGET PULSE
    val currentBudgets = ledger.budgets.filter { it.month == monthStart(date) && it.limit.currency == currency }
    if (currentBudgets.isNotEmpty()) {
        Panel {
            SectionTitle(title = "Budget Status", subtitle = "Current month limits")
            currentBudgets.take(3).forEach { budget ->
                SafeBudgetBar(ledger, budget)
            }
        }
    }

    // 5. RECENT ACTIVITY
    Panel {
        SectionTitle(
            title = "Recent Transactions",
            subtitle = "Latest ledger movements",
            action = {
                TextButton(onClick = onAddTransaction) {
                    Text("+ Add entry", style = MaterialTheme.typography.labelLarge)
                }
            }
        )

        val recentTxs = ledger.history(TransactionFilter()).take(5)
        if (recentTxs.isEmpty()) {
            Text(
                "Your first transaction starts your financial journey.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            recentTxs.forEach { tx ->
                TransactionRow(tx, ledger) { onTransaction(tx) }
            }
        }
    }

    // 6. UPCOMING SCHEDULED
    val upcoming = ledger.rules.filter { it.active }.sortedBy { it.dueDate() }.take(2)
    if (upcoming.isNotEmpty()) {
        Panel {
            SectionTitle(title = "Upcoming Schedules", subtitle = "Automated recurring entries")
            upcoming.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.template.description.ifBlank { "Scheduled Entry" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Due ${rule.dueDate()} · Every ${rule.interval} ${rule.frequency.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = rule.template.amount.display(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Editorial Inflow / Outflow / Net Flow Cards
 */
@Composable
private fun FlowCards(flow: CashFlow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Income / Inflow Card
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "INFLOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+${flow.income.display()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("This month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Expense / Outflow Card
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "OUTFLOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "−${flow.expense.display()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text("This month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Net Cash Flow Card
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "NET FLOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                val isPositive = flow.net.minor >= 0
                Text(
                    text = (if (isPositive) "+" else "") + flow.net.display(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text("Net savings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Editorial Accounts Screen
 */
@Composable
fun AccountsScreen(
    ledger: Ledger,
    onAdd: () -> Unit,
    onEdit: (Account) -> Unit,
    onHistory: (Account) -> Unit
) {
    ScreenHeader(
        title = "Accounts",
        subtitle = "Balances include opening amounts and all recorded movements",
        action = {
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("+ New Account", style = MaterialTheme.typography.labelLarge)
            }
        }
    )

    if (ledger.accounts.isEmpty()) {
        EmptyState(
            title = "No accounts recorded",
            text = "Create bank accounts, savings vaults, or cash wallets to begin organizing your money.",
            action = "Add account",
            onAction = onAdd
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ledger.accounts.forEach { account ->
            val balance = ledger.balance(account.id)
            val opening = Money(account.openingMinor, account.currency)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = account.type.name.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = account.currency.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (account.archived) {
                                Text(
                                    text = "· Archived",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        TextButton(
                            onClick = { onEdit(account) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Edit", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif
                    )

                    Text(
                        text = balance.display(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    EditorialDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opened with ${opening.display()} on ${account.openingDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { onHistory(account) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Activity history →", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Editorial Activity / Transactions Screen
 */
@Composable
fun TransactionsScreen(
    ledger: Ledger,
    initialAccount: String?,
    onFilterConsumed: () -> Unit,
    onOpen: (Transaction) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf(initialAccount) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var currency by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(TransactionSort.NEWEST.name) }
    var month by rememberSaveable { mutableStateOf<String?>(null) }
    var count by remember { mutableIntStateOf(50) }

    LaunchedEffect(initialAccount) {
        if (initialAccount != null) {
            account = initialAccount
            onFilterConsumed()
        }
    }

    val filter = TransactionFilter(
        search = search,
        accountId = account,
        categoryId = category,
        type = type?.let(TransactionType::valueOf),
        currency = currency?.let(Currency::valueOf),
        period = month?.let { Period.month(LocalDate.parse(it)) },
        sort = TransactionSort.valueOf(sort)
    )

    LaunchedEffect(filter) { count = 50 }
    val rows = remember(ledger, filter) { ledger.history(filter) }

    ScreenHeader(
        title = "Activity",
        subtitle = "${rows.size} transactions in record"
    )

    // FILTER TOOLBAR
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search transactions, notes, tags…", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Text("✕", fontSize = 12.sp)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Quick Type Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorialChip(
                    selected = type == null,
                    onClick = { type = null },
                    label = "All Types"
                )
                EditorialChip(
                    selected = type == TransactionType.EXPENSE.name,
                    onClick = { type = if (type == TransactionType.EXPENSE.name) null else TransactionType.EXPENSE.name },
                    label = "Expenses"
                )
                EditorialChip(
                    selected = type == TransactionType.INCOME.name,
                    onClick = { type = if (type == TransactionType.INCOME.name) null else TransactionType.INCOME.name },
                    label = "Income"
                )
                EditorialChip(
                    selected = type == TransactionType.TRANSFER.name,
                    onClick = { type = if (type == TransactionType.TRANSFER.name) null else TransactionType.TRANSFER.name },
                    label = "Transfers"
                )
            }

            // Secondary Dropdown Pickers
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.widthIn(min = 160.dp).weight(1f)) {
                    Picker(
                        label = "Account",
                        value = account,
                        options = listOf(null) + ledger.accounts.map { it.id },
                        text = { id -> ledger.accounts.find { it.id == id }?.name ?: "All accounts" }
                    ) { account = it }
                }

                Box(Modifier.widthIn(min = 160.dp).weight(1f)) {
                    Picker(
                        label = "Category",
                        value = category,
                        options = listOf(null) + ledger.categories.map { it.id },
                        text = { id -> ledger.categories.find { it.id == id }?.name ?: "All categories" }
                    ) { category = it }
                }

                Box(Modifier.widthIn(min = 140.dp).weight(1f)) {
                    Picker(
                        label = "Currency",
                        value = currency,
                        options = listOf(null) + Currency.entries.map { it.name },
                        text = { it ?: "All currencies" }
                    ) {
                        currency = it
                        if (it == null && sort == TransactionSort.AMOUNT.name) {
                            sort = TransactionSort.NEWEST.name
                        }
                    }
                }

                Box(Modifier.widthIn(min = 140.dp).weight(1f)) {
                    Picker(
                        label = "Sort",
                        value = sort,
                        options = TransactionSort.entries.filter { currency != null || it != TransactionSort.AMOUNT }.map { it.name }
                    ) { sort = it }
                }
            }

            // Month Filter & Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = month != null,
                        onCheckedChange = { month = if (it) monthStart(today(ledger.preferences.zone)).toString() else null }
                    )
                    Text("Filter by month", style = MaterialTheme.typography.bodyMedium)
                }

                if (search.isNotBlank() || account != null || category != null || type != null || currency != null || month != null || sort != TransactionSort.NEWEST.name) {
                    TextButton(onClick = {
                        search = ""
                        account = null
                        category = null
                        type = null
                        currency = null
                        month = null
                        sort = TransactionSort.NEWEST.name
                    }) {
                        Text("Reset filters", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            month?.let { m ->
                MonthPicker(LocalDate.parse(m)) { selected -> month = selected.toString() }
            }
        }
    }

    // TRANSACTIONS LIST
    if (rows.isEmpty()) {
        EmptyState("No matching entries", "Try adjusting your search query or reset your filters.")
    } else {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var lastDate: LocalDate? = null
                rows.take(count).forEach { tx ->
                    if ((sort == TransactionSort.NEWEST.name || sort == TransactionSort.OLDEST.name) && tx.date != lastDate) {
                        if (lastDate != null) {
                            EditorialDivider(Modifier.padding(vertical = 4.dp))
                        }
                        Text(
                            text = formatEditorialDate(tx.date, ledger.preferences.zone),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        lastDate = tx.date
                    }
                    TransactionRow(tx, ledger) { onOpen(tx) }
                }

                if (count < rows.size) {
                    OutlinedButton(
                        onClick = { count += 50 },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Load more (${rows.size - count} remaining)", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * Editorial Insights / Analytics Screen
 */
@Composable
fun InsightsScreen(ledger: Ledger) {
    var monthText by rememberSaveable { mutableStateOf(monthStart(today(ledger.preferences.zone)).toString()) }
    var currency by rememberSaveable { mutableStateOf(ledger.preferences.currency.name) }
    val month = LocalDate.parse(monthText)
    val code = Currency.valueOf(currency)

    ScreenHeader(
        title = "Insights",
        subtitle = "Complete calendar months · transfers excluded"
    )

    // PERIOD AND CURRENCY SELECTOR
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MonthPicker(month) { monthText = it.toString() }
            Picker("Report Currency", code, Currency.entries) { currency = it.name }
        }
    }

    val reportError = runCatching {
        val current = ledger.cashFlow(Period.month(month), code)
        val previous = ledger.cashFlow(Period.month(month.minus(1, DateTimeUnit.MONTH)), code)
        current.net - previous.net
        current.expense - previous.expense
        (0..5).forEach { ledger.cashFlow(Period.month(month.minus(it, DateTimeUnit.MONTH)), code).net }
        ledger.spending(Period.month(month), code)
    }.exceptionOrNull()

    if (reportError != null) {
        EmptyState(
            title = "Report exceeds supported range",
            text = "Select another month or currency to view financial summaries."
        )
        return
    }

    val flow = ledger.cashFlow(Period.month(month), code)
    val previous = ledger.cashFlow(Period.month(month.minus(1, DateTimeUnit.MONTH)), code)

    // CURRENT MONTH FLOW
    FlowCards(flow)

    // MOMENTUM / COMPARISON METRICS
    Panel {
        SectionTitle(title = "Compared with last month")
        val expDiff = flow.expense - previous.expense
        val netDiff = flow.net - previous.net
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Expense Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = (if (expDiff.minor >= 0) "+" else "") + expDiff.display(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (expDiff.minor > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Net Flow Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = (if (netDiff.minor >= 0) "+" else "") + netDiff.display(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (netDiff.minor >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
        if (previous.expense.minor == 0L) {
            Text("Previous month had no expenses in $code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // SIX MONTHS COMPARISON
    Panel {
        SectionTitle(title = "Six Months at a Glance", subtitle = "Income vs Expenses · $code")
        val periods = (5 downTo 0).map { month.minus(it, DateTimeUnit.MONTH) }
        val values = periods.map { ledger.cashFlow(Period.month(it), code) }
        val maximum = values.maxOf { maxOf(it.income.minor, it.expense.minor) }.coerceAtLeast(1)

        periods.zip(values).forEach { (date, value) ->
            val monthLabel = "${date.month.name.take(3)} ${date.year}"
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(monthLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                ChartBar(value.income, maximum, MaterialTheme.colorScheme.primary, "Income")
                ChartBar(value.expense, maximum, MaterialTheme.colorScheme.tertiary, "Expenses")
            }
        }
    }

    // SPENDING DISTRIBUTION DONUT & BREAKDOWN
    val spending = ledger.spending(Period.month(month), code).toList().sortedByDescending { it.second.minor }
    Panel {
        SectionTitle(title = "Expense Distribution", subtitle = "Where money was allocated · $code")
        if (spending.isEmpty()) {
            Text("No recorded expenses in $code for this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val totalExpense = flow.expense.minor.toDouble().coerceAtLeast(1.0)
            val palette = listOf(
                EditorialColors.ForestGreen,
                EditorialColors.MutedSage,
                EditorialColors.RestrainedTerracotta,
                Color(0xFF5B746C),
                Color(0xFF8170A6),
                Color(0xFFBA687B),
                Color(0xFF737871)
            )

            val slices = spending.mapIndexed { index, (catId, catMoney) ->
                val catName = ledger.categories.find { it.id == catId }?.name ?: "Other"
                DonutSlice(catName, catMoney.minor.toDouble(), palette[index % palette.size])
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Donut Chart Canvas
                Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    DonutChart(slices = slices, modifier = Modifier.fillMaxSize(), strokeWidth = 18.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TOTAL", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(flow.expense.display(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }

                // Ranked Category Breakdown
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    slices.take(5).forEach { slice ->
                        val percent = ((slice.value / totalExpense) * 100).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(slice.color))
                                Text(slice.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    val excluded = ledger.transactions.count { it.date in Period.month(month) && it.type != TransactionType.TRANSFER && it.amount.currency != code }
    if (excluded > 0) {
        Text(
            "$excluded entries in other currencies are excluded from this report.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartBar(value: Money, max: Long, color: Color, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.display(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            if (value.minor > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((value.minor.toDouble() / max).coerceIn(0.0, 1.0).toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}
