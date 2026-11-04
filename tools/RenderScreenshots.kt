import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.maksym.financetracker.FinanceApplication
import com.maksym.financetracker.Screen
import com.maksym.financetracker.data.AtomicLedgerRepository
import com.maksym.financetracker.domain.*
import com.maksym.financetracker.presentation.*
import com.maksym.financetracker.ui.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import java.io.File

/** Uses the real shared UI with an isolated fixture; never opens user storage. */
private class FixtureRepository(theme: ThemeMode) : AtomicLedgerRepository(Dispatchers.Unconfined) {
    private val date = today("UTC")
    private val start = monthStart(date)
    private val opening = LocalDate(date.year - 1, 1, 1)
    private var ledger = Ledger(
        accounts = listOf(
            Account("main", "Everyday account", Currency.USD, AccountType.BANK, 135000, opening, createdAt = 0),
            Account("savings", "A little further ahead", Currency.USD, AccountType.SAVINGS, 850000, opening, createdAt = 0),
            Account("cash", "Pocket money", Currency.USD, AccountType.CASH, 6800, opening, createdAt = 0),
        ),
        transactions = listOf(
            Transaction("salary", TransactionType.INCOME, "main", Money(420000, Currency.USD), start, "salary", description = "November salary", createdAt = 0),
            Transaction("rent", TransactionType.EXPENSE, "main", Money(95000, Currency.USD), start, "home", description = "A place to call home", createdAt = 1),
            Transaction("groceries", TransactionType.EXPENSE, "main", Money(8640, Currency.USD), date, "groceries", description = "The weekly shop", createdAt = 4),
            Transaction("coffee", TransactionType.EXPENSE, "main", Money(650, Currency.USD), date, "food", description = "Coffee & a quiet morning", createdAt = 5),
            Transaction("train", TransactionType.EXPENSE, "main", Money(2450, Currency.USD), date, "transport", description = "Train into the city", createdAt = 3),
            Transaction("save", TransactionType.TRANSFER, "main", Money(100000, Currency.USD), start, destinationId = "savings", received = Money(100000, Currency.USD), description = "Paying future me", createdAt = 2),
        ),
        budgets = listOf(Budget("food", "food", start, Money(25000, Currency.USD)), Budget("home", "home", start, Money(120000, Currency.USD))),
        preferences = Preferences(Currency.USD, "UTC", theme),
    )
    public fun getLedger(): Ledger = ledger
    override fun read() = ledger
    override fun persist(previous: Ledger, next: Ledger, replacing: Boolean) { ledger = next }
    override fun close() {}
}

fun main() {
    val output = File("docs/screenshots").apply { mkdirs() }
    val screensToRender = listOf(
        Triple("overview-light", Screen.OVERVIEW, false),
        Triple("overview-dark", Screen.OVERVIEW, true),
        Triple("overview-mobile", Screen.OVERVIEW, false),
        Triple("activity", Screen.TRANSACTIONS, false),
        Triple("accounts", Screen.ACCOUNTS, false),
        Triple("budgets", Screen.BUDGETS, false),
        Triple("insights", Screen.INSIGHTS, false),
        Triple("scheduled", Screen.RECURRING, false),
        Triple("settings", Screen.SETTINGS, false),
        Triple("transaction-editor", Screen.OVERVIEW, false),
        Triple("transaction-editor-mobile", Screen.OVERVIEW, false)
    )

    screensToRender.forEach { (name, targetScreen, isDark) ->
        val width = if (name.contains("mobile")) 420 else 1440
        val height = if (name.contains("mobile")) 1000 else 1100
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val fixtureRepo = FixtureRepository(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
        val model = FinanceViewModel(fixtureRepo, scope)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val files = object : FileGateway {
            override suspend fun save(name: String, content: String) = false
            override suspend fun readBackup(): String? = null
        }
        val isEditorModal = name.startsWith("transaction-editor")

        val scene = ImageComposeScene(width, height, content = {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (isEditorModal) {
                    LedgerTheme(ThemeMode.LIGHT) {
                        TransactionEditor(
                            existing = null,
                            rule = null,
                            scheduling = false,
                            ledger = fixtureRepo.getLedger(),
                            model = model,
                            state = FinanceUiState(ledger = fixtureRepo.getLedger()),
                            onDismiss = {}
                        )
                    }
                } else {
                    FinanceApplication(model, files, initialScreen = targetScreen)
                }
            }
        })
        try {
            repeat(4) { runCatching { scene.render(it * 20_000_000L).close() } }
            runCatching {
                scene.render(100_000_000L).use { image ->
                    image.encodeToData()!!.use { data ->
                        File(output, "$name.png").writeBytes(data.bytes)
                    }
                }
            }
            println("Rendered $name")
        } finally {
            runCatching { scene.close() }
            scope.cancel()
        }
    }
}
