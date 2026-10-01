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
import app.financas.data.endMonth
import app.financas.data.installmentsUntil
import app.financas.data.splitInstallments
import app.financas.data.startMonth
import app.financas.util.formatMoney
import app.financas.util.label
import java.time.YearMonth

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
                Text("Despesas fixas", style = MaterialTheme.typography.titleLarge)
                Text(
                    "O valor total é dividido em parcelas, lançadas automaticamente na aba \"Fixas\" de cada mês.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
            if (fixedExpenses.isEmpty()) {
                item {
                    Text(
                        "Nenhuma despesa fixa cadastrada.\nToque em \"Nova\" para adicionar.",
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

@Composable
private fun FixedExpenseCard(fixed: FixedExpense, today: YearMonth, onClick: () -> Unit) {
    val parcels = splitInstallments(fixed.totalCents, fixed.installments)
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
                Text(formatMoney(fixed.totalCents), fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${fixed.installments}x de ${formatMoney(parcels.first())} • ${fixed.category.label}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (fixed.installments == 1) fixed.startMonth.label()
                else "${fixed.startMonth.label()} a ${fixed.endMonth.label()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { due.toFloat() / fixed.installments },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    due == 0 -> "Começa em ${fixed.startMonth.label()}"
                    due == fixed.installments -> "Todas as ${fixed.installments} parcelas já venceram"
                    else -> "$due de ${fixed.installments} parcelas até este mês • " +
                        "restam ${formatMoney(parcels.drop(due).sum())}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
