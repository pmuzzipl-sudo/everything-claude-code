@file:OptIn(ExperimentalLayoutApi::class)

package app.financas.ui.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.financas.invest.AssetOperation
import app.financas.invest.InvestUiState
import app.financas.invest.OperationKind
import app.financas.invest.ledger
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.SHORT_DATE_FORMAT
import app.financas.util.formatMoney
import app.financas.util.formatPrice
import app.financas.util.label
import java.time.LocalDate

/** Registro de todas as compras, vendas e posições iniciais, mês a mês. */
@Composable
internal fun OperationsTab(state: InvestUiState, onOpenOperation: (AssetOperation) -> Unit) {
    var ticker by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf<OperationKind?>(null) }
    val tickers = state.operations.map { it.ticker }.distinct().sorted()
    val months = ledger(state.operations, ticker, kind)

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("Todos") })
                OperationKind.entries.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = if (kind == k) null else k },
                        label = { Text(if (k == OperationKind.INITIAL) "Iniciais" else k.label + "s") },
                    )
                }
            }
            if (tickers.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ticker == null, onClick = { ticker = null }, label = { Text("Todos os ativos") })
                    tickers.forEach { t ->
                        FilterChip(selected = ticker == t, onClick = { ticker = if (ticker == t) null else t }, label = { Text(t) })
                    }
                }
            }
        }
        if (months.isEmpty()) {
            item {
                Text(
                    if (state.operations.isEmpty()) "Nenhum lançamento ainda.\nToque em \"Novo lançamento\" para registrar compras e vendas."
                    else "Nenhum lançamento com esses filtros.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        }
        months.forEach { month ->
            item(key = "m-${month.month}") {
                Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                    Text(month.month.label(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildList {
                            if (month.boughtCents > 0) add("Aplicado ${formatMoney(month.boughtCents)}")
                            if (month.soldCents > 0) add("Vendido ${formatMoney(month.soldCents)}")
                        }.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (month.realizedCents != 0L) {
                        Text(
                            "Lucro em vendas: ${formatMoney(month.realizedCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = resultColor(month.realizedCents),
                        )
                    }
                }
            }
            items(month.entries, key = { it.operation.id }) { step ->
                val op = step.operation
                val sell = op.kind == OperationKind.SELL
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenOperation(op) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${op.ticker} • ${op.kind.label}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${LocalDate.ofEpochDay(op.epochDay).format(SHORT_DATE_FORMAT)} • ${op.quantity} × ${formatPrice(op.unitPrice)}" +
                                if (op.feesCents > 0) " + taxas ${formatMoney(op.feesCents)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (step.after.quantity > 0) "Depois: ${step.after.quantity} cotas • PM ${formatPrice(step.after.averagePrice)}"
                            else "Depois: posição zerada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            (if (sell) "+ " else "- ") + formatMoney(if (sell) op.amountCents - op.feesCents else op.amountCents + op.feesCents),
                            color = if (sell) IncomeColor else ExpenseColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (sell) {
                            Text("Resultado ${formatMoney(step.realizedCents)}", style = MaterialTheme.typography.bodySmall, color = resultColor(step.realizedCents))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
