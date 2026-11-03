package app.financas.data

import kotlin.random.Random

/*
 * Conversão entre os modelos do app e os documentos do Firestore. Feita à mão (sem reflexão) para ser
 * testável e não depender de regras do R8. Campos desconhecidos são ignorados e registros com dados
 * inválidos (por exemplo, uma categoria criada por uma versão mais nova do app) são descartados.
 */

/** Ids aleatórios positivos: dois aparelhos podem criar registros ao mesmo tempo sem colidir. */
fun newCloudId(random: Random = Random.Default): Long = (random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1)

/**
 * Id na nuvem para um registro antigo (criado só neste aparelho, com ids 1, 2, 3...). O [salt] é sorteado
 * uma vez por aparelho: reenviar gera os mesmos ids (sem duplicar) e aparelhos diferentes não colidem.
 */
fun legacyCloudId(salt: Long, localId: Long): Long {
    // splitmix64
    var z = salt + localId * -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    z = z xor (z ushr 31)
    return (z and Long.MAX_VALUE).coerceAtLeast(1)
}

fun Transaction.toCloud(): Map<String, Any?> = mapOf(
    "type" to type.name,
    "amountCents" to amountCents,
    "description" to description,
    "category" to category.name,
    "epochDay" to epochDay,
    "fixedExpenseId" to fixedExpenseId,
    "installment" to installment,
    "installmentCount" to installmentCount,
)

fun transactionFromCloud(id: Long, data: Map<String, Any?>): Transaction? = runCatching {
    Transaction(
        id = id,
        type = TransactionType.valueOf(data["type"] as String),
        amountCents = (data["amountCents"] as Number).toLong(),
        description = data["description"] as? String ?: "",
        category = Category.valueOf(data["category"] as String),
        epochDay = (data["epochDay"] as Number).toLong(),
        fixedExpenseId = (data["fixedExpenseId"] as? Number)?.toLong(),
        installment = (data["installment"] as? Number)?.toInt(),
        installmentCount = (data["installmentCount"] as? Number)?.toInt(),
    )
}.getOrNull()

fun Budget.toCloud(): Map<String, Any?> = mapOf("limitCents" to limitCents)

fun budgetFromCloud(docId: String, data: Map<String, Any?>): Budget? = runCatching {
    Budget(Category.valueOf(docId), (data["limitCents"] as Number).toLong())
}.getOrNull()

fun FixedExpense.toCloud(): Map<String, Any?> = mapOf(
    "description" to description,
    "category" to category.name,
    "totalCents" to totalCents,
    "installments" to installments,
    "startEpochMonth" to startEpochMonth,
    "dayOfMonth" to dayOfMonth,
)

fun fixedExpenseFromCloud(id: Long, data: Map<String, Any?>): FixedExpense? = runCatching {
    FixedExpense(
        id = id,
        description = data["description"] as? String ?: "",
        category = Category.valueOf(data["category"] as String),
        totalCents = (data["totalCents"] as Number).toLong(),
        installments = (data["installments"] as Number).toInt(),
        startEpochMonth = (data["startEpochMonth"] as Number).toInt(),
        dayOfMonth = (data["dayOfMonth"] as Number).toInt(),
    )
}.getOrNull()

/** Dados antigos deste aparelho convertidos para os ids da nuvem, mantendo o vínculo das parcelas. */
data class LegacySnapshot(
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val fixedExpenses: List<FixedExpense>,
) {
    val isEmpty get() = transactions.isEmpty() && budgets.isEmpty() && fixedExpenses.isEmpty()

    fun withCloudIds(salt: Long) = LegacySnapshot(
        transactions = transactions.map { t ->
            t.copy(id = legacyCloudId(salt, t.id), fixedExpenseId = t.fixedExpenseId?.let { legacyCloudId(salt, -it) })
        },
        budgets = budgets,
        // Despesas fixas usam ids negativos na mistura para não coincidirem com os dos lançamentos.
        fixedExpenses = fixedExpenses.map { it.copy(id = legacyCloudId(salt, -it.id)) },
    )
}
