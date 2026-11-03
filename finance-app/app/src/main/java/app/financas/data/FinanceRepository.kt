package app.financas.data

import kotlinx.coroutines.flow.Flow

/** Acesso aos dados do usuário. Gravações aparecem nos fluxos na hora, mesmo sem internet. */
interface FinanceRepository {
    fun transactionsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<Transaction>>
    /** Todos os lançamentos (usado pela busca). */
    fun allTransactions(): Flow<List<Transaction>>
    /** Leitura única de um período (usada pelo lembrete diário, fora do app). */
    suspend fun transactionsOnce(fromEpochDay: Long, toEpochDay: Long): List<Transaction>
    suspend fun transaction(id: Long): Transaction?
    fun save(transaction: Transaction)
    fun delete(transaction: Transaction)
    suspend fun expenseTotal(category: Category, fromEpochDay: Long, toEpochDay: Long): Long

    fun budgets(): Flow<List<Budget>>
    suspend fun budget(category: Category): Budget?
    fun setBudget(category: Category, limitCents: Long?)

    fun fixedExpenses(): Flow<List<FixedExpense>>
    suspend fun fixedExpense(id: Long): FixedExpense?
    /** Salva a despesa/receita fixa e (re)cria seus lançamentos, mantendo o "pago" dos que já existiam. */
    suspend fun saveFixedExpense(fixed: FixedExpense)
    /** Exclui a fixa e os lançamentos ainda não pagos; os pagos ficam no histórico. */
    suspend fun deleteFixedExpense(fixed: FixedExpense)
    /** Lança os próximos meses de uma recorrência sem data de fim. */
    fun extendRecurring(fixed: FixedExpense)

    /** Envia dados antigos deste aparelho (já com ids da nuvem). */
    fun importLegacy(snapshot: LegacySnapshot)
}
