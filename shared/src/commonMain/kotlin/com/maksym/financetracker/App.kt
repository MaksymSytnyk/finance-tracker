package com.maksym.financetracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.*
import com.maksym.financetracker.domain.*
import com.maksym.financetracker.presentation.*
import com.maksym.financetracker.ui.*

@Composable
fun App(repositoryFactory: () -> LedgerRepository, files: FileGateway) {
    val model = viewModel { FinanceViewModel(repositoryFactory) }
    FinanceApplication(model, files)
}

enum class Screen(val title: String, val glyph: String, val section: String) {
    OVERVIEW("Overview", "⌂", "Main"),
    TRANSACTIONS("Activity", "⇄", "Main"),
    ACCOUNTS("Accounts", "▣", "Main"),
    BUDGETS("Budgets", "◷", "Planning"),
    INSIGHTS("Insights", "▥", "Planning"),
    RECURRING("Scheduled", "↻", "Planning"),
    SETTINGS("Settings", "⚙", "System")
}

@Composable
fun FinanceApplication(model: FinanceViewModel, files: FileGateway, initialScreen: Screen = Screen.OVERVIEW) {
    val state by model.state.collectAsState()
    var screenName by rememberSaveable { mutableStateOf(initialScreen.name) }
    val screen = Screen.valueOf(screenName)
    val screenStates = rememberSaveableStateHolder()

    var editAccount by remember { mutableStateOf<Account?>(null) }
    var showAccount by remember { mutableStateOf(false) }
    var editTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showTransaction by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<RecurringRule?>(null) }
    var detail by remember { mutableStateOf<Transaction?>(null) }
    var accountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val ledger = state.ledger

    fun navigate(next: Screen) {
        screenName = next.name
        model.clearNotice()
    }

    fun newTransaction(recurring: Boolean = false) {
        editTransaction = null
        editRule = null
        schedule = recurring
        showTransaction = true
        model.clearNotice()
    }

    if (LocalNavigationEventDispatcherOwner.current != null) {
        NavigationEventHandler(
            rememberNavigationEventState(NavigationEventInfo.None),
            isForwardEnabled = false,
            isBackEnabled = screen != Screen.OVERVIEW,
            onBackCompleted = { navigate(Screen.OVERVIEW) }
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ledger != null && !state.busy) model.catchUp()
    }

    LedgerTheme(ledger?.preferences?.theme ?: ThemeMode.SYSTEM) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Escape && screen != Screen.OVERVIEW) {
                        navigate(Screen.OVERVIEW)
                        true
                    } else if (event.type == KeyEventType.KeyUp && event.isCtrlPressed && event.key == Key.N && ledger?.accounts?.any { !it.archived } == true) {
                        newTransaction()
                        true
                    } else false
                },
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= 920.dp

                Row(Modifier.fillMaxSize()) {
                    // DESKTOP EDITORIAL SIDEBAR
                    if (wide) {
                        Surface(
                            modifier = Modifier
                                .width(230.dp)
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 28.dp, horizontal = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Masthead
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("\u0024", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            "Personal Finance Tracker",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.sp,
                                            fontSize = 14.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Clip,
                                            fontFamily = FontFamily.Serif,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        "PERSONAL FINANCE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Spacer(Modifier.height(24.dp))

                                // Navigation Items Grouped
                                Screen.entries.forEach { entry ->
                                    val isSelected = screen == entry
                                    Surface(
                                        onClick = { navigate(entry) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                entry.glyph,
                                                fontSize = 16.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                entry.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                // Quick Entry Desktop Action
                                if (ledger?.accounts?.any { !it.archived } == true) {
                                    Button(
                                        onClick = { newTransaction() },
                                        enabled = !state.busy,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth().height(42.dp)
                                    ) {
                                        Text("+ New Entry", style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Local Storage / Privacy Badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            "LOCAL & OFFLINE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Data stored strictly on this device.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // MAIN WORKSPACE CONTENT
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        // TOP APP BAR
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 76.dp)
                                    .padding(horizontal = if (wide) 36.dp else 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!wide) {
                                        Text("\u0024", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = if (wide) screen.title else "Personal Finance Tracker",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = if (wide) 20.sp else 18.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!wide) {
                                            Text(
                                                screen.title.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!wide) {
                                        var menu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { menu = true }) {
                                                Text("⋯", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            }
                                            DropdownMenu(
                                                expanded = menu,
                                                onDismissRequest = { menu = false }
                                            ) {
                                                listOf(Screen.RECURRING, Screen.SETTINGS).forEach { item ->
                                                    DropdownMenuItem(
                                                        text = { Text("${item.glyph}   ${item.title}") },
                                                        onClick = {
                                                            menu = false
                                                            navigate(item)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (ledger?.accounts?.any { !it.archived } == true) {
                                        Button(
                                            onClick = { newTransaction() },
                                            enabled = !state.busy,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text("+ Entry", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }

                        if (state.busy) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        // NOTICE BANNER
                        if (state.error != null || state.message != null) {
                            Box(Modifier.padding(horizontal = if (wide) 36.dp else 18.dp, vertical = 6.dp)) {
                                Notice(
                                    state.error ?: state.message.orEmpty(),
                                    state.error != null,
                                    model::clearNotice
                                )
                            }
                        }

                        // SCREEN BODY
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when {
                                ledger == null && state.loading -> {
                                    CircularProgressIndicator(
                                        Modifier.align(Alignment.Center),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                ledger == null -> {
                                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                        EmptyState("Let's try again", "Your records could not be loaded.", "Reload", model::reload)
                                    }
                                }
                                else -> {
                                    screenStates.SaveableStateProvider(screenName) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            // Content Width Limiter for clean editorial proportions
                                            Column(
                                                modifier = Modifier
                                                    .widthIn(max = 1060.dp)
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(
                                                        start = if (wide) 36.dp else 16.dp,
                                                        end = if (wide) 36.dp else 16.dp,
                                                        top = 22.dp,
                                                        bottom = if (wide) 36.dp else 90.dp
                                                    ),
                                                verticalArrangement = Arrangement.spacedBy(22.dp)
                                            ) {
                                                when (screen) {
                                                    Screen.OVERVIEW -> OverviewScreen(
                                                        ledger,
                                                        onAddAccount = { editAccount = null; showAccount = true },
                                                        onAddTransaction = { newTransaction() },
                                                        onTransaction = { detail = it },
                                                        onAccounts = { navigate(Screen.ACCOUNTS) },
                                                        onRates = { navigate(Screen.SETTINGS) }
                                                    )
                                                    Screen.TRANSACTIONS -> TransactionsScreen(
                                                        ledger,
                                                        accountFilter,
                                                        onFilterConsumed = { accountFilter = null },
                                                        onOpen = { detail = it }
                                                    )
                                                    Screen.ACCOUNTS -> AccountsScreen(
                                                        ledger,
                                                        onAdd = { editAccount = null; showAccount = true },
                                                        onEdit = { editAccount = it; showAccount = true },
                                                        onHistory = { accountFilter = it.id; navigate(Screen.TRANSACTIONS) }
                                                    )
                                                    Screen.BUDGETS -> BudgetsScreen(ledger, model, state)
                                                    Screen.INSIGHTS -> InsightsScreen(ledger)
                                                    Screen.RECURRING -> RecurringScreen(
                                                        ledger,
                                                        model,
                                                        state,
                                                        onAdd = { newTransaction(true) },
                                                        onEdit = {
                                                            editRule = it
                                                            editTransaction = it.template
                                                            schedule = true
                                                            showTransaction = true
                                                        }
                                                    )
                                                    Screen.SETTINGS -> SettingsScreen(ledger, model, files, state)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // MOBILE BESPOKE BOTTOM NAVIGATION BAR
                        if (!wide) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Screen.entries.take(5).forEach { item ->
                                        val isSelected = screen == item
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { navigate(item) }
                                                .padding(horizontal = 2.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = item.glyph,
                                                fontSize = 18.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MODALS & DIALOGS
            if (ledger != null) {
                if (showAccount) {
                    AccountEditor(editAccount, ledger, model, state) { showAccount = false }
                }
                if (showTransaction) {
                    TransactionEditor(editTransaction, editRule, schedule, ledger, model, state) { showTransaction = false }
                }
                detail?.let { tx ->
                    TransactionDetails(
                        tx,
                        ledger,
                        model,
                        state,
                        onDismiss = { detail = null },
                        onEdit = {
                            detail = null
                            editTransaction = tx
                            editRule = null
                            schedule = false
                            showTransaction = true
                        }
                    )
                }
                state.restorePreview?.let { preview ->
                    AlertDialog(
                        onDismissRequest = model::cancelRestore,
                        title = { Text("Replace your data?", style = MaterialTheme.typography.titleLarge) },
                        text = {
                            Text(
                                "This backup contains ${preview.accounts.size} accounts and ${preview.transactions.size} transactions. It will replace all current records and settings. Export a backup first if you want to keep them.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = model::confirmRestore,
                                enabled = !state.busy,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Replace and restore")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = model::cancelRestore, enabled = !state.busy) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}
