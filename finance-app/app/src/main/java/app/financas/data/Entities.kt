package app.financas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tabelas do banco local (Room) das versões 1.x, anteriores à sincronização. Hoje servem só para
 * enviar esses dados antigos para a nuvem; os campos não podem mudar sem uma nova migração.
 */

@Entity(tableName = "transactions", indices = [Index("epochDay")])
data class LocalTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    val description: String,
    val category: Category,
    val epochDay: Long,
    val fixedExpenseId: Long? = null,
    val installment: Int? = null,
    val installmentCount: Int? = null,
)

/** Limite mensal de gastos para uma categoria de despesa (mesmo formato no banco local e na nuvem). */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: Category,
    val limitCents: Long,
)

@Entity(tableName = "fixed_expenses")
data class LocalFixedExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val category: Category,
    val totalCents: Long,
    val installments: Int,
    val startEpochMonth: Int,
    val dayOfMonth: Int,
)
