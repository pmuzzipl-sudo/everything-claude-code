@file:OptIn(ExperimentalMaterial3Api::class)

package app.financas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.financas.data.Category
import app.financas.data.FixedExpense
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

private const val MAX_INSTALLMENTS = 360

@Composable
fun EditFixedExpenseScreen(
    id: Long?,
    load: suspend (Long) -> FixedExpense?,
    onSave: (FixedExpense) -> Unit,
    onDelete: (FixedExpense) -> Unit,
    onBack: () -> Unit,
) {
    var original by remember { mutableStateOf<FixedExpense?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.HOUSING) }
    var total by rememberSaveable { mutableStateOf("") }
    var installments by rememberSaveable { mutableStateOf("1") }
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
            description = f.description
            category = f.category
            total = centsToInput(f.totalCents)
            installments = f.installments.toString()
            startEpochMonth = f.startEpochMonth
            day = f.dayOfMonth.toString()
            loaded = true
        }
    }

    val totalCents = parseMoney(total)
    val count = installments.toIntOrNull()?.takeIf { it in 1..MAX_INSTALLMENTS }
    val dayOfMonth = day.toIntOrNull()?.takeIf { it in 1..31 }
    val start = epochMonthToYearMonth(startEpochMonth)
    val showErrors = triedSave

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "Nova despesa fixa" else "Editar despesa fixa") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição") },
                placeholder = { Text("Ex.: Aluguel, Financiamento do carro") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryPicker(TransactionType.EXPENSE, category, onSelect = { category = it })

            OutlinedTextField(
                value = total,
                onValueChange = { total = it },
                label = { Text("Valor total (R$)") },
                placeholder = { Text("0,00") },
                singleLine = true,
                isError = (showErrors || total.isNotBlank()) && totalCents == null,
                supportingText = if ((showErrors || total.isNotBlank()) && totalCents == null) {
                    { Text("Informe um valor maior que zero") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = installments,
                onValueChange = { installments = it.filter(Char::isDigit).take(3) },
                label = { Text("Número de parcelas") },
                singleLine = true,
                isError = count == null,
                supportingText = if (count == null) {
                    { Text("Entre 1 e $MAX_INSTALLMENTS") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("Mês de início", style = MaterialTheme.typography.labelLarge)
                MonthSelector(
                    month = start,
                    onPrevious = { startEpochMonth -= 1 },
                    onNext = { startEpochMonth += 1 },
                )
            }

            OutlinedTextField(
                value = day,
                onValueChange = { day = it.filter(Char::isDigit).take(2) },
                label = { Text("Dia do vencimento") },
                singleLine = true,
                isError = dayOfMonth == null,
                supportingText = {
                    Text(if (dayOfMonth == null) "Entre 1 e 31" else "Em meses mais curtos, usa o último dia")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            if (totalCents != null && count != null) {
                val parcels = splitInstallments(totalCents, count)
                val end = start.plusMonths(count - 1L)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (parcels.first() == parcels.last()) "${count}x de ${formatMoney(parcels.first())}"
                            else "1ª de ${formatMoney(parcels.first())} e demais de ${formatMoney(parcels.last())}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (count == 1) start.label() else "${start.label()} a ${end.label()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (original != null) {
                            Text(
                                "Ao salvar, todas as parcelas são recriadas com os novos valores.",
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
                    if (totalCents != null && count != null && dayOfMonth != null) {
                        onSave(
                            FixedExpense(
                                id = original?.id ?: 0,
                                description = description.trim(),
                                category = category,
                                totalCents = totalCents,
                                installments = count,
                                startEpochMonth = startEpochMonth,
                                dayOfMonth = dayOfMonth,
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
            title = { Text("Excluir despesa fixa?") },
            text = { Text("Todas as parcelas lançadas nos meses também serão excluídas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    original?.let(onDelete)
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
