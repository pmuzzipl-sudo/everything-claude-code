@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package app.financas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.financas.data.Category
import app.financas.ui.ExpenseKind
import app.financas.ui.FinanceUiState
import app.financas.ui.LOADED_MONTHS
import app.financas.ui.comparisonMonths
import app.financas.ui.expenseTotalsByCategory
import app.financas.ui.monthOverMonthChange
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.compactLabel
import app.financas.util.formatMoney
import java.time.YearMonth

private val PERIODS = listOf(3, 6, LOADED_MONTHS)
private const val DEFAULT_SELECTED = 3

@Composable
fun ComparisonTab(
    state: FinanceUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenCategory: (Category) -> Unit,
) {
    var period by rememberSaveable { mutableIntStateOf(6) }
    var kind by rememberSaveable { mutableStateOf(ExpenseKind.ALL) }
    // null = seleção automática (as categorias com mais gastos no período).
    var chosen by rememberSaveable { mutableStateOf<List<String>?>(null) }

    val months = comparisonMonths(state.month, period)
    val series = expenseTotalsByCategory(state.recentTransactions, months, kind)
    val available = series.map { it.first }
    val selected = chosen?.map(Category::valueOf)?.filter { it in available }
        ?: available.take(DEFAULT_SELECTED)
    val shown = series.filter { it.first in selected }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        MonthSelector(state.month, onPreviousMonth, onNextMonth)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Filtros", style = MaterialTheme.typography.titleMedium)
                FilterLabel("Período (até ${state.month.compactLabel()})")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PERIODS.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text("$p meses") })
                    }
                }
                FilterLabel("Tipo de despesa")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterLabel("Categorias", Modifier.weight(1f))
                    if (available.isNotEmpty()) {
                        TextButton(onClick = {
                            chosen = if (selected.size == available.size) emptyList() else available.map { it.name }
                        }) { Text(if (selected.size == available.size) "Limpar" else "Todas") }
                    }
                }
                if (available.isEmpty()) {
                    Text(
                        "Sem despesas no período.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { c ->
                            val isOn = c in selected
                            FilterChip(
                                selected = isOn,
                                onClick = {
                                    chosen = (if (isOn) selected - c else selected + c).map { it.name }
                                },
                                label = { Text(c.label) },
                                leadingIcon = {
                                    Box(Modifier.size(10.dp).background(Color(c.color), CircleShape))
                                },
                            )
                        }
                    }
                }
            }
        }

        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Evolução mensal", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    val max = shown.maxOf { (_, values) -> values.max() }
                    Text(
                        "Máximo: ${formatMoney(max)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LineChart(
                        series = shown.map { (c, values) -> Color(c.color) to values },
                        max = max,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    MonthAxis(months)
                }
            }

            shown.forEach { (category, values) ->
                Spacer(Modifier.height(12.dp))
                CategoryComparisonCard(category, months, values, onOpen = { onOpenCategory(category) })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilterLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LineChart(series: List<Pair<Color, List<Long>>>, max: Long, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val count = series.first().second.size
        val stepX = if (count > 1) size.width / (count - 1) else 0f
        val top = 8.dp.toPx()
        val chartHeight = size.height - top
        fun y(value: Long) = top + chartHeight * (1f - value.toFloat() / max.coerceAtLeast(1))

        // Linhas de referência em 0%, 25%, 50%, 75% e 100% do máximo.
        for (i in 0..4) {
            val gy = top + chartHeight * i / 4f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        series.forEach { (color, values) ->
            val path = Path()
            values.forEachIndexed { i, v ->
                val point = Offset(stepX * i, y(v))
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            values.forEachIndexed { i, v -> drawCircle(color, radius = 4.dp.toPx(), center = Offset(stepX * i, y(v))) }
        }
    }
}

@Composable
private fun MonthAxis(months: List<YearMonth>) {
    // Com muitos meses, mostra um rótulo sim, outro não para não sobrepor.
    val every = if (months.size > 6) 2 else 1
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(16.dp)) {
        months.forEachIndexed { i, m ->
            if (i % every == 0 || i == months.lastIndex) {
                val bias = if (months.size > 1) -1f + 2f * i / (months.size - 1) else 0f
                Text(
                    m.compactLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(BiasAlignment(bias, 0f)),
                )
            }
        }
    }
}

@Composable
private fun CategoryComparisonCard(category: Category, months: List<YearMonth>, values: List<Long>, onOpen: () -> Unit) {
    val total = values.sum()
    val max = values.max().coerceAtLeast(1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(category.color), CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Total ${formatMoney(total)} • média ${formatMoney(total / values.size)}/mês",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Ver categoria")
            }
            Spacer(Modifier.height(8.dp))
            months.indices.reversed().forEach { i ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(months[i].compactLabel(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                    Box(Modifier.weight(1f).height(10.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(values[i].toFloat() / max)
                                .height(10.dp)
                                .background(Color(category.color), RoundedCornerShape(5.dp)),
                        )
                    }
                    Text(
                        formatMoney(values[i]),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp).width(92.dp),
                        textAlign = TextAlign.End,
                    )
                    val change = if (i > 0) monthOverMonthChange(values[i - 1], values[i]) else null
                    Text(
                        when {
                            change == null -> ""
                            change > 0 -> "▲$change%"
                            change < 0 -> "▼${-change}%"
                            else -> "="
                        },
                        style = MaterialTheme.typography.labelSmall,
                        // Para despesas, aumento é ruim (vermelho) e queda é boa (verde).
                        color = when {
                            change == null || change == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            change > 0 -> ExpenseColor
                            else -> IncomeColor
                        },
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(52.dp),
                    )
                }
            }
        }
    }
}
