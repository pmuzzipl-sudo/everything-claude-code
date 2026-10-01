@file:OptIn(ExperimentalMaterial3Api::class)

package app.financas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import app.financas.data.Transaction
import app.financas.data.TransactionType
import app.financas.util.DATE_FORMAT
import app.financas.util.centsToInput
import app.financas.util.parseMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun EditTransactionScreen(
    id: Long?,
    load: suspend (Long) -> Transaction?,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onBack: () -> Unit,
) {
    var original by remember { mutableStateOf<Transaction?>(null) }
    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.FOOD) }
    var epochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var loaded by rememberSaveable { mutableStateOf(id == null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var triedSave by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        val t = load(id) ?: return@LaunchedEffect
        original = t
        if (!loaded) {
            type = t.type
            amount = centsToInput(t.amountCents)
            description = t.description
            category = t.category
            epochDay = t.epochDay
            loaded = true
        }
    }

    val cents = parseMoney(amount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) "Novo lançamento" else "Editar lançamento") },
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
            val installmentOf = original?.takeIf { it.isFixed }
            if (installmentOf != null) {
                Text(
                    "Parcela ${installmentOf.installment}/${installmentOf.installmentCount} de uma despesa fixa. " +
                        "Alterações aqui valem só para esta parcela; para mudar todas, edite a despesa na aba \"Fixas\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val types = listOf(TransactionType.EXPENSE to "Despesa", TransactionType.INCOME to "Receita")
            if (installmentOf == null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = type == value,
                        onClick = {
                            if (type != value) {
                                type = value
                                category = Category.of(value).first()
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    ) { Text(label) }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Valor (R$)") },
                placeholder = { Text("0,00") },
                singleLine = true,
                isError = (triedSave || amount.isNotBlank()) && cents == null,
                supportingText = if ((triedSave || amount.isNotBlank()) && cents == null) {
                    { Text("Informe um valor maior que zero") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryPicker(type, category, onSelect = { category = it })

            Box {
                OutlinedTextField(
                    value = LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data") },
                    trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Camada transparente para abrir o seletor ao tocar no campo.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showDatePicker = true },
                )
            }

            Button(
                onClick = {
                    triedSave = true
                    if (cents != null) {
                        // copy() mantém o vínculo da parcela com a despesa fixa ao editar.
                        val base = original ?: Transaction(type = type, amountCents = 0, description = "", category = category, epochDay = 0)
                        onSave(
                            base.copy(
                                type = type,
                                amountCents = cents,
                                description = description.trim(),
                                category = category,
                                epochDay = epochDay,
                            ),
                        )
                    }
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
        ) {
            DatePicker(pickerState)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir lançamento?") },
            text = { Text("Esta ação não pode ser desfeita.") },
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

@Composable
fun CategoryPicker(type: TransactionType, selected: Category, onSelect: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoria") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Category.of(type).forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.label) },
                    onClick = { onSelect(c); expanded = false },
                )
            }
        }
    }
}
