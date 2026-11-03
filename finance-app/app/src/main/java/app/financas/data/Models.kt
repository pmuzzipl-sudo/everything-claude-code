package app.financas.data

/**
 * Lançamento. Valores em centavos; data em epoch day (LocalDate). Lançamentos gerados por uma despesa ou
 * receita fixa guardam a origem ([fixedExpenseId]) e o número da parcela; [installmentCount] é nulo
 * quando a recorrência não tem data de fim. [paid] indica pago (despesa) ou recebido (receita).
 */
data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    val description: String,
    val category: Category,
    val epochDay: Long,
    val fixedExpenseId: Long? = null,
    val installment: Int? = null,
    val installmentCount: Int? = null,
    val paid: Boolean = true,
) {
    val isFixed get() = fixedExpenseId != null
}

enum class FixedMode {
    /** Valor total dividido em [FixedExpense.months] parcelas. */
    INSTALLMENTS,

    /** Mesmo valor todo mês, por [FixedExpense.months] meses ou sem data de fim. */
    RECURRING,
}

/**
 * Despesa ou receita fixa. [amountCents] é o valor total (parcelado) ou o valor mensal (recorrente).
 * [months] nulo = recorrente sem data de fim; nesse caso os lançamentos são criados aos poucos, até
 * [generatedUntilEpochMonth]. Mês de início em epoch month (ano * 12 + mês - 1).
 */
data class FixedExpense(
    val id: Long = 0,
    val type: TransactionType = TransactionType.EXPENSE,
    val mode: FixedMode = FixedMode.INSTALLMENTS,
    val description: String,
    val category: Category,
    val amountCents: Long,
    val months: Int?,
    val startEpochMonth: Int,
    val dayOfMonth: Int,
    val generatedUntilEpochMonth: Int? = null,
) {
    val isEndless get() = months == null
}
