package app.financas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Valores em centavos para evitar erros de arredondamento. Data em epoch day (LocalDate).
 * Parcelas de despesas fixas guardam a despesa de origem e o número da parcela; lançamentos avulsos
 * (despesas variáveis e receitas) deixam esses campos nulos.
 */
@Entity(tableName = "transactions", indices = [Index("epochDay")])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    val description: String,
    val category: Category,
    val epochDay: Long,
    val fixedExpenseId: Long? = null,
    val installment: Int? = null,
    val installmentCount: Int? = null,
) {
    val isFixed get() = fixedExpenseId != null
}

/** Limite mensal de gastos para uma categoria de despesa. */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: Category,
    val limitCents: Long,
)

/**
 * Despesa fixa cadastrada: o valor total é dividido em [installments] parcelas mensais a partir de
 * [startEpochMonth] (ano * 12 + mês - 1), lançadas no dia [dayOfMonth] de cada mês.
 */
@Entity(tableName = "fixed_expenses")
data class FixedExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val category: Category,
    val totalCents: Long,
    val installments: Int,
    val startEpochMonth: Int,
    val dayOfMonth: Int,
)
