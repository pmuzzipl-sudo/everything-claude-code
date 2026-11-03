package app.financas.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.financas.invest.InvestmentsViewModel
import app.financas.ui.invest.AssetDetailScreen
import app.financas.ui.invest.EditOperationScreen
import app.financas.ui.invest.InvestmentsScreen
import app.financas.ui.invest.TargetsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.financas.data.Category
import app.financas.ui.screens.BudgetScreen
import app.financas.ui.screens.CategoryDetailScreen
import app.financas.ui.screens.ChartsScreen
import app.financas.ui.screens.EditFixedExpenseScreen
import app.financas.ui.screens.EditTransactionScreen
import app.financas.ui.screens.FixedExpensesScreen
import app.financas.ui.screens.HomeScreen
import app.financas.ui.screens.SearchScreen
import app.financas.ui.screens.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Início", Icons.Filled.AccountBalanceWallet),
    CHARTS("charts", "Gráficos", Icons.Filled.PieChart),
    FIXED("fixed", "Fixas", Icons.Filled.EventRepeat),
    BUDGET("budget", "Orçamento", Icons.Filled.Savings),
}

private const val EDIT_ROUTE = "edit?id={id}"
private const val CATEGORY_ROUTE = "category/{category}"
private const val SEARCH_ROUTE = "search"
private const val SETTINGS_ROUTE = "settings"
private const val INVEST_ROUTE = "investments"
private const val ASSET_ROUTE = "investments/asset/{ticker}"
private const val TARGETS_ROUTE = "investments/targets"
private const val OPERATION_ROUTE = "investments/op?id={id}&ticker={ticker}"

private fun operationRoute(id: Long?, ticker: String?) = buildString {
    append("investments/op")
    val params = listOfNotNull(id?.let { "id=$it" }, ticker?.let { "ticker=$it" })
    if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
}
private const val EDIT_FIXED_ROUTE = "fixed/edit?id={id}"
private fun editFixedRoute(id: Long?) = if (id == null) "fixed/edit" else "fixed/edit?id=$id"
private fun editRoute(id: Long?) = if (id == null) "edit" else "edit?id=$id"

@Composable
fun FinanceApp(vm: FinanceViewModel, investVm: InvestmentsViewModel, accountEmail: String?, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val investState by investVm.state.collectAsStateWithLifecycle()
    val brapiToken by investVm.token.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    val inFinance = Tab.entries.any { it.route == currentRoute }
    val inInvestments = currentRoute == INVEST_ROUTE
    // Leva à tela principal de uma das duas áreas, sem empilhar telas.
    val goTo = { route: String ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (inFinance || inInvestments) {
                AreaSelector(
                    investments = inInvestments,
                    onSelect = { investments -> goTo(if (investments) INVEST_ROUTE else Tab.HOME.route) },
                    onSearch = if (inFinance) ({ nav.navigate(SEARCH_ROUTE) }) else null,
                    onSettings = { nav.navigate(SETTINGS_ROUTE) },
                )
            }
        },
        bottomBar = {
            if (Tab.entries.any { it.route == currentRoute }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    state = state,
                    onPreviousMonth = vm::previousMonth,
                    onNextMonth = vm::nextMonth,
                    onAdd = { nav.navigate(editRoute(null)) },
                    onOpen = { nav.navigate(editRoute(it.id)) },
                    onTogglePaid = vm::togglePaid,
                )
            }
            composable(SEARCH_ROUTE) {
                val all = remember { vm.allTransactions() }
                SearchScreen(
                    transactions = all,
                    onOpen = { nav.navigate(editRoute(it.id)) },
                    onTogglePaid = vm::togglePaid,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    accountEmail = accountEmail,
                    onSignOut = onSignOut,
                    brapiToken = brapiToken,
                    onSaveToken = investVm::setToken,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(INVEST_ROUTE) {
                InvestmentsScreen(
                    state = investState,
                    onRefresh = investVm::refresh,
                    onAddOperation = { nav.navigate(operationRoute(null, null)) },
                    onOpenAsset = { nav.navigate("investments/asset/$it") },
                    onOpenSettings = { nav.navigate(SETTINGS_ROUTE) },
                    onOpenTargets = { nav.navigate(TARGETS_ROUTE) },
                )
            }
            composable(TARGETS_ROUTE) {
                TargetsScreen(state = investState, onSave = investVm::saveTargets, onBack = { nav.popBackStack() })
            }
            composable(ASSET_ROUTE) { entry ->
                val ticker = entry.arguments?.getString("ticker") ?: return@composable
                AssetDetailScreen(
                    ticker = ticker,
                    state = investState,
                    loadHistory = investVm::priceHistory,
                    onAddOperation = { nav.navigate(operationRoute(null, ticker)) },
                    onOpenOperation = { nav.navigate(operationRoute(it.id, null)) },
                    onSetClass = { investVm.setAssetClass(ticker, it) },
                    onSetFrozen = { investVm.setFrozen(ticker, it) },
                    onDeleteAsset = { investVm.deleteAsset(ticker); nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                OPERATION_ROUTE,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("ticker") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                EditOperationScreen(
                    id = entry.arguments?.getLong("id")?.takeIf { it > 0 },
                    presetTicker = entry.arguments?.getString("ticker"),
                    state = investState,
                    onSave = { op, assetClass -> investVm.saveOperation(op, assetClass); nav.popBackStack() },
                    onDelete = { investVm.deleteOperation(it); nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Tab.CHARTS.route) {
                ChartsScreen(
                    state = state,
                    onPreviousMonth = vm::previousMonth,
                    onNextMonth = vm::nextMonth,
                    onOpenCategory = { nav.navigate("category/${it.name}") },
                )
            }
            composable(CATEGORY_ROUTE) { entry ->
                val category = Category.valueOf(entry.arguments?.getString("category") ?: return@composable)
                CategoryDetailScreen(
                    category = category,
                    state = state,
                    onPreviousMonth = vm::previousMonth,
                    onNextMonth = vm::nextMonth,
                    onOpen = { nav.navigate(editRoute(it.id)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Tab.FIXED.route) {
                FixedExpensesScreen(
                    fixedExpenses = state.fixedExpenses,
                    onAdd = { nav.navigate(editFixedRoute(null)) },
                    onOpen = { nav.navigate(editFixedRoute(it.id)) },
                )
            }
            composable(
                EDIT_FIXED_ROUTE,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                EditFixedExpenseScreen(
                    id = id,
                    load = vm::fixedExpense,
                    onSave = { vm.saveFixedExpense(it); nav.popBackStack() },
                    onDelete = { vm.deleteFixedExpense(it); nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Tab.BUDGET.route) {
                BudgetScreen(
                    state = state,
                    onPreviousMonth = vm::previousMonth,
                    onNextMonth = vm::nextMonth,
                    onSetBudget = vm::setBudget,
                )
            }
            composable(
                EDIT_ROUTE,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
                EditTransactionScreen(
                    id = id,
                    load = vm::transaction,
                    onSave = { vm.save(it); nav.popBackStack() },
                    onDelete = { vm.delete(it); nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

/** Seletor das duas áreas do app, com os atalhos de busca (só em Finanças) e configurações. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaSelector(
    investments: Boolean,
    onSelect: (investments: Boolean) -> Unit,
    onSearch: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            listOf(false to "Finanças", true to "Investimentos").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = investments == value,
                    onClick = { if (investments != value) onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label, maxLines = 1) }
            }
        }
        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Buscar") }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Configurações") }
    }
}
