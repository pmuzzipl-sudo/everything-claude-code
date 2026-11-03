package app.financas.invest

import kotlin.math.roundToLong

/** Um ativo da carteira com sua posição e a cotação mais recente (se houver). */
data class Holding(val asset: Asset, val position: Position, val quote: Quote?) {
    val ticker get() = asset.ticker

    /** Valor de mercado; sem cotação, usa o custo (resultado zero) para não distorcer o total. */
    val valueCents: Long get() = quote?.let { (position.quantity * it.price * 100).roundToLong() } ?: position.costCents
    val resultCents: Long get() = valueCents - position.costCents
    val resultRatio: Double get() = if (position.costCents == 0L) 0.0 else resultCents.toDouble() / position.costCents

    /** Quanto o valor mudou hoje, pela variação do dia da cotação. */
    val dayChangeCents: Long
        get() {
            val pct = quote?.changePercent ?: return 0
            return (valueCents - valueCents / (1 + pct / 100)).roundToLong()
        }
}

data class PortfolioSummary(
    /** Ativos com cotas em carteira, do maior para o menor valor. */
    val holdings: List<Holding>,
    /** Ativos que já foram totalmente vendidos (aparecem só pelo histórico e lucro realizado). */
    val closed: List<Holding>,
) {
    val valueCents get() = holdings.sumOf { it.valueCents }
    val investedCents get() = holdings.sumOf { it.position.costCents }
    val resultCents get() = valueCents - investedCents
    val resultRatio get() = if (investedCents == 0L) 0.0 else resultCents.toDouble() / investedCents
    val dayChangeCents get() = holdings.sumOf { it.dayChangeCents }
    val realizedCents get() = (holdings + closed).sumOf { it.position.realizedCents }

    /** Valor por classe (renda fixa, variável, cripto), do maior para o menor. */
    val allocation: List<Pair<AssetClass, Long>>
        get() = holdings.groupBy { it.asset.assetClass }
            .map { (c, list) -> c to list.sumOf { it.valueCents } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
}

fun buildPortfolio(assets: List<Asset>, operations: List<AssetOperation>, quotes: Map<String, Quote>): PortfolioSummary {
    val byTicker = assets.associateBy { it.ticker }
    val tickers = (assets.map { it.ticker } + operations.map { it.ticker }).toSet()
    val all = tickers.map { ticker ->
        Holding(byTicker[ticker] ?: Asset(ticker, AssetClass.OTHER), computePosition(ticker, operations), quotes[ticker])
    }
    return PortfolioSummary(
        holdings = all.filter { it.position.quantity > 0 }.sortedByDescending { it.valueCents },
        closed = all.filter { it.position.quantity == 0L }.sortedBy { it.ticker },
    )
}
