package app.financas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.financas.ui.FinanceUiState
import app.financas.ui.MonthTotals
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.formatMoney
import app.financas.util.shortLabel

@Composable
fun ChartsScreen(state: FinanceUiState, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        MonthSelector(state.month, onPreviousMonth, onNextMonth)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Despesas por categoria", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                val total = state.expensesByCategory.sumOf { it.second }
                if (total == 0L) {
                    Text("Sem despesas neste mês.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = state.expensesByCategory.map { (c, v) -> Color(c.color) to v.toFloat() },
                            modifier = Modifier.size(200.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total", style = MaterialTheme.typography.labelMedium)
                            Text(formatMoney(total), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    state.expensesByCategory.forEach { (category, value) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(12.dp).background(Color(category.color), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(category.label, Modifier.weight(1f))
                            Text(
                                "${value * 100 / total}%",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(formatMoney(value), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Últimos 6 meses", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(IncomeColor, "Receitas")
                    Spacer(Modifier.width(16.dp))
                    Legend(ExpenseColor, "Despesas")
                }
                Spacer(Modifier.height(16.dp))
                HistoryBars(state.history)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DonutChart(slices: List<Pair<Color, Float>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat()
    Canvas(modifier) {
        val stroke = size.minDimension * 0.18f
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        var start = -90f
        slices.forEach { (color, value) ->
            val sweep = 360f * value / total
            drawArc(color, start, sweep, useCenter = false, topLeft = topLeft, size = Size(diameter, diameter), style = Stroke(stroke))
            start += sweep
        }
    }
}

@Composable
private fun HistoryBars(history: List<MonthTotals>) {
    val max = history.maxOfOrNull { maxOf(it.incomeCents, it.expenseCents) }?.takeIf { it > 0 } ?: 1L
    Row(
        Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        history.forEach { m ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Bar(IncomeColor, m.incomeCents.toFloat() / max)
                    Spacer(Modifier.width(2.dp))
                    Bar(ExpenseColor, m.expenseCents.toFloat() / max)
                }
                Spacer(Modifier.height(4.dp))
                Text(m.month.shortLabel(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Bar(color: Color, fraction: Float) {
    Box(
        Modifier
            .width(14.dp)
            .fillMaxHeight(fraction.coerceIn(0.01f, 1f))
            .background(color, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
    )
}
