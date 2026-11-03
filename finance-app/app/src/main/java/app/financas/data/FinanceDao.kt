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
    fun transactionsBetween(from: Long, to: Long): Flow<List<LocalTransaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun transaction(id: Long): LocalTransaction?

    @Upsert
    suspend fun upsert(transaction: LocalTransaction)

    @Delete
    suspend fun delete(transaction: LocalTransaction)

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
    fun fixedExpenses(): Flow<List<LocalFixedExpense>>

    @Query("SELECT * FROM fixed_expenses WHERE id = :id")
    suspend fun fixedExpense(id: Long): LocalFixedExpense?

    @Insert
    suspend fun insert(fixedExpense: LocalFixedExpense): Long

    @Update
    suspend fun update(fixedExpense: LocalFixedExpense)

    @Query("DELETE FROM fixed_expenses WHERE id = :id")
    suspend fun deleteFixedExpense(id: Long)

    @Insert
    suspend fun insertAll(transactions: List<LocalTransaction>)

    @Query("DELETE FROM transactions WHERE fixedExpenseId = :fixedExpenseId")
    suspend fun deleteInstallments(fixedExpenseId: Long)

    // Leitura completa usada só para enviar os dados antigos (anteriores à sincronização) para a nuvem.
    @Query("SELECT * FROM transactions")
    suspend fun allTransactions(): List<LocalTransaction>

    @Query("SELECT * FROM budgets")
    suspend fun allBudgets(): List<Budget>

    @Query("SELECT * FROM fixed_expenses")
    suspend fun allFixedExpenses(): List<LocalFixedExpense>
}
