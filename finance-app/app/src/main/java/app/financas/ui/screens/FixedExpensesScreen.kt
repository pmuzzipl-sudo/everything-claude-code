package app.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.financas.data.FixedExpense
import app.financas.data.FixedMode
import app.financas.data.TransactionType
import app.financas.data.amountIn
import app.financas.data.endMonth
import app.financas.data.firstAmount
import app.financas.data.fixedCommitments
import app.financas.data.installmentsUntil
import app.financas.data.startMonth
import app.financas.ui.comparisonMonths
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.compactLabel
import app.financas.util.formatMoney
import app.financas.util.label
import java.time.YearMonth

private const val COMMITMENT_MONTHS = 6

@Composable
fun FixedExpensesScreen(
    fixedExpenses: List<FixedExpense>,
    onAdd: () -> Unit,
    onOpen: (FixedExpense) -> Unit,
) {
    val today = YearMonth.now()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)) {
            item {
                Text("Fixas e recorrentes", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Despesas e receitas que se repetem: o app lança cada mês automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
            if (fixedExpenses.isNotEmpty()) {
                item { CommitmentCard(fixedExpenses, today) }
            } else {
                item {
                    Text(
                        "Nada cadastrado ainda.\nToque em \"Nova\" para adicionar aluguel, salário, financiamentos...",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
            items(fixedExpenses, key = { it.id }) { fixed ->
                FixedExpenseCard(fixed, today, onClick = { onOpen(fixed) })
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nova") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

/** Quanto das receitas e despesas dos próximos meses já está comprometido pelas fixas. */
@Composable
private fun CommitmentCard(fixed: List<FixedExpense>, today: YearMonth) {
    val months = comparisonMonths(today.plusMonths(COMMITMENT_MONTHS - 1L), COMMITMENT_MONTHS)
    val rows = fixedCommitments(fixed, months)
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Próximos $COMMITMENT_MONTHS meses", style = MaterialTheme.typography.titleSmall)
            Text(
                "Valores já comprometidos pelas fixas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row {
                Text("Mês", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("Receitas", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                Text("Despesas", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                Text("Saldo", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
            rows.forEach { r ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                val balance = r.incomeCents - r.expenseCents
                Row {
                    Text(r.month.compactLabel(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(formatMoney(r.incomeCents), Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, color = IncomeColor, textAlign = TextAlign.End)
                    Text(formatMoney(r.expenseCents), Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall, color = ExpenseColor, textAlign = TextAlign.End)
                    Text(
                        formatMoney(balance),
                        Modifier.weight(1.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (balance < 0) ExpenseColor else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun FixedExpenseCard(fixed: FixedExpense, today: YearMonth, onClick: () -> Unit) {
    val income = fixed.type == TransactionType.INCOME
    val months = fixed.months
    val due = fixed.installmentsUntil(today)
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.clickable(onClick = onClick).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(fixed.category.color), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    fixed.description.ifBlank { fixed.category.label },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    (if (income) "+ " else "- ") + formatMoney(fixed.firstAmount()) + if (fixed.mode == FixedMode.RECURRING) "/mês" else "",
                    fontWeight = FontWeight.SemiBold,
                    color = if (income) IncomeColor else ExpenseColor,
                )
            }
            Text(
                buildString {
                    append(if (income) "Receita • " else "Despesa • ")
                    append(fixed.category.label)
                    when {
                        fixed.mode == FixedMode.INSTALLMENTS -> append(" • ${months}x, total ${formatMoney(fixed.amountCents)}")
                        months == null -> append(" • todo mês, sem data de fim")
                        else -> append(" • por $months meses")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                when (val end = fixed.endMonth) {
                    null -> "Desde ${fixed.startMonth.label()}"
                    fixed.startMonth -> fixed.startMonth.label()
                    else -> "${fixed.startMonth.label()} a ${end.label()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (months != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { due.toFloat() / months }, modifier = Modifier.fillMaxWidth())
                val remaining = (due until months).sumOf { i -> fixed.amountIn(fixed.startMonth.plusMonths(i.toLong())) }
                Text(
                    when {
                        due == 0 -> "Começa em ${fixed.startMonth.label()}"
                        due == months -> "Concluída: todos os $months meses já venceram"
                        else -> "$due de $months até este mês • ${if (income) "a receber" else "restam"} ${formatMoney(remaining)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
