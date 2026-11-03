@file:OptIn(ExperimentalMaterial3Api::class)

package app.financas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.financas.data.Category
import app.financas.data.FixedExpense
import app.financas.data.FixedMode
import app.financas.data.TransactionType
import app.financas.data.epochMonthToYearMonth
import app.financas.data.splitInstallments
import app.financas.data.toEpochMonth
import app.financas.util.centsToInput
import app.financas.util.formatMoney
import app.financas.util.label
import app.financas.util.parseMoney
import java.time.LocalDate
import java.time.YearMonth

private const val MAX_MONTHS = 360

@Composable
fun EditFixedExpenseScreen(
    id: Long?,
    load: suspend (Long) -> FixedExpense?,
    onSave: (FixedExpense) -> Unit,
    onDelete: (FixedExpense) -> Unit,
    onBack: () -> Unit,
) {
    var original by remember { mutableStateOf<FixedExpense?>(null) }
    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var mode by rememberSaveable { mutableStateOf(FixedMode.RECURRING) }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.HOUSING) }
    var amount by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("12") }
    var endless by rememberSaveable { mutableStateOf(true) }
    var startEpochMonth by rememberSaveable { mutableStateOf(YearMonth.now().toEpochMonth()) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        val f = load(id) ?: return@LaunchedEffect
        original = f
        if (!loaded) {
            type = f.type
            mode = f.mode
            description = f.description
            category = f.category
            amount = centsToInput(f.amountCents)
            endless = f.months == null
            months = (f.months ?: 12).toString()
            startEpochMonth = f.startEpochMonth
            day = f.dayOfMonth.toString()
            loaded = true
        }
    }

    val isEndless = mode == FixedMode.RECURRING && endless
    val amountCents = parseMoney(amount)
    val count = if (isEndless) null else months.toIntOrNull()?.takeIf { it in 1..MAX_MONTHS }
    val countValid = isEndless || count != null
    val dayOfMonth = day.toIntOrNull()?.takeIf { it in 1..31 }
    val start = epochMonthToYearMonth(startEpochMonth)
    val income = type == TransactionType.INCOME

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "Nova fixa" else "Editar fixa") },
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
            Segmented(
                options = listOf(TransactionType.EXPENSE to "Despesa", TransactionType.INCOME to "Receita"),
                selected = type,
                onSelect = { if (it != type) { type = it; category = Category.of(it).first() } },
            )
            Segmented(
                options = listOf(FixedMode.RECURRING to "Valor mensal", FixedMode.INSTALLMENTS to "Parcelado"),
                selected = mode,
                onSelect = { mode = it },
            )
            Text(
                if (mode == FixedMode.RECURRING) "Mesmo valor todo mês (aluguel, salário, assinaturas)."
                else "Um valor total dividido em parcelas mensais (compras parceladas, financiamentos).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição") },
                placeholder = { Text(if (income) "Ex.: Salário" else "Ex.: Aluguel, Internet") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryPicker(type, category, onSelect = { category = it })

            val amountError = (triedSave || amount.isNotBlank()) && amountCents == null
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(if (mode == FixedMode.RECURRING) "Valor por mês (R$)" else "Valor total (R$)") },
                placeholder = { Text("0,00") },
                singleLine = true,
                isError = amountError,
                supportingText = if (amountError) { { Text("Informe um valor maior que zero") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            if (mode == FixedMode.RECURRING) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sem data de fim", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Repete todo mês até você encerrar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = endless, onCheckedChange = { endless = it })
                }
            }
            if (!isEndless) {
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it.filter(Char::isDigit).take(3) },
                    label = { Text(if (mode == FixedMode.INSTALLMENTS) "Número de parcelas" else "Por quantos meses") },
                    singleLine = true,
                    isError = !countValid,
                    supportingText = if (!countValid) { { Text("Entre 1 e $MAX_MONTHS") } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column {
                Text("Mês de início", style = MaterialTheme.typography.labelLarge)
                MonthSelector(month = start, onPrevious = { startEpochMonth -= 1 }, onNext = { startEpochMonth += 1 })
            }

            OutlinedTextField(
                value = day,
                onValueChange = { day = it.filter(Char::isDigit).take(2) },
                label = { Text(if (income) "Dia do recebimento" else "Dia do vencimento") },
                singleLine = true,
                isError = dayOfMonth == null,
                supportingText = { Text(if (dayOfMonth == null) "Entre 1 e 31" else "Em meses mais curtos, usa o último dia") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            if (amountCents != null && countValid) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(previewTitle(mode, amountCents, count), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                count == null -> "A partir de ${start.label()}, sem data de fim"
                                count == 1 -> start.label()
                                else -> "${start.label()} a ${start.plusMonths(count - 1L).label()}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (original != null) {
                            Text(
                                "Ao salvar, os lançamentos são recriados com os novos valores (inclusive meses passados); " +
                                    "o \"pago\" de cada mês é mantido. Para mudar o valor só daqui para frente, " +
                                    "defina um fim nesta e cadastre uma nova.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    triedSave = true
                    if (amountCents != null && countValid && dayOfMonth != null) {
                        onSave(
                            FixedExpense(
                                id = original?.id ?: 0,
                                type = type,
                                mode = mode,
                                description = description.trim(),
                                category = category,
                                amountCents = amountCents,
                                months = count,
                                startEpochMonth = startEpochMonth,
                                dayOfMonth = dayOfMonth,
                                generatedUntilEpochMonth = original?.generatedUntilEpochMonth,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${if (income) "receita" else "despesa"} fixa?") },
            text = { Text("Os meses já marcados como pagos/recebidos continuam no histórico; os demais são excluídos.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; original?.let(onDelete) }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private fun previewTitle(mode: FixedMode, amountCents: Long, count: Int?): String = when {
    mode == FixedMode.RECURRING -> "${formatMoney(amountCents)} por mês" +
        (count?.let { " • total ${formatMoney(amountCents * it)}" } ?: "")
    else -> {
        val parcels = splitInstallments(amountCents, count ?: 1)
        if (parcels.first() == parcels.last()) "${parcels.size}x de ${formatMoney(parcels.first())}"
        else "1ª de ${formatMoney(parcels.first())} e demais de ${formatMoney(parcels.last())}"
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}
