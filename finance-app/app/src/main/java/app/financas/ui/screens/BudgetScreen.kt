package app.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.financas.data.Category
import app.financas.ui.BudgetStatus
import app.financas.ui.FinanceUiState
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.WarningColor
import app.financas.util.centsToInput
import app.financas.util.formatMoney
import app.financas.util.parseMoney

@Composable
fun BudgetScreen(
    state: FinanceUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetBudget: (Category, Long?) -> Unit,
) {
    var editing by remember { mutableStateOf<BudgetStatus?>(null) }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
        item { MonthSelector(state.month, onPreviousMonth, onNextMonth) }
        item {
            Text(
                "Toque em uma categoria para definir o limite mensal. " +
                    "Você será avisado ao atingir 80% do limite.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(state.budgets, key = { it.category.name }) { status ->
            BudgetRow(status, onClick = { editing = status })
            HorizontalDivider()
        }
    }

    editing?.let { status ->
        BudgetDialog(
            status = status,
            onDismiss = { editing = null },
            onSave = { onSetBudget(status.category, it); editing = null },
        )
    }
}

@Composable
private fun BudgetRow(status: BudgetStatus, onClick: () -> Unit) {
    val color = when {
        status.isOver -> ExpenseColor
        status.isNear -> WarningColor
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(12.dp).background(Color(status.category.color), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(status.category.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                if (status.limitCents == null) formatMoney(status.spentCents)
                else "${formatMoney(status.spentCents)} / ${formatMoney(status.limitCents)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (status.limitCents == null) {
            Text(
                "Sem limite definido",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { status.ratio.toFloat().coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
            val remaining = status.limitCents - status.spentCents
            Text(
                if (remaining >= 0) "Restam ${formatMoney(remaining)}" else "Excedido em ${formatMoney(-remaining)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun BudgetDialog(status: BudgetStatus, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember { mutableStateOf(status.limitCents?.let(::centsToInput) ?: "") }
    val parsed = parseMoney(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limite: ${status.category.label}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Valor mensal (R$)") },
                singleLine = true,
                isError = text.isNotBlank() && parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(parsed) }, enabled = parsed != null) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (status.limitCents != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Remover") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
