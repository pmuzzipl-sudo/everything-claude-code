package app.financas.data

import kotlinx.coroutines.flow.Flow

/** Acesso aos dados do usuário. Gravações aparecem nos fluxos na hora, mesmo sem internet. */
interface FinanceRepository {
    fun transactionsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<Transaction>>
    suspend fun transaction(id: Long): Transaction?
    fun save(transaction: Transaction)
    fun delete(transaction: Transaction)
    suspend fun expenseTotal(category: Category, fromEpochDay: Long, toEpochDay: Long): Long

    fun budgets(): Flow<List<Budget>>
    suspend fun budget(category: Category): Budget?
    fun setBudget(category: Category, limitCents: Long?)

    fun fixedExpenses(): Flow<List<FixedExpense>>
    suspend fun fixedExpense(id: Long): FixedExpense?
    /** Salva a despesa fixa e (re)cria todas as suas parcelas. */
    suspend fun saveFixedExpense(fixed: FixedExpense)
    /** Exclui a despesa fixa e todas as suas parcelas. */
    suspend fun deleteFixedExpense(fixed: FixedExpense)

    /** Envia dados antigos deste aparelho (já com ids da nuvem). */
    fun importLegacy(snapshot: LegacySnapshot)
}
