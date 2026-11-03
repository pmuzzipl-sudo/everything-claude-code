package app.financas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.financas.data.Transaction
import app.financas.data.TransactionType
import app.financas.ui.theme.ExpenseColor
import app.financas.ui.theme.IncomeColor
import app.financas.util.DATE_FORMAT
import app.financas.util.SHORT_DATE_FORMAT
import app.financas.util.formatMoney
import java.time.LocalDate
import app.financas.util.label
import java.time.YearMonth

@Composable
fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior")
        }
        Text(month.label(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês")
        }
    }
}

/** Linha de lançamento com o botão de pago/recebido. [showYear] mostra a data completa (busca). */
@Composable
fun TransactionRow(t: Transaction, onClick: () -> Unit, onTogglePaid: () -> Unit, showYear: Boolean = false) {
    val income = t.type == TransactionType.INCOME
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(Color(t.category.color), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                t.description.ifBlank { t.category.label },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(t.category.label)
                    when {
                        t.installment != null && t.installmentCount != null -> append(" • parcela ${t.installment}/${t.installmentCount}")
                        t.isFixed -> append(" • mensal")
                    }
                    val date = LocalDate.ofEpochDay(t.epochDay)
                    append(" • ${if (showYear) date.format(DATE_FORMAT) else date.format(SHORT_DATE_FORMAT)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (income) "+ " else "- ") + formatMoney(t.amountCents),
            color = if (income) IncomeColor else ExpenseColor,
            fontWeight = FontWeight.SemiBold,
        )
        PaidButton(t, onToggle = onTogglePaid)
    }
}

/** Marca como pago (despesa) ou recebido (receita). */
@Composable
fun PaidButton(t: Transaction, onToggle: () -> Unit) {
    val verb = if (t.type == TransactionType.INCOME) "recebido" else "pago"
    IconButton(onClick = onToggle) {
        if (t.paid) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Marcado como $verb; tocar para desmarcar", tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Outlined.Circle, contentDescription = "Marcar como $verb", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
