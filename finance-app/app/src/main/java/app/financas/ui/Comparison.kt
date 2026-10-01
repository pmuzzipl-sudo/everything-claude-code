package app.financas.ui

import app.financas.data.Category
import app.financas.data.Transaction
import app.financas.data.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/** Filtro do comparativo por tipo de despesa. */
enum class ExpenseKind(val label: String, val matches: (Transaction) -> Boolean) {
    ALL("Todas", { true }),
    FIXED("Fixas", { it.isFixed }),
    VARIABLE("Variáveis", { !it.isFixed }),
}

/** Os [count] meses terminando em [end], do mais antigo ao mais recente. */
fun comparisonMonths(end: YearMonth, count: Int): List<YearMonth> =
    (count - 1 downTo 0).map { end.minusMonths(it.toLong()) }

/**
 * Total de despesas por categoria em cada mês, alinhado com [months]. Inclui só categorias com algum gasto
 * no período, ordenadas da que mais gastou para a que menos gastou.
 */
fun expenseTotalsByCategory(
    transactions: List<Transaction>,
    months: List<YearMonth>,
    kind: ExpenseKind,
): List<Pair<Category, List<Long>>> {
    val index = months.withIndex().associate { (i, m) -> m to i }
    val totals = mutableMapOf<Category, LongArray>()
    transactions
        .filter { it.type == TransactionType.EXPENSE && kind.matches(it) }
        .forEach { t ->
            val i = index[YearMonth.from(LocalDate.ofEpochDay(t.epochDay))] ?: return@forEach
            totals.getOrPut(t.category) { LongArray(months.size) }[i] += t.amountCents
        }
    return totals.map { (c, values) -> c to values.toList() }.sortedByDescending { (_, v) -> v.sum() }
}

/** Variação percentual em relação ao mês anterior; null quando não há base de comparação. */
fun monthOverMonthChange(previous: Long, current: Long): Int? =
    if (previous == 0L) null else (((current - previous) * 100.0) / previous).let { Math.round(it).toInt() }
