@file:OptIn(ExperimentalMaterial3Api::class)

package app.financas.ui.invest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.financas.invest.FULL_BPS
import app.financas.invest.InvestUiState
import app.financas.invest.suggestContribution
import app.financas.invest.targetsSummary
import app.financas.ui.theme.ExpenseColor
import app.financas.util.formatMoney
import app.financas.util.formatPrice
import app.financas.util.parseDecimal
import app.financas.util.parseMoney
import java.math.BigDecimal
import java.math.RoundingMode

/** 2500 -> "25"; 3333 -> "33,33" */
private fun bpsToInput(bps: Int): String =
    BigDecimal(bps).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',')

/** "25" -> 2500; vazio ou zero -> null (sem meta). */
private fun inputToBps(text: String): Int? =
    parseDecimal(text)?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toInt()?.takeIf { it > 0 }

/** "25,5%" */
fun formatBps(bps: Int): String = bpsToInput(bps) + "%"

@Composable
fun TargetsScreen(state: InvestUiState, onSave: (Map<String, Int?>) -> Unit, onBack: () -> Unit) {
    val summary = targetsSummary(state.portfolio)
    // Ativos em carteira e os cadastrados sem cotas (podem receber meta para começar a comprar).
    val tickers = (state.portfolio.holdings.map { it.ticker } + state.assets.map { it.ticker }).distinct()
    val savedBps = state.assets.associate { it.ticker to it.targetBps }
    val inputs = remember(tickers) {
        mutableStateMapOf<String, String>().apply { tickers.forEach { put(it, savedBps[it]?.let(::bpsToInput).orEmpty()) } }
    }
    var contribution by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    val edited = tickers.associateWith { inputToBps(inputs[it].orEmpty()) }
    val invalid = tickers.filter { inputs[it].orEmpty().isNotBlank() && parseDecimal(inputs[it].orEmpty()) == null }
    val sumBps = edited.values.sumOf { it ?: 0 }
    val changed = edited.any { (t, bps) -> bps != savedBps[t] }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Metas e aporte") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Text(
                    "Defina quanto (%) de toda a carteira cada ativo deve representar. A soma deve dar 100%. " +
                        "Deixe em branco os ativos sem meta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(tickers, key = { it }) { ticker ->
                val row = summary.rows.firstOrNull { it.ticker == ticker }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val color = row?.holding?.asset?.assetClass?.color ?: state.assets.firstOrNull { it.ticker == ticker }?.assetClass?.color
                    Box(Modifier.size(10.dp).background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline, CircleShape))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(ticker, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Atual ${formatRatio(row?.currentRatio ?: 0.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = inputs[ticker].orEmpty(),
                        onValueChange = { inputs[ticker] = it.take(6); saved = false },
                        suffix = { Text("%") },
                        singleLine = true,
                        isError = ticker in invalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(110.dp),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Total das metas: ${formatBps(sumBps)}",
                        fontWeight = FontWeight.SemiBold,
                        color = if (sumBps == FULL_BPS || sumBps == 0) MaterialTheme.colorScheme.onSurface else ExpenseColor,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onSave(edited); saved = true },
                        enabled = changed && invalid.isEmpty(),
                    ) { Text(if (saved && !changed) "Salvo" else "Salvar metas") }
                }
                if (sumBps != FULL_BPS && sumBps != 0) {
                    Text(
                        if (sumBps < FULL_BPS) "Faltam ${formatBps(FULL_BPS - sumBps)} para 100%."
                        else "Passou ${formatBps(sumBps - FULL_BPS)} de 100%.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseColor,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Simular aporte", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Digite quanto vai investir: o app divide entre os ativos mais abaixo da meta, sem precisar vender nada. " +
                        "Usa as metas salvas e a cotação atual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = contribution,
                    onValueChange = { contribution = it },
                    label = { Text("Valor do aporte (R$)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            val amount = parseMoney(contribution)
            if (amount != null) {
                val suggestions = suggestContribution(summary, amount)
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            if (suggestions.isEmpty()) {
                                Text("Salve metas para ver a sugestão.", style = MaterialTheme.typography.bodyMedium)
                            }
                            suggestions.forEach { s ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(s.ticker, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatMoney(s.amountCents))
                                        if (s.quantity != null && s.unitPrice != null) {
                                            Text(
                                                "≈ ${s.quantity} cotas a ${formatPrice(s.unitPrice)}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                            if (suggestions.isNotEmpty()) {
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    "Cotas arredondadas para baixo; o valor que sobrar fica para o próximo aporte.",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 0.3214 -> "32,1%" */
fun formatRatio(ratio: Double): String =
    BigDecimal(ratio * 100).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%"
