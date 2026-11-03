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

/** "Out/26" */
fun YearMonth.compactLabel(): String = "${shortLabel()}/${year % 100}"

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR)

/** "05/11" */
val SHORT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", PT_BR)

/**
 * Número decimal digitado ("52,3456", "1.234,5", "10"). Diferente de [parseMoney], mantém mais de duas casas
 * (preço médio de corretora costuma ter quatro). Retorna null se inválido ou não positivo.
 */
fun parseDecimal(input: String): BigDecimal? {
    var s = input.trim().replace("R$", "").replace(" ", "")
    if (s.isEmpty()) return null
    s = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
    return s.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
}

private val priceFormat: NumberFormat = NumberFormat.getCurrencyInstance(PT_BR).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 4
}

/** Preço unitário em reais com até 4 casas: 52.3456 -> "R$ 52,3456"; 52.5 -> "R$ 52,50". */
fun formatPrice(reais: Double): String = priceFormat.format(reais)

/** 0.1234 -> "+12,34%" */
fun formatPercent(ratio: Double): String =
    (if (ratio > 0) "+" else "") + String.format(PT_BR, "%.2f%%", ratio * 100)
