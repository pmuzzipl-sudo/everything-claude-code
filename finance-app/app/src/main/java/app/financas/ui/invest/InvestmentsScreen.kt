package app.financas.ui.invest

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.financas.invest.Holding
import app.financas.invest.InvestUiState
import app.financas.invest.PortfolioPoint
import app.financas.invest.targetsSummary
import app.financas.ui.screens.DonutChart
import app.financas.ui.screens.LineChart
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.ui.theme.WarningColor
import app.financas.util.SHORT_DATE_FORMAT
import app.financas.util.formatMoney
import app.financas.util.formatPercent
import app.financas.util.formatPrice
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm")

@Composable
fun InvestmentsScreen(
    state: InvestUiState,
    onRefresh: (force: Boolean) -> Unit,
    onAddOperation: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTargets: () -> Unit,
) {
    val tickers = state.portfolio.holdings.map { it.ticker }
    // Ao abrir (ou ao mudar a carteira/chave), atualiza cotações que estiverem antigas.
    LaunchedEffect(state.hasToken, tickers) { if (state.hasToken) onRefresh(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)) {
            if (!state.hasToken) {
                item {
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Cotações automáticas", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Cadastre a chave gratuita do brapi.dev nas Configurações para ver o valor atual da carteira.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            Button(onClick = onOpenSettings) { Text("Abrir Configurações") }
                        }
                    }
                }
            }
            item { SummaryCard(state, onRefresh = { onRefresh(true) }) }
            state.error?.let { msg ->
                item { Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (state.portfolio.holdings.isEmpty()) {
                item {
                    Text(
                        "Nenhum ativo ainda.\nToque em \"Nova operação\" e cadastre a posição inicial de cada ETF " +
                            "(quantidade e preço médio).",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            } else {
                item { AllocationCard(state) }
                item { TargetsCard(state, onOpenTargets) }
                if (state.history.size >= 2) item { EvolutionCard(state.history) }
                item { Text("Ativos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                items(state.portfolio.holdings, key = { it.ticker }) { h ->
                    HoldingRow(h, onClick = { onOpenAsset(h.ticker) })
                    HorizontalDivider()
                }
            }
            if (state.portfolio.closed.isNotEmpty()) {
                item {
                    Text("Vendidos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                items(state.portfolio.closed, key = { "closed-" + it.ticker }) { h ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenAsset(h.ticker) }.padding(vertical = 10.dp)) {
                        Text(h.ticker, Modifier.weight(1f))
                        Text(
                            "Lucro realizado ${formatMoney(h.position.realizedCents)}",
                            color = if (h.position.realizedCents < 0) ExpenseColor else IncomeColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAddOperation,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nova operação") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun SummaryCard(state: InvestUiState, onRefresh: () -> Unit) {
    val p = state.portfolio
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Patrimônio", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (state.refreshing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (state.hasToken) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar cotações")
                    }
                }
            }
            Text(formatMoney(p.valueCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Resultado ${formatMoney(p.resultCents)} (${formatPercent(p.resultRatio)})",
                color = resultColor(p.resultCents),
                fontWeight = FontWeight.SemiBold,
            )
            if (p.dayChangeCents != 0L) {
                Text("Hoje ${formatMoney(p.dayChangeCents)}", color = resultColor(p.dayChangeCents), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("Investido (custo): ${formatMoney(p.investedCents)}", style = MaterialTheme.typography.bodySmall)
            if (p.realizedCents != 0L) {
                Text("Lucro realizado em vendas: ${formatMoney(p.realizedCents)}", style = MaterialTheme.typography.bodySmall)
            }
            state.quotesAtMillis?.let {
                Text(
                    "Cotações de ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(TIME)} (atraso de até ~30 min)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AllocationCard(state: InvestUiState) {
    val allocation = state.portfolio.allocation
    val total = allocation.sumOf { it.second }
    if (total == 0L) return
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = allocation.map { (c, v) -> Color(c.color) to v.toFloat() },
                onSliceClick = {},
                modifier = Modifier.size(110.dp),
            )
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Divisão da carteira", style = MaterialTheme.typography.titleSmall)
                allocation.forEach { (c, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(Color(c.color), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("${c.label} ${v * 100 / total}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvolutionCard(history: List<PortfolioPoint>) {
    val points = history.takeLast(365)
    val values = points.map { it.valueCents }
    val invested = points.map { it.investedCents }
    val max = (values + invested).max()
    val min = (values + invested).min()
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Evolução do patrimônio", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(vertical = 4.dp)) {
                LegendDot(MaterialTheme.colorScheme.primary, "Patrimônio")
                Spacer(Modifier.width(16.dp))
                LegendDot(MaterialTheme.colorScheme.outline, "Investido")
            }
            LineChart(
                series = listOf(MaterialTheme.colorScheme.outline to invested, MaterialTheme.colorScheme.primary to values),
                max = max,
                min = min - (max - min) / 10,
                showPoints = points.size <= 31,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Row {
                Text(LocalDate.ofEpochDay(points.first().epochDay).format(SHORT_DATE_FORMAT), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(LocalDate.ofEpochDay(points.last().epochDay).format(SHORT_DATE_FORMAT), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "Um ponto por dia em que as cotações foram atualizadas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HoldingRow(h: Holding, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(Color(h.asset.assetClass.color), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                h.ticker + if (h.asset.frozen) "  ❄ congelado" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${h.position.quantity} cotas • PM ${formatPrice(h.position.averagePrice)}" +
                    (h.quote?.let { " • ${formatPrice(it.price)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatMoney(h.valueCents), fontWeight = FontWeight.SemiBold)
            Text(
                if (h.quote == null) "sem cotação" else formatPercent(h.resultRatio),
                style = MaterialTheme.typography.bodySmall,
                color = if (h.quote == null) MaterialTheme.colorScheme.onSurfaceVariant else resultColor(h.resultCents),
            )
        }
    }
}

@Composable
internal fun resultColor(cents: Long): Color = when {
    cents > 0 -> IncomeColor
    cents < 0 -> ExpenseColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Participação atual × meta de cada ativo, com quanto comprar ou quanto está acima. */
@Composable
private fun TargetsCard(state: InvestUiState, onOpenTargets: () -> Unit) {
    val summary = targetsSummary(state.portfolio)
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Metas da carteira", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenTargets) { Text(if (summary.hasTargets) "Metas e aporte" else "Definir metas") }
            }
            if (summary.frozen.isNotEmpty()) {
                Text(
                    "❄ Congelados, fora das metas: ${summary.frozen.joinToString { it.ticker }} • ${formatMoney(summary.frozenCents)}. " +
                        "As porcentagens abaixo são sobre ${formatMoney(summary.totalCents)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (!summary.hasTargets) {
                Text(
                    "Defina quanto cada ativo deve representar na carteira e acompanhe se está acima ou abaixo da meta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            if (!summary.isComplete) {
                Text(
                    "As metas somam ${formatBps(summary.targetSumBps)} (o ideal é 100%).",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseColor,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            summary.rows.forEach { row ->
                val target = row.targetRatio
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.ticker, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            "${formatRatio(row.currentRatio)}" + (target?.let { " / meta ${formatRatio(it)}" } ?: " / sem meta"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TargetBar(row.currentRatio, target, Color(row.holding.asset.assetClass.color))
                    row.adjustCents(summary.totalCents)?.let { adjust ->
                        val diff = row.diffRatio ?: 0.0
                        Text(
                            when {
                                abs(diff) < 0.005 -> "Na meta"
                                adjust > 0 -> "Abaixo da meta: comprar ${formatMoney(adjust)}"
                                else -> "Acima da meta em ${formatMoney(-adjust)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                abs(diff) < 0.005 -> MaterialTheme.colorScheme.onSurfaceVariant
                                adjust > 0 -> WarningColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Barra com a participação atual; o traço marca a meta. */
@Composable
private fun TargetBar(current: Double, target: Double?, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(10.dp).padding(vertical = 2.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(
            color,
            size = Size(size.width * current.toFloat().coerceIn(0f, 1f), size.height),
            cornerRadius = r,
        )
        if (target != null) {
            val x = size.width * target.toFloat().coerceIn(0f, 1f)
            drawLine(marker, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = 4f)
        }
    }
}
