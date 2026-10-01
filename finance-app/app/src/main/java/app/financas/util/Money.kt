package app.financas.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

private val currency: NumberFormat = NumberFormat.getCurrencyInstance(PT_BR)

/** 123456 -> "R$ 1.234,56" */
fun formatMoney(cents: Long): String = currency.format(BigDecimal.valueOf(cents, 2))

/** 123456 -> "1234,56" (para edição em campo de texto). */
fun centsToInput(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

/**
 * Converte o texto digitado em centavos. Aceita "12", "12,5", "1.234,56" e "1234.56".
 * Retorna null se o valor for inválido ou não for positivo.
 */
fun parseMoney(input: String): Long? {
    var s = input.trim().replace("R$", "").replace(" ", "")
    if (s.isEmpty()) return null
    s = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
    val value = s.toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
}

fun YearMonth.label(): String {
    val month = month.getDisplayName(TextStyle.FULL, PT_BR).replaceFirstChar { it.uppercase(PT_BR) }
    return "$month $year"
}

fun YearMonth.shortLabel(): String =
    month.getDisplayName(TextStyle.SHORT, PT_BR).trimEnd('.').replaceFirstChar { it.uppercase(PT_BR) }

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)
