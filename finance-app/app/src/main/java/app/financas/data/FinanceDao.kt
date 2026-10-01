package app.financas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, id DESC")
    fun transactionsBetween(from: Long, to: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: Long): Transaction?

    @Upsert
    suspend fun upsert(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM transactions " +
            "WHERE type = 'EXPENSE' AND category = :category AND epochDay BETWEEN :from AND :to"
    )
    suspend fun expenseTotal(category: Category, from: Long, to: Long): Long

    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE category = :category")
    suspend fun budget(category: Category): Budget?

    @Upsert
    suspend fun upsert(budget: Budget)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: Category)

    @Query("SELECT * FROM fixed_expenses ORDER BY startEpochMonth DESC, id DESC")
    fun fixedExpenses(): Flow<List<FixedExpense>>

    @Query("SELECT * FROM fixed_expenses WHERE id = :id")
    suspend fun fixedExpense(id: Long): FixedExpense?

    @Insert
    suspend fun insert(fixedExpense: FixedExpense): Long

    @Update
    suspend fun update(fixedExpense: FixedExpense)

    @Query("DELETE FROM fixed_expenses WHERE id = :id")
    suspend fun deleteFixedExpense(id: Long)

    @Insert
    suspend fun insertAll(transactions: List<Transaction>)

    @Query("DELETE FROM transactions WHERE fixedExpenseId = :fixedExpenseId")
    suspend fun deleteInstallments(fixedExpenseId: Long)
}
