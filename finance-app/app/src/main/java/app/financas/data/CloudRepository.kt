package app.financas.data

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Máximo de operações por lote do Firestore é 500; fica uma margem. */
private const val BATCH_LIMIT = 400

private typealias BatchOp = (WriteBatch) -> Unit

/**
 * Dados em users/{uid}/... no Firestore. O cache local do Firestore guarda tudo no aparelho: o app funciona
 * offline e as alterações são enviadas quando a internet voltar. As gravações não esperam o servidor
 * (sem await), senão ficariam travadas sem conexão; os fluxos recebem a alteração imediatamente.
 */
class CloudRepository(uid: String, private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) : FinanceRepository {
    private val user = db.collection("users").document(uid)
    private val transactions = user.collection("transactions")
    private val budgets = user.collection("budgets")
    private val fixedExpenses = user.collection("fixed_expenses")

    override fun transactionsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<Transaction>> =
        transactions.rangeQuery(fromEpochDay, toEpochDay).listen { snap ->
            snap.documents.mapNotNull { it.toTransaction() }
                .sortedWith(compareByDescending<Transaction> { it.epochDay }.thenByDescending { it.id })
        }

    override suspend fun transaction(id: Long): Transaction? = transactions.document(id.toString()).read()?.toTransaction()

    override fun save(transaction: Transaction) {
        val withId = if (transaction.id == 0L) transaction.copy(id = newCloudId()) else transaction
        transactions.document(withId.id.toString()).set(withId.toCloud())
    }

    override fun delete(transaction: Transaction) {
        transactions.document(transaction.id.toString()).delete()
    }

    override suspend fun expenseTotal(category: Category, fromEpochDay: Long, toEpochDay: Long): Long =
        transactions.rangeQuery(fromEpochDay, toEpochDay).read().documents
            .mapNotNull { it.toTransaction() }
            .filter { it.type == TransactionType.EXPENSE && it.category == category }
            .sumOf { it.amountCents }

    override fun budgets(): Flow<List<Budget>> =
        budgets.listen { snap -> snap.documents.mapNotNull { doc -> doc.data?.let { budgetFromCloud(doc.id, it) } } }

    override suspend fun budget(category: Category): Budget? =
        budgets.document(category.name).read()?.let { doc -> doc.data?.let { budgetFromCloud(doc.id, it) } }

    override fun setBudget(category: Category, limitCents: Long?) {
        val doc = budgets.document(category.name)
        if (limitCents == null) doc.delete() else doc.set(Budget(category, limitCents).toCloud())
    }

    override fun fixedExpenses(): Flow<List<FixedExpense>> =
        fixedExpenses.listen { snap ->
            snap.documents.mapNotNull { it.toFixedExpense() }
                .sortedWith(compareByDescending<FixedExpense> { it.startEpochMonth }.thenByDescending { it.id })
        }

    override suspend fun fixedExpense(id: Long): FixedExpense? = fixedExpenses.document(id.toString()).read()?.toFixedExpense()

    override suspend fun saveFixedExpense(fixed: FixedExpense) {
        val saved = if (fixed.id == 0L) fixed.copy(id = newCloudId()) else fixed
        val old = installmentsOf(saved.id)
        commitInBatches(buildList<BatchOp> {
            old.forEach { ref -> add { b -> b.delete(ref) } }
            add { b -> b.set(fixedExpenses.document(saved.id.toString()), saved.toCloud()) }
            saved.toInstallments().forEach { t ->
                val withId = t.copy(id = newCloudId())
                add { b -> b.set(transactions.document(withId.id.toString()), withId.toCloud()) }
            }
        })
    }

    override suspend fun deleteFixedExpense(fixed: FixedExpense) {
        val old = installmentsOf(fixed.id)
        commitInBatches(buildList<BatchOp> {
            old.forEach { ref -> add { b -> b.delete(ref) } }
            add { b -> b.delete(fixedExpenses.document(fixed.id.toString())) }
        })
    }

    override fun importLegacy(snapshot: LegacySnapshot) {
        commitInBatches(buildList<BatchOp> {
            snapshot.fixedExpenses.forEach { f -> add { b -> b.set(fixedExpenses.document(f.id.toString()), f.toCloud()) } }
            snapshot.budgets.forEach { budget -> add { b -> b.set(budgets.document(budget.category.name), budget.toCloud()) } }
            snapshot.transactions.forEach { t -> add { b -> b.set(transactions.document(t.id.toString()), t.toCloud()) } }
        })
    }

    /**
     * Parcelas de uma despesa fixa. Consulta o servidor (com cache como alternativa offline), pois parcelas
     * de meses futuros podem não estar no cache local e precisam ser apagadas ao editar.
     */
    private suspend fun installmentsOf(fixedExpenseId: Long): List<DocumentReference> =
        transactions.whereEqualTo("fixedExpenseId", fixedExpenseId).get().await().documents.map { it.reference }

    private fun commitInBatches(operations: List<BatchOp>) {
        operations.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { it(batch) }
            batch.commit()
        }
    }

    private fun CollectionReference.rangeQuery(from: Long, to: Long): Query =
        whereGreaterThanOrEqualTo("epochDay", from).whereLessThanOrEqualTo("epochDay", to)

    private fun DocumentSnapshot.toTransaction(): Transaction? =
        id.toLongOrNull()?.let { id -> data?.let { transactionFromCloud(id, it) } }

    private fun DocumentSnapshot.toFixedExpense(): FixedExpense? =
        id.toLongOrNull()?.let { id -> data?.let { fixedExpenseFromCloud(id, it) } }
}

/** Escuta a consulta em tempo real (inclui alterações feitas no outro aparelho). */
private fun <T> Query.listen(map: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snap, error ->
        // Em caso de erro (ex.: sem permissão), mantém os últimos dados exibidos.
        if (error == null && snap != null) trySend(map(snap))
    }
    awaitClose { registration.remove() }
}

/** Lê do cache local primeiro (rápido e funciona offline); se não houver, busca no servidor. */
private suspend fun Query.read(): QuerySnapshot =
    runCatching { get(Source.CACHE).await() }.getOrNull() ?: get().await()

private suspend fun DocumentReference.read(): DocumentSnapshot? {
    val cached = runCatching { get(Source.CACHE).await() }.getOrNull()
    if (cached != null && cached.exists()) return cached
    return runCatching { get().await() }.getOrNull()?.takeIf { it.exists() }
}
