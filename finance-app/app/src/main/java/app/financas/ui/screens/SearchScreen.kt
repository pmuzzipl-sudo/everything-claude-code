@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.financas.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.financas.data.Category
import app.financas.data.Transaction
import app.financas.data.TransactionType
import app.financas.ui.SearchPeriod
import app.financas.ui.SearchQuery
import app.financas.ui.searchTransactions
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.formatMoney
import app.financas.util.parseMoney
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun SearchScreen(
    transactions: Flow<List<Transaction>>,
    onOpen: (Transaction) -> Unit,
    onTogglePaid: (Transaction) -> Unit,
    onBack: () -> Unit,
) {
    val all by transactions.collectAsStateWithLifecycle(initialValue = null)
    var text by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<TransactionType?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(SearchPeriod.ALL) }
    val today = LocalDate.now().toEpochDay()

    val results = all?.let {
        searchTransactions(
            it,
            SearchQuery(text, type, category, parseMoney(min), parseMoney(max), period),
            today,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar lançamentos") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Descrição ou categoria (ex.: uber)") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpar") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(null to "Todos", TransactionType.EXPENSE to "Despesas", TransactionType.INCOME to "Receitas")
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = type == value,
                                onClick = {
                                    type = value
                                    if (category != null && value != null && category?.type != value) category = null
                                },
                                label = { Text(label) },
                            )
                        }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchPeriod.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
                    }
                }
                CategoryFilter(type, category, onSelect = { category = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = min,
                        onValueChange = { min = it },
                        label = { Text("Valor mín.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = max,
                        onValueChange = { max = it },
                        label = { Text("Valor máx.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (results == null) {
                item { Text("Carregando…", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item {
                    val income = results.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
                    val expense = results.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
                    Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                        Text(
                            if (results.size == 1) "1 resultado" else "${results.size} resultados",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row {
                            if (expense > 0) Text("Despesas ${formatMoney(expense)}", color = ExpenseColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 16.dp))
                            if (income > 0) Text("Receitas ${formatMoney(income)}", color = IncomeColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                items(results, key = { it.id }) { t ->
                    TransactionRow(t, onClick = { onOpen(t) }, onTogglePaid = { onTogglePaid(t) }, showYear = true)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CategoryFilter(type: TransactionType?, selected: Category?, onSelect: (Category?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (type == null) Category.entries else Category.of(type)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = selected?.label ?: "Todas as categorias",
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoria") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todas as categorias") }, onClick = { onSelect(null); expanded = false })
            options.forEach { c ->
                DropdownMenuItem(text = { Text(c.label) }, onClick = { onSelect(c); expanded = false })
            }
        }
    }
}
