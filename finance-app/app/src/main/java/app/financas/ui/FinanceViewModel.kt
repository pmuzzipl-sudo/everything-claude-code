package app.financas.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.financas.data.Budget
import app.financas.data.Category
import app.financas.data.FinanceDatabase
import app.financas.data.Transaction
import app.financas.data.TransactionType
import app.financas.util.formatMoney
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Percentual do orçamento a partir do qual o usuário é avisado. */
const val BUDGET_WARNING_RATIO = 0.8

data class MonthSummary(val incomeCents: Long = 0, val expenseCents: Long = 0) {
    val balanceCents get() = incomeCents - expenseCents
}

data class MonthTotals(val month: YearMonth, val incomeCents: Long, val expenseCents: Long)

data class BudgetStatus(val category: Category, val limitCents: Long?, val spentCents: Long) {
    val ratio: Double get() = if (limitCents == null || limitCents == 0L) 0.0 else spentCents.toDouble() / limitCents
    val isOver get() = limitCents != null && spentCents > limitCents
    val isNear get() = limitCents != null && !isOver && ratio >= BUDGET_WARNING_RATIO
}

data class FinanceUiState(
    val month: YearMonth = YearMonth.now(),
    val transactions: List<Transaction> = emptyList(),
    val summary: MonthSummary = MonthSummary(),
    val expensesByCategory: List<Pair<Category, Long>> = emptyList(),
    val history: List<MonthTotals> = emptyList(),
    val budgets: List<BudgetStatus> = emptyList(),
)

private const val HISTORY_MONTHS = 6L

private fun YearMonth.firstDay() = atDay(1).toEpochDay()
private fun YearMonth.lastDay() = atEndOfMonth().toEpochDay()
private fun Transaction.month() = YearMonth.from(LocalDate.ofEpochDay(epochDay))

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FinanceDatabase.get(application).dao()

    private val month = MutableStateFlow(YearMonth.now())

    private val messages = Channel<String>(Channel.BUFFERED)
    /** Mensagens únicas (avisos de orçamento) para exibir em Snackbar. */
    val events = messages.receiveAsFlow()

    // Carrega o mês selecionado e os anteriores de uma vez, para o gráfico de histórico.
    private val recentTransactions = month.flatMapLatest { m ->
        dao.transactionsBetween(m.minusMonths(HISTORY_MONTHS - 1).firstDay(), m.lastDay())
    }

    val state: StateFlow<FinanceUiState> =
        combine(month, recentTransactions, dao.budgets()) { m, recent, budgets ->
            buildState(m, recent, budgets)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }

    suspend fun transaction(id: Long): Transaction? = dao.transaction(id)

    fun save(transaction: Transaction) {
        viewModelScope.launch {
            dao.upsert(transaction)
            if (transaction.type == TransactionType.EXPENSE) checkBudget(transaction)
        }
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch { dao.delete(transaction) }
    }

    fun setBudget(category: Category, limitCents: Long?) {
        viewModelScope.launch {
            if (limitCents == null) dao.deleteBudget(category) else dao.upsert(Budget(category, limitCents))
        }
    }

    private suspend fun checkBudget(transaction: Transaction) {
        val limit = dao.budget(transaction.category)?.limitCents ?: return
        val m = transaction.month()
        val spent = dao.expenseTotal(transaction.category, m.firstDay(), m.lastDay())
        val name = transaction.category.label
        when {
            spent > limit -> messages.send(
                "Orçamento de $name estourado: ${formatMoney(spent)} de ${formatMoney(limit)}"
            )
            spent >= limit * BUDGET_WARNING_RATIO -> messages.send(
                "Atenção: ${(spent * 100 / limit)}% do orçamento de $name usado"
            )
        }
    }

    private fun buildState(m: YearMonth, recent: List<Transaction>, budgets: List<Budget>): FinanceUiState {
        val current = recent.filter { it.month() == m }
        val expenses = current.filter { it.type == TransactionType.EXPENSE }
        val spentByCategory = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountCents } }
        val limits = budgets.associate { it.category to it.limitCents }

        val history = (HISTORY_MONTHS - 1 downTo 0).map { back ->
            val hm = m.minusMonths(back)
            val items = recent.filter { it.month() == hm }
            MonthTotals(
                month = hm,
                incomeCents = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                expenseCents = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
            )
        }

        return FinanceUiState(
            month = m,
            transactions = current,
            summary = MonthSummary(
                incomeCents = current.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                expenseCents = expenses.sumOf { it.amountCents },
            ),
            expensesByCategory = spentByCategory.toList().sortedByDescending { it.second },
            history = history,
            budgets = Category.of(TransactionType.EXPENSE).map { c ->
                BudgetStatus(c, limits[c], spentByCategory[c] ?: 0)
            },
        )
    }
}
