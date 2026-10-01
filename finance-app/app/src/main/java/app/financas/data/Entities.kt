package app.financas.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Valores em centavos para evitar erros de arredondamento. Data em epoch day (LocalDate). */
@Entity(tableName = "transactions", indices = [Index("epochDay")])
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    val description: String,
    val category: Category,
    val epochDay: Long,
)

/** Limite mensal de gastos para uma categoria de despesa. */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: Category,
    val limitCents: Long,
)
