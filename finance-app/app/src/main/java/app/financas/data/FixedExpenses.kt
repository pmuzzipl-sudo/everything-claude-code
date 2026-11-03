package app.financas.data

import java.time.YearMonth

/** Quantos meses à frente as recorrências sem data de fim já ficam lançadas. */
const val GENERATE_AHEAD_MONTHS = 12L

fun YearMonth.toEpochMonth(): Int = year * 12 + monthValue - 1

fun epochMonthToYearMonth(epochMonth: Int): YearMonth = YearMonth.of(epochMonth / 12, epochMonth % 12 + 1)

val FixedExpense.startMonth: YearMonth get() = epochMonthToYearMonth(startEpochMonth)

/** Último mês; nulo quando não tem data de fim. */
val FixedExpense.endMonth: YearMonth? get() = months?.let { startMonth.plusMonths(it - 1L) }

/** Até onde as recorrências sem fim devem estar lançadas, a partir de [today]. */
fun generationHorizon(today: YearMonth): YearMonth = today.plusMonths(GENERATE_AHEAD_MONTHS)

/** Divide o total em parcelas; os centavos que sobram vão para as primeiras parcelas. */
fun splitInstallments(totalCents: Long, installments: Int): List<Long> {
    require(installments > 0)
    val base = totalCents / installments
    val remainder = totalCents % installments
    return List(installments) { i -> base + if (i < remainder) 1 else 0 }
}

/** Id fixo da parcela [number]: os dois aparelhos geram o mesmo id e nada se duplica. */
fun installmentId(fixedExpenseId: Long, number: Int): Long = mixId(fixedExpenseId, number.toLong())

/** Valor no mês informado (0 fora do período). */
fun FixedExpense.amountIn(month: YearMonth): Long {
    val index = month.toEpochMonth() - startEpochMonth
    if (index < 0 || (months != null && index >= months)) return 0
    return when (mode) {
        FixedMode.INSTALLMENTS -> splitInstallments(amountCents, months ?: 1)[index]
        FixedMode.RECURRING -> amountCents
    }
}

/** Valor de cada parcela (parcelado) ou de cada mês (recorrente). */
fun FixedExpense.firstAmount(): Long = amountIn(startMonth)

/**
 * Gera os lançamentos das parcelas [fromNumber] em diante. Recorrências sem fim vão até [until].
 * [previousPaid] preserva o "pago" de parcelas que já existiam (ao editar); as novas vêm marcadas como
 * pagas só se a data já passou.
 */
fun FixedExpense.generateInstallments(
    until: YearMonth,
    todayEpochDay: Long,
    previousPaid: Map<Int, Boolean> = emptyMap(),
    fromNumber: Int = 1,
): List<Transaction> {
    val count = months ?: (until.toEpochMonth() - startEpochMonth + 1).coerceAtLeast(0)
    if (fromNumber > count) return emptyList()
    return (fromNumber..count).map { number ->
        val month = startMonth.plusMonths(number - 1L)
        val epochDay = month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth())).toEpochDay()
        Transaction(
            id = installmentId(id, number),
            type = type,
            amountCents = amountIn(month),
            description = description,
            category = category,
            epochDay = epochDay,
            fixedExpenseId = id,
            installment = number,
            installmentCount = months,
            paid = previousPaid[number] ?: (epochDay < todayEpochDay),
        )
    }
}

/** Quantas parcelas vencem até o mês informado (inclusive). */
fun FixedExpense.installmentsUntil(month: YearMonth): Int {
    val elapsed = (month.toEpochMonth() - startEpochMonth + 1).coerceAtLeast(0)
    return months?.let { elapsed.coerceAtMost(it) } ?: elapsed
}

/** Totais das despesas e receitas fixas previstos para cada mês. */
data class MonthCommitment(val month: YearMonth, val incomeCents: Long, val expenseCents: Long)

fun fixedCommitments(fixed: List<FixedExpense>, months: List<YearMonth>): List<MonthCommitment> =
    months.map { m ->
        MonthCommitment(
            month = m,
            incomeCents = fixed.filter { it.type == TransactionType.INCOME }.sumOf { it.amountIn(m) },
            expenseCents = fixed.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountIn(m) },
        )
    }
