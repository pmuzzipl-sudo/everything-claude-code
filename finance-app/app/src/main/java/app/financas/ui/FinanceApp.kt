package app.financas.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import androidx.lifecycle.viewmodel.compose.viewModel
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
import app.financas.ui.screens.EditTransactionScreen
import app.financas.ui.screens.HomeScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Início", Icons.Filled.AccountBalanceWallet),
    CHARTS("charts", "Gráficos", Icons.Filled.PieChart),
    BUDGET("budget", "Orçamento", Icons.Filled.Savings),
}

private const val EDIT_ROUTE = "edit?id={id}"
private const val CATEGORY_ROUTE = "category/{category}"
private fun editRoute(id: Long?) = if (id == null) "edit" else "edit?id=$id"

@Composable
fun FinanceApp(vm: FinanceViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
