package app.financas.invest

fun Asset.toCloud(): Map<String, Any?> = mapOf("assetClass" to assetClass.name)

fun assetFromCloud(ticker: String, data: Map<String, Any?>): Asset? = runCatching {
    Asset(ticker, (data["assetClass"] as? String)?.let(AssetClass::valueOf) ?: AssetClass.OTHER)
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

fun PortfolioPoint.toCloud(): Map<String, Any?> = mapOf("valueCents" to valueCents, "investedCents" to investedCents)

fun pointFromCloud(epochDay: Long, data: Map<String, Any?>): PortfolioPoint? = runCatching {
    PortfolioPoint(epochDay, (data["valueCents"] as Number).toLong(), (data["investedCents"] as Number).toLong())
}.getOrNull()
