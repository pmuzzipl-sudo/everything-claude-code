package app.financas.data

import kotlin.random.Random

/*
 * Conversão entre os modelos do app e os documentos do Firestore. Feita à mão (sem reflexão) para ser
 * testável e não depender de regras do R8. Campos desconhecidos são ignorados e registros com dados
 * inválidos (por exemplo, uma categoria criada por uma versão mais nova do app) são descartados.
 */

/** Ids aleatórios positivos: dois aparelhos podem criar registros ao mesmo tempo sem colidir. */
fun newCloudId(random: Random = Random.Default): Long = (random.nextLong() and Long.MAX_VALUE).coerceAtLeast(1)

/** Mistura dois números num id positivo e bem espalhado (splitmix64); mesmo par, mesmo id. */
fun mixId(seed: Long, value: Long): Long {
    var z = seed + value * -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    z = z xor (z ushr 31)
    return (z and Long.MAX_VALUE).coerceAtLeast(1)
}

/**
 * Id na nuvem para um registro antigo (criado só neste aparelho, com ids 1, 2, 3...). O [salt] é sorteado
 * uma vez por aparelho: reenviar gera os mesmos ids (sem duplicar) e aparelhos diferentes não colidem.
 */
fun legacyCloudId(salt: Long, localId: Long): Long = mixId(salt, localId)

fun Transaction.toCloud(): Map<String, Any?> = mapOf(
    "type" to type.name,
    "amountCents" to amountCents,
    "description" to description,
    "category" to category.name,
    "epochDay" to epochDay,
    "fixedExpenseId" to fixedExpenseId,
    "installment" to installment,
    "installmentCount" to installmentCount,
    "paid" to paid,
)

/**
 * [todayEpochDay] define o "pago" de registros salvos antes desse campo existir: lançamentos avulsos
 * contam como pagos; parcelas de fixas, só se a data já passou.
 */
fun transactionFromCloud(id: Long, data: Map<String, Any?>, todayEpochDay: Long): Transaction? = runCatching {
    val epochDay = (data["epochDay"] as Number).toLong()
    val fixedExpenseId = (data["fixedExpenseId"] as? Number)?.toLong()
    Transaction(
        id = id,
        type = TransactionType.valueOf(data["type"] as String),
        amountCents = (data["amountCents"] as Number).toLong(),
        description = data["description"] as? String ?: "",
        category = Category.valueOf(data["category"] as String),
        epochDay = epochDay,
        fixedExpenseId = fixedExpenseId,
        installment = (data["installment"] as? Number)?.toInt(),
        installmentCount = (data["installmentCount"] as? Number)?.toInt(),
        paid = data["paid"] as? Boolean ?: (fixedExpenseId == null || epochDay < todayEpochDay),
    )
}.getOrNull()

fun Budget.toCloud(): Map<String, Any?> = mapOf("limitCents" to limitCents)

fun budgetFromCloud(docId: String, data: Map<String, Any?>): Budget? = runCatching {
    Budget(Category.valueOf(docId), (data["limitCents"] as Number).toLong())
}.getOrNull()

// "totalCents" e "installments" mantêm os nomes da versão 2.0 (só parcelado) para ler os dados já salvos.
fun FixedExpense.toCloud(): Map<String, Any?> = mapOf(
    "type" to type.name,
    "mode" to mode.name,
    "description" to description,
    "category" to category.name,
    "totalCents" to amountCents,
    "installments" to months,
    "startEpochMonth" to startEpochMonth,
    "dayOfMonth" to dayOfMonth,
    "generatedUntil" to generatedUntilEpochMonth,
)

fun fixedExpenseFromCloud(id: Long, data: Map<String, Any?>): FixedExpense? = runCatching {
    val mode = (data["mode"] as? String)?.let(FixedMode::valueOf) ?: FixedMode.INSTALLMENTS
    val months = (data["installments"] as? Number)?.toInt()
    require(months != null || mode == FixedMode.RECURRING) { "parcelado precisa de número de parcelas" }
    FixedExpense(
        id = id,
        type = (data["type"] as? String)?.let(TransactionType::valueOf) ?: TransactionType.EXPENSE,
        mode = mode,
        description = data["description"] as? String ?: "",
        category = Category.valueOf(data["category"] as String),
        amountCents = (data["totalCents"] as Number).toLong(),
        months = months,
        startEpochMonth = (data["startEpochMonth"] as Number).toInt(),
        dayOfMonth = (data["dayOfMonth"] as Number).toInt(),
        generatedUntilEpochMonth = (data["generatedUntil"] as? Number)?.toInt(),
    )
}.getOrNull()

/** Dados antigos deste aparelho (banco local das versões 1.x), já no formato da nuvem. */
data class LegacySnapshot(
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val fixedExpenses: List<FixedExpense>,
) {
    val isEmpty get() = transactions.isEmpty() && budgets.isEmpty() && fixedExpenses.isEmpty()

    /** Troca os ids locais (1, 2, 3...) pelos da nuvem, mantendo o vínculo das parcelas. */
    fun withCloudIds(salt: Long) = LegacySnapshot(
        transactions = transactions.map { t ->
            t.copy(id = legacyCloudId(salt, t.id), fixedExpenseId = t.fixedExpenseId?.let { legacyCloudId(salt, -it) })
        },
        budgets = budgets,
        // Despesas fixas usam ids negativos na mistura para não coincidirem com os dos lançamentos.
        fixedExpenses = fixedExpenses.map { it.copy(id = legacyCloudId(salt, -it.id)) },
    )

    companion object {
        fun fromLocal(
            transactions: List<LocalTransaction>,
            budgets: List<Budget>,
            fixedExpenses: List<LocalFixedExpense>,
            todayEpochDay: Long,
        ) = LegacySnapshot(
            transactions = transactions.map {
                Transaction(
                    id = it.id, type = it.type, amountCents = it.amountCents, description = it.description,
                    category = it.category, epochDay = it.epochDay, fixedExpenseId = it.fixedExpenseId,
                    installment = it.installment, installmentCount = it.installmentCount,
                    paid = it.fixedExpenseId == null || it.epochDay < todayEpochDay,
                )
            },
            budgets = budgets,
            fixedExpenses = fixedExpenses.map {
                FixedExpense(
                    id = it.id, description = it.description, category = it.category, amountCents = it.totalCents,
                    months = it.installments, startEpochMonth = it.startEpochMonth, dayOfMonth = it.dayOfMonth,
                )
            },
        )
    }
}
