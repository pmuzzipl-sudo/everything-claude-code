package app.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.financas.data.Transaction
import app.financas.data.TransactionType
import app.financas.ui.FinanceUiState
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.ui.theme.WarningColor
import app.financas.util.DATE_FORMAT
import app.financas.util.formatMoney
import java.time.LocalDate

private enum class HomeTab(val label: String, val filter: (Transaction) -> Boolean) {
    VARIABLE("Variáveis", { it.type == TransactionType.EXPENSE && !it.isFixed }),
    FIXED("Fixas", { it.type == TransactionType.EXPENSE && it.isFixed }),
    INCOME("Receitas", { it.type == TransactionType.INCOME }),
}

@Composable
fun HomeScreen(
    state: FinanceUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Transaction) -> Unit,
) {
    val alerts = state.budgets.filter { it.isOver || it.isNear }
    var tab by rememberSaveable { mutableStateOf(HomeTab.VARIABLE) }
    val shown = state.transactions.filter(tab.filter)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
            item { MonthSelector(state.month, onPreviousMonth, onNextMonth) }
            item { SummaryCard(state) }
            if (alerts.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            alerts.forEach { status ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = if (status.isOver) ExpenseColor else WarningColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (status.isOver) "${status.category.label}: orçamento estourado"
                                        else "${status.category.label}: ${(status.ratio * 100).toInt()}% do orçamento",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(top = 16.dp)) {
                    HomeTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t },
                            text = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "${shown.size} lançamento" + if (shown.size == 1) "" else "s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Total: ${formatMoney(shown.sumOf { it.amountCents })}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        when (tab) {
                            HomeTab.FIXED -> "Nenhuma despesa fixa neste mês.\nCadastre-as na aba \"Fixas\"."
                            HomeTab.VARIABLE -> "Nenhuma despesa variável neste mês.\nToque em \"Novo\" para adicionar."
                            HomeTab.INCOME -> "Nenhuma receita neste mês.\nToque em \"Novo\" para adicionar."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { t ->
                TransactionRow(t, onClick = { onOpen(t) })
                HorizontalDivider()
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Novo") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SummaryCard(state: FinanceUiState) {
    val s = state.summary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Saldo do mês", style = MaterialTheme.typography.labelLarge)
            Text(
                formatMoney(s.balanceCents),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (s.balanceCents < 0) ExpenseColor else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Receitas", style = MaterialTheme.typography.labelMedium)
                    Text(formatMoney(s.incomeCents), color = IncomeColor, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Despesas", style = MaterialTheme.typography.labelMedium)
                    Text(formatMoney(s.expenseCents), color = ExpenseColor, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Fixas ${formatMoney(s.fixedCents)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        "Variáveis ${formatMoney(s.variableCents)}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(t: Transaction, onClick: () -> Unit) {
    val income = t.type == TransactionType.INCOME
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(Color(t.category.color), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.description.ifBlank { t.category.label },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(t.category.label)
                    if (t.installment != null) append(" • parcela ${t.installment}/${t.installmentCount}")
                    append(" • ${LocalDate.ofEpochDay(t.epochDay).format(DATE_FORMAT)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (income) "+ " else "- ") + formatMoney(t.amountCents),
            color = if (income) IncomeColor else ExpenseColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
