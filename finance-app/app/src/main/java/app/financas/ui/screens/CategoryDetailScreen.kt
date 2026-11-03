@file:OptIn(ExperimentalMaterial3Api::class)

package app.financas.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.financas.data.Category
import app.financas.data.Transaction
import app.financas.data.TransactionType
import app.financas.ui.FinanceUiState
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.DATE_FORMAT
import app.financas.util.DescriptionGroup
import app.financas.util.formatMoney
import app.financas.util.groupBySimilarDescription
import java.time.LocalDate

@Composable
fun CategoryDetailScreen(
    category: Category,
    state: FinanceUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpen: (Transaction) -> Unit,
    onBack: () -> Unit,
) {
    val items = state.transactions.filter { it.category == category }
    val total = items.sumOf { it.amountCents }
    val typeTotal = state.transactions.filter { it.type == category.type }.sumOf { it.amountCents }
    val groups = groupBySimilarDescription(items) { it.description }
        .sortedByDescending { group -> group.items.sumOf { it.amountCents } }
    val color = if (category.type == TransactionType.INCOME) IncomeColor else ExpenseColor
    val budget = state.budgets.firstOrNull { it.category == category }?.limitCents

    // Grupos abertos, identificados pelo nome; sobrevive à rotação da tela.
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item { MonthSelector(state.month, onPreviousMonth, onNextMonth) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (category.type == TransactionType.INCOME) "Recebido no mês" else "Gasto no mês",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            formatMoney(total),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        Text(
                            buildString {
                                append(if (items.size == 1) "1 lançamento" else "${items.size} lançamentos")
                                if (typeTotal > 0) {
                                    append(" • ${total * 100 / typeTotal}% das ")
                                    append(if (category.type == TransactionType.INCOME) "receitas" else "despesas")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (budget != null) {
                            Text(
                                "Orçamento: ${formatMoney(total)} de ${formatMoney(budget)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (total > budget) ExpenseColor else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        "Nenhum lançamento nesta categoria neste mês.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "Agrupado por descrição",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    Text(
                        "Descrições parecidas são somadas juntas. Toque em um grupo para ver os lançamentos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(groups) { group ->
                    val isOpen = group.label in expanded
                    GroupCard(
                        group = group,
                        categoryTotal = total,
                        color = Color(category.color),
                        amountColor = color,
                        expanded = isOpen,
                        onToggle = { expanded = if (isOpen) expanded - group.label else expanded + group.label },
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: DescriptionGroup<Transaction>,
    categoryTotal: Long,
    color: Color,
    amountColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (Transaction) -> Unit,
) {
    val sum = group.items.sumOf { it.amountCents }
    val share = if (categoryTotal > 0) sum.toFloat() / categoryTotal else 0f
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.clickable(onClick = onToggle).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(if (group.items.size == 1) "1 lançamento" else "${group.items.size} lançamentos")
                            if (group.items.size > 1) append(" • média ${formatMoney(sum / group.items.size)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(sum), fontWeight = FontWeight.SemiBold, color = amountColor)
                    Text(
                        "${(share * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Recolher" else "Expandir",
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { share }, color = color, modifier = Modifier.fillMaxWidth())
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                group.items.sortedByDescending { it.epochDay }.forEach { t ->
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(t) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                t.description.ifBlank { "Sem descrição" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                LocalDate.ofEpochDay(t.epochDay).format(DATE_FORMAT),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatMoney(t.amountCents), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
