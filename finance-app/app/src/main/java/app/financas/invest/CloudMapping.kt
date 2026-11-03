package app.financas.invest

fun Asset.toCloud(): Map<String, Any?> =
    mapOf("assetClass" to assetClass.name, "targetBps" to targetBps, "frozen" to frozen)

fun assetFromCloud(ticker: String, data: Map<String, Any?>): Asset? = runCatching {
    Asset(
        ticker = ticker,
        assetClass = (data["assetClass"] as? String)?.let(AssetClass::valueOf) ?: AssetClass.OTHER,
        targetBps = (data["targetBps"] as? Number)?.toInt(),
        frozen = data["frozen"] as? Boolean ?: false,
    )
}.getOrNull()

fun AssetOperation.toCloud(): Map<String, Any?> = mapOf(
    "ticker" to ticker,
    "kind" to kind.name,
    "quantity" to quantity,
    "amountCents" to amountCents,
    "feesCents" to feesCents,
    "epochDay" to epochDay,
)

fun operationFromCloud(id: Long, data: Map<String, Any?>): AssetOperation? = runCatching {
    AssetOperation(
        id = id,
        ticker = data["ticker"] as String,
        kind = OperationKind.valueOf(data["kind"] as String),
        quantity = (data["quantity"] as Number).toLong(),
        amountCents = (data["amountCents"] as Number).toLong(),
        feesCents = (data["feesCents"] as? Number)?.toLong() ?: 0,
        epochDay = (data["epochDay"] as Number).toLong(),
    )
}.getOrNull()

fun PortfolioPoint.toCloud(): Map<String, Any?> = mapOf(
    "valueCents" to valueCents,
    "investedCents" to investedCents,
    "assets" to assets.mapValues { (_, p) -> mapOf("v" to p.valueCents, "c" to p.costCents) },
)

fun pointFromCloud(epochDay: Long, data: Map<String, Any?>): PortfolioPoint? = runCatching {
    val assets = (data["assets"] as? Map<*, *>).orEmpty().mapNotNull { (ticker, value) ->
        val m = value as? Map<*, *> ?: return@mapNotNull null
        val v = (m["v"] as? Number)?.toLong() ?: return@mapNotNull null
        val c = (m["c"] as? Number)?.toLong() ?: return@mapNotNull null
        (ticker as? String ?: return@mapNotNull null) to AssetPoint(v, c)
    }.toMap()
    PortfolioPoint(epochDay, (data["valueCents"] as Number).toLong(), (data["investedCents"] as Number).toLong(), assets)
}.getOrNull()
