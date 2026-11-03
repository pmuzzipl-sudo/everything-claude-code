@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.financas.ui.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.financas.invest.AssetClass
import app.financas.invest.AssetOperation
import app.financas.invest.InvestUiState
import app.financas.invest.OperationKind
import app.financas.invest.computePosition
import app.financas.invest.computeSteps
import app.financas.invest.normalizeTicker
import app.financas.util.DATE_FORMAT
import app.financas.util.centsToInput
import app.financas.util.formatMoney
import app.financas.util.formatPrice
import app.financas.util.parseDecimal
import app.financas.util.parseMoney
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun EditOperationScreen(
    id: Long?,
    presetTicker: String?,
    state: InvestUiState,
    onSave: (AssetOperation, AssetClass?) -> Unit,
    onDelete: (AssetOperation) -> Unit,
    onBack: () -> Unit,
) {
    val original = remember(id) { id?.let { i -> state.operations.firstOrNull { it.id == i } } }
    var ticker by rememberSaveable { mutableStateOf(original?.ticker ?: presetTicker.orEmpty()) }
    var kind by rememberSaveable {
        mutableStateOf(original?.kind ?: if (presetTicker == null) OperationKind.INITIAL else OperationKind.BUY)
    }
    var quantity by rememberSaveable { mutableStateOf(original?.quantity?.toString().orEmpty()) }
    var price by rememberSaveable {
        mutableStateOf(original?.let { BigDecimal(it.unitPrice).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',') }.orEmpty())
    }
    var fees by rememberSaveable { mutableStateOf(original?.feesCents?.takeIf { it > 0 }?.let(::centsToInput).orEmpty()) }
    var epochDay by rememberSaveable { mutableStateOf(original?.epochDay ?: LocalDate.now().toEpochDay()) }
    var assetClass by rememberSaveable { mutableStateOf<AssetClass?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }

    val symbol = normalizeTicker(ticker)
    val existing = state.assets.firstOrNull { it.ticker == symbol }
    val qty = quantity.toLongOrNull()?.takeIf { it > 0 }
    val unitPrice = parseDecimal(price)
    val feesCents = if (fees.isBlank()) 0L else parseMoney(fees)
    val amountCents = if (qty != null && unitPrice != null) {
        unitPrice.multiply(BigDecimal(qty)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    } else null
    // Posição sem esta operação (ao editar), para validar vendas e mostrar a prévia.
    val otherOps = state.operations.filter { it.id != original?.id }
    val before = computePosition(symbol, otherOps.filter { it.epochDay <= epochDay })
    val sellTooBig = kind == OperationKind.SELL && qty != null && qty > before.quantity
    val needsClass = existing == null && symbol.isNotEmpty()
    val valid = symbol.length >= 4 && qty != null && amountCents != null && feesCents != null && !sellTooBig &&
        (!needsClass || assetClass != null)

    val draft = if (valid) {
        AssetOperation(
            id = original?.id ?: 0,
            ticker = symbol,
            kind = kind,
            quantity = qty!!,
            amountCents = amountCents!!,
            feesCents = if (kind == OperationKind.INITIAL) 0 else feesCents!!,
            epochDay = epochDay,
        )
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Nova operação" else "Editar operação") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = ticker,
                onValueChange = { ticker = it.uppercase().take(12) },
                label = { Text("Código do ativo (ticker)") },
                placeholder = { Text("Ex.: IVVB11") },
                singleLine = true,
                enabled = original == null && presetTicker == null,
                isError = triedSave && symbol.length < 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )

            if (needsClass) {
                Column {
                    Text("Classe do ativo", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssetClass.entries.forEach { c ->
                            FilterChip(selected = assetClass == c, onClick = { assetClass = c }, label = { Text(c.label) })
                        }
                    }
                    if (triedSave && assetClass == null) {
                        Text("Escolha a classe", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                OperationKind.entries.forEachIndexed { index, k ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(index, OperationKind.entries.size),
                    ) { Text(if (k == OperationKind.INITIAL) "Inicial" else k.label) }
                }
            }
            if (kind == OperationKind.INITIAL) {
                Text(
                    "Use para cadastrar o que você já tem: quantidade atual e o preço médio informado pela corretora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter(Char::isDigit).take(9) },
                label = { Text("Quantidade de cotas") },
                singleLine = true,
                isError = (triedSave && qty == null) || sellTooBig,
                supportingText = if (sellTooBig) { { Text("Você tem ${before.quantity} cotas nessa data") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text(if (kind == OperationKind.INITIAL) "Preço médio (R$)" else "Preço por cota (R$)") },
                placeholder = { Text("0,00") },
                singleLine = true,
                isError = (triedSave || price.isNotBlank()) && unitPrice == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind != OperationKind.INITIAL) {
                OutlinedTextField(
                    value = fees,
                    onValueChange = { fees = it },
                    label = { Text("Taxas (R$, opcional)") },
                    singleLine = true,
                    isError = fees.isNotBlank() && feesCents == null,
                    supportingText = { Text("Corretagem e emolumentos; entram no custo (compra) ou saem do valor (venda)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box {
                OutlinedTextField(
                    value = LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data") },
                    trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier.matchParentSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        showDatePicker = true
                    },
                )
            }

            if (draft != null) Preview(draft, otherOps)

            Button(
                onClick = {
                    triedSave = true
                    if (draft != null) onSave(draft, if (needsClass) assetClass else null)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar") }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(pickerState) }
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir operação?") },
            text = { Text("O preço médio e a quantidade serão recalculados sem ela.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(original) }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Mostra como fica a posição depois de salvar: novo preço médio ou resultado da venda. */
@Composable
private fun Preview(draft: AssetOperation, otherOps: List<AssetOperation>) {
    val step = computeSteps(draft.ticker, otherOps + draft).first { it.operation === draft }
    val after = step.after
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Total da operação: ${formatMoney(draft.amountCents)}", style = MaterialTheme.typography.bodyMedium)
            if (draft.kind == OperationKind.SELL) {
                Text(
                    "Resultado da venda: ${formatMoney(step.realizedCents)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = resultColor(step.realizedCents),
                )
                Text("O preço médio das cotas restantes não muda.", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (after.quantity > 0) "Depois desta operação: ${after.quantity} cotas, preço médio ${formatPrice(after.averagePrice)}"
                else "Depois desta operação: posição zerada",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
