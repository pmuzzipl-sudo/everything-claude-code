@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.financas.ui.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.financas.invest.AssetClass
import app.financas.invest.AssetOperation
import app.financas.invest.Holding
import app.financas.invest.InvestUiState
import app.financas.invest.OperationKind
import app.financas.invest.computeSteps
import app.financas.ui.screens.LineChart
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.DATE_FORMAT
import app.financas.util.SHORT_DATE_FORMAT
import app.financas.util.formatMoney
import app.financas.util.formatPercent
import app.financas.util.formatPrice
import java.time.LocalDate
import kotlin.math.roundToLong

@Composable
fun AssetDetailScreen(
    ticker: String,
    state: InvestUiState,
    loadHistory: suspend (String) -> Result<List<Pair<Long, Double>>>,
    onAddOperation: () -> Unit,
    onOpenOperation: (AssetOperation) -> Unit,
    onSetClass: (AssetClass) -> Unit,
    onSetFrozen: (Boolean) -> Unit,
    onDeleteAsset: () -> Unit,
    onBack: () -> Unit,
) {
    val holding = (state.portfolio.holdings + state.portfolio.closed).firstOrNull { it.ticker == ticker }
    val steps = computeSteps(ticker, state.operations).reversed()
    var history by remember { mutableStateOf<List<Pair<Long, Double>>?>(null) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(ticker, state.hasToken) {
        if (!state.hasToken) return@LaunchedEffect
        loadHistory(ticker).onSuccess { history = it }.onFailure { historyError = it.message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ticker) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    IconButton(onClick = onAddOperation) { Icon(Icons.Filled.Add, contentDescription = "Nova operação") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir ativo") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            if (holding != null) item { PositionCard(holding) }
            item { PriceChartCard(history, historyError, holding?.position?.averagePrice?.takeIf { holding.position.quantity > 0 }) }
            item {
                Text("Classe", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssetClass.entries.forEach { c ->
                        FilterChip(selected = holding?.asset?.assetClass == c, onClick = { onSetClass(c) }, label = { Text(c.label) })
                    }
                }
            }
            item {
                val frozen = holding?.asset?.frozen == true
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Congelar ativo", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Para quando você parou de investir nele: continua no patrimônio, " +
                                "mas sai das metas e das sugestões de aporte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = frozen, onCheckedChange = onSetFrozen)
                }
            }
            item { Text("Operações", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            items(steps, key = { it.operation.id }) { step ->
                val op = step.operation
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenOperation(op) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${op.kind.label} • ${LocalDate.ofEpochDay(op.epochDay).format(DATE_FORMAT)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${op.quantity} × ${formatPrice(op.unitPrice)}" +
                                (if (op.feesCents > 0) " + taxas ${formatMoney(op.feesCents)}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Depois: ${step.after.quantity} cotas" +
                                (if (step.after.quantity > 0) " • PM ${formatPrice(step.after.averagePrice)}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatMoney(op.amountCents),
                            color = if (op.kind == OperationKind.SELL) IncomeColor else MaterialTheme.colorScheme.onSurface,
                        )
                        if (op.kind == OperationKind.SELL) {
                            Text(
                                "Resultado ${formatMoney(step.realizedCents)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = resultColor(step.realizedCents),
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir $ticker?") },
            text = { Text("O ativo e todas as suas operações serão excluídos.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteAsset() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PositionCard(h: Holding) {
    val p = h.position
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            h.quote?.name?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(formatMoney(h.valueCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (h.quote != null && p.quantity > 0) {
                Text(
                    "Resultado ${formatMoney(h.resultCents)} (${formatPercent(h.resultRatio)})",
                    color = resultColor(h.resultCents),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Info("Cotas", p.quantity.toString(), Modifier.weight(1f))
                Info("Preço médio", formatPrice(p.averagePrice), Modifier.weight(1f))
                Info("Cotação", h.quote?.let { formatPrice(it.price) } ?: "—", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp)) {
                Info("Custo", formatMoney(p.costCents), Modifier.weight(1f))
                Info(
                    "Hoje",
                    h.quote?.changePercent?.let { formatPercent(it / 100) } ?: "—",
                    Modifier.weight(1f),
                )
                Info("Lucro em vendas", formatMoney(p.realizedCents), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PriceChartCard(history: List<Pair<Long, Double>>?, error: String?, averagePrice: Double?) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Cotação nos últimos 3 meses", style = MaterialTheme.typography.titleSmall)
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                history == null -> Text("Carregando…", style = MaterialTheme.typography.bodySmall)
                history.size < 2 -> Text("Sem histórico disponível.", style = MaterialTheme.typography.bodySmall)
                else -> {
                    val prices = history.map { (it.second * 100).roundToLong() }
                    val pm = averagePrice?.let { (it * 100).roundToLong() }
                    val all = prices + listOfNotNull(pm)
                    val max = all.max()
                    val min = all.min()
                    Row(Modifier.padding(vertical = 4.dp)) {
                        LegendDot(IncomeColor, "Cotação")
                        if (pm != null) {
                            Spacer(Modifier.width(16.dp))
                            LegendDot(ExpenseColor, "Seu preço médio")
                        }
                    }
                    LineChart(
                        series = listOfNotNull(
                            pm?.let { ExpenseColor to List(prices.size) { _ -> it } },
                            IncomeColor to prices,
                        ),
                        max = max,
                        min = min - (max - min) / 10,
                        showPoints = false,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                    )
                    Row {
                        Text(LocalDate.ofEpochDay(history.first().first).format(SHORT_DATE_FORMAT), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text(LocalDate.ofEpochDay(history.last().first).format(SHORT_DATE_FORMAT), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
