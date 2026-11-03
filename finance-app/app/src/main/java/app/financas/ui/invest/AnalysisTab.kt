@file:OptIn(ExperimentalLayoutApi::class)

package app.financas.ui.invest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.financas.invest.AnalysisPeriod
import app.financas.invest.InvestUiState
import app.financas.invest.analysisSeries
import app.financas.ui.screens.DonutChart
import app.financas.ui.screens.LineChart
import app.financas.util.DATE_FORMAT
import app.financas.util.SHORT_DATE_FORMAT
import app.financas.util.formatMoney
import app.financas.util.formatPercent
import java.time.LocalDate

@Composable
internal fun AnalysisTab(state: InvestUiState, onReconstructHistory: () -> Unit) {
    // Na primeira vez, monta os últimos 3 meses com os preços passados.
    LaunchedEffect(state.hasToken, state.portfolio.holdings.size) { onReconstructHistory() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (state.portfolio.holdings.isEmpty()) {
            Text(
                "Cadastre seus ativos na aba Carteira para ver a análise.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
            return@Column
        }
        AllocationCard(state)
        Spacer(Modifier.height(12.dp))
        EvolutionCard(state)
    }
}

/** Divisão da carteira por classe e por ativo. */
@Composable
private fun AllocationCard(state: InvestUiState) {
    val allocation = state.portfolio.allocation
    val total = allocation.sumOf { it.second }
    if (total == 0L) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Divisão da carteira", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = allocation.map { (c, v) -> Color(c.color) to v.toFloat() },
                    onSliceClick = {},
                    modifier = Modifier.size(140.dp),
                )
                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allocation.forEach { (c, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Color(c.color), CircleShape))
                            Column(Modifier.padding(start = 6.dp)) {
                                Text("${c.label} • ${v * 100 / total}%", style = MaterialTheme.typography.bodyMedium)
                                Text(formatMoney(v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Por ativo", style = MaterialTheme.typography.titleSmall)
            state.portfolio.holdings.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(h.asset.assetClass.color), CircleShape))
                    Text(
                        h.ticker + if (h.asset.frozen) " ❄" else "",
                        Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(formatRatio(h.valueCents.toDouble() / total), style = MaterialTheme.typography.bodySmall)
                    Text(formatMoney(h.valueCents), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/** Acompanhamento da carteira (ou de um ativo) no período escolhido: valor de mercado × custo. */
@Composable
private fun EvolutionCard(state: InvestUiState) {
    var period by rememberSaveable { mutableStateOf(AnalysisPeriod.SIX_MONTHS) }
    var ticker by rememberSaveable { mutableStateOf<String?>(null) }
    val tickers = state.portfolio.holdings.map { it.ticker } +
        state.history.flatMap { it.assets.keys }.distinct().filter { t -> state.portfolio.holdings.none { it.ticker == t } }.sorted()
    val selected = ticker.takeIf { it in tickers }
    val today = LocalDate.now()
    val series = analysisSeries(state.history, selected, period, today)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Acompanhamento", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                AnalysisPeriod.entries.forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                }
            }
            Text("Ver", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == null, onClick = { ticker = null }, label = { Text("Carteira total") })
                tickers.forEach { t ->
                    FilterChip(selected = selected == t, onClick = { ticker = t }, label = { Text(t) })
                }
            }

            if (series.size < 2) {
                Text(
                    if (state.hasToken) "Ainda não há dados suficientes para este gráfico. O histórico é montado com os " +
                        "últimos 3 meses de cotações e cresce a cada dia em que as cotações são atualizadas."
                    else "Cadastre a chave do brapi.dev em Configurações para montar o histórico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                return@Column
            }

            val first = series.first()
            val last = series.last()
            val result = last.valueCents - last.costCents
            // Ganho no período descontando novos aportes/vendas (variação do resultado).
            val periodGain = result - (first.valueCents - first.costCents)
            Row(Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Valor atual", style = MaterialTheme.typography.labelSmall)
                    Text(formatMoney(last.valueCents), fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Resultado", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatMoney(result) + if (last.costCents > 0) " (${formatPercent(result.toDouble() / last.costCents)})" else "",
                        fontWeight = FontWeight.SemiBold,
                        color = resultColor(result),
                    )
                }
            }
            Text(
                "Ganho no período: ${formatMoney(periodGain)}",
                style = MaterialTheme.typography.bodySmall,
                color = resultColor(periodGain),
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                LegendDot(MaterialTheme.colorScheme.primary, "Valor de mercado")
                Spacer(Modifier.width(16.dp))
                LegendDot(MaterialTheme.colorScheme.outline, "Investido (custo)")
            }
            val values = series.map { it.valueCents }
            val costs = series.map { it.costCents }
            val max = (values + costs).max()
            val min = (values + costs).min()
            LineChart(
                series = listOf(MaterialTheme.colorScheme.outline to costs, MaterialTheme.colorScheme.primary to values),
                max = max,
                min = (min - (max - min) / 10).coerceAtLeast(0),
                showPoints = series.size <= 31,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            Row {
                Text(LocalDate.ofEpochDay(first.epochDay).format(SHORT_DATE_FORMAT), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(LocalDate.ofEpochDay(last.epochDay).format(SHORT_DATE_FORMAT), style = MaterialTheme.typography.labelSmall)
            }
            val since = today.minusMonths(period.months)
            if (LocalDate.ofEpochDay(first.epochDay).isAfter(since.plusDays(7))) {
                Text(
                    "Histórico disponível desde ${LocalDate.ofEpochDay(first.epochDay).format(DATE_FORMAT)}; " +
                        "o período de ${period.label} vai se completando com o tempo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
