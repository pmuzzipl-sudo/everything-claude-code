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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.financas.invest.AssetClass
import app.financas.invest.AssetOperation
import app.financas.invest.Holding
import app.financas.invest.InvestUiState
import app.financas.invest.targetsSummary
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.ui.theme.WarningColor
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
    onOpenOperation: (AssetOperation) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTargets: () -> Unit,
    onReconstructHistory: () -> Unit,
) {
    val tickers = state.portfolio.holdings.map { it.ticker }
    // Ao abrir (ou ao mudar a carteira/chave), atualiza cotações que estiverem antigas.
    LaunchedEffect(state.hasToken, tickers) { if (state.hasToken) onRefresh(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf("Carteira", "Lançamentos", "Análise").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> PortfolioTab(state, onRefresh, onOpenAsset, onOpenSettings, onOpenTargets)
                1 -> OperationsTab(state, onOpenOperation)
                else -> AnalysisTab(state, onReconstructHistory)
            }
            if (tab != 2) ExtendedFloatingActionButton(
                onClick = onAddOperation,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (tab == 0) "Nova operação" else "Novo lançamento") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun PortfolioTab(
    state: InvestUiState,
    onRefresh: (force: Boolean) -> Unit,
    onOpenAsset: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTargets: () -> Unit,
) {
    // Abas por classe: só as classes que existem na carteira (mais "Todos").
    val classTabs: List<AssetClass?> = listOf<AssetClass?>(null) +
        AssetClass.entries.filter { c -> state.portfolio.holdings.any { it.asset.assetClass == c } }
    var selectedClass by rememberSaveable { mutableStateOf<AssetClass?>(null) }
    val classTab = selectedClass.takeIf { it in classTabs }
    val shown = state.portfolio.holdings.filter { classTab == null || it.asset.assetClass == classTab }
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
            item { TargetsCard(state, onOpenTargets) }
            item {
                ScrollableTabRow(
                    selectedTabIndex = classTabs.indexOf(classTab).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    classTabs.forEach { c ->
                        Tab(selected = classTab == c, onClick = { selectedClass = c }, text = { Text(c?.label ?: "Todos", maxLines = 1) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "${shown.size} ativo" + if (shown.size == 1) "" else "s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val value = shown.sumOf { it.valueCents }
                    val cost = shown.sumOf { it.position.costCents }
                    Text(
                        formatMoney(value) + if (cost > 0) " (${formatPercent((value - cost).toDouble() / cost)})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = resultColor(value - cost),
                    )
                }
            }
            items(shown, key = { it.ticker }) { h ->
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
                    "❄ Congelados, sem meta: ${summary.frozen.joinToString { it.ticker }} • ${formatMoney(summary.frozenCents)} " +
                        "(${formatBps(summary.frozenBps)} do total). As porcentagens são sobre o total da carteira.",
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
            targetsCheck(summary.targetSumBps, summary.frozenBps)?.let { (msg, isError) ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) ExpenseColor else WarningColor,
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
