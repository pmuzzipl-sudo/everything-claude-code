package app.financas.ui

import app.financas.data.Transaction
import app.financas.data.TransactionType

/** Quantos dias à frente as contas aparecem em "a vencer". */
const val DUE_AHEAD_DAYS = 7L

/** Até quantos dias atrás uma conta não paga ainda aparece como atrasada. */
const val DUE_LOOKBACK_DAYS = 90L

/** Despesas não pagas atrasadas ou que vencem até [aheadDays] dias depois de hoje, da mais antiga à mais nova. */
fun dueBills(transactions: List<Transaction>, todayEpochDay: Long, aheadDays: Long = DUE_AHEAD_DAYS): List<Transaction> =
    transactions
        .filter { it.type == TransactionType.EXPENSE && !it.paid && it.epochDay <= todayEpochDay + aheadDays }
        .sortedWith(compareBy<Transaction> { it.epochDay }.thenBy { it.id })

/** Texto curto do vencimento: "Atrasada há 3 dias", "Vence hoje", "Vence amanhã", "Vence em 5 dias". */
fun dueLabel(epochDay: Long, todayEpochDay: Long): String {
    val days = epochDay - todayEpochDay
    return when {
        days < -1 -> "Atrasada há ${-days} dias"
        days == -1L -> "Atrasada desde ontem"
        days == 0L -> "Vence hoje"
        days == 1L -> "Vence amanhã"
        else -> "Vence em $days dias"
    }
}
