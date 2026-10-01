package app.financas.data

import java.time.YearMonth

fun YearMonth.toEpochMonth(): Int = year * 12 + monthValue - 1

fun epochMonthToYearMonth(epochMonth: Int): YearMonth = YearMonth.of(epochMonth / 12, epochMonth % 12 + 1)

val FixedExpense.startMonth: YearMonth get() = epochMonthToYearMonth(startEpochMonth)
val FixedExpense.endMonth: YearMonth get() = startMonth.plusMonths(installments - 1L)

/** Divide o total em parcelas; os centavos que sobram vão para as primeiras parcelas. */
fun splitInstallments(totalCents: Long, installments: Int): List<Long> {
    require(installments > 0)
    val base = totalCents / installments
    val remainder = totalCents % installments
    return List(installments) { i -> base + if (i < remainder) 1 else 0 }
}

/** Gera um lançamento de despesa por mês, a partir do mês de início. */
fun FixedExpense.toInstallments(): List<Transaction> =
    splitInstallments(totalCents, installments).mapIndexed { i, amount ->
        val month = startMonth.plusMonths(i.toLong())
        Transaction(
            type = TransactionType.EXPENSE,
            amountCents = amount,
            description = description,
            category = category,
            epochDay = month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth())).toEpochDay(),
            fixedExpenseId = id,
            installment = i + 1,
            installmentCount = installments,
        )
    }

/** Quantas parcelas vencem até o mês informado (inclusive). */
fun FixedExpense.installmentsUntil(month: YearMonth): Int =
    (month.toEpochMonth() - startEpochMonth + 1).coerceIn(0, installments)
