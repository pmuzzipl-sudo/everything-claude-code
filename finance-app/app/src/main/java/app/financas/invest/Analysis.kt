package app.financas.invest

import java.time.LocalDate
import kotlin.math.roundToLong

enum class AnalysisPeriod(val label: String, val months: Long) {
    SIX_MONTHS("6 meses", 6),
    ONE_YEAR("1 ano", 12),
    FIVE_YEARS("5 anos", 60),
    TEN_YEARS("10 anos", 120),
}

/** Um ponto do gráfico de acompanhamento: valor de mercado e custo investido. */
data class SeriesPoint(val epochDay: Long, val valueCents: Long, val costCents: Long)

/**
 * Pontos do período para a carteira inteira ([ticker] nulo) ou para um ativo. Para um ativo, usa só os dias
 * em que ele foi registrado.
 */
fun analysisSeries(history: List<PortfolioPoint>, ticker: String?, period: AnalysisPeriod, today: LocalDate): List<SeriesPoint> {
    val from = today.minusMonths(period.months).toEpochDay()
    return history.filter { it.epochDay >= from }.sortedBy { it.epochDay }.mapNotNull { p ->
        if (ticker == null) SeriesPoint(p.epochDay, p.valueCents, p.investedCents)
        else p.assets[ticker]?.let { SeriesPoint(p.epochDay, it.valueCents, it.costCents) }
    }
}

/** Valor e custo de cada ativo em carteira, para gravar no ponto do dia. */
fun assetPoints(portfolio: PortfolioSummary): Map<String, AssetPoint> =
    portfolio.holdings.associate { it.ticker to AssetPoint(it.valueCents, it.position.costCents) }

/**
 * Reconstrói a evolução a partir dos preços de fechamento ([closes] por ativo: dia → preço) e das operações.
 * Em cada dia, quantidade e custo vêm das operações até aquele dia; a posição inicial vale desde o começo
 * (ela representa o que o usuário já tinha antes de usar o app). Dias sem preço repetem o último fechamento.
 * Ignora os dias de [skipDays] (já registrados de verdade).
 */
fun reconstructHistory(
    operations: List<AssetOperation>,
    closes: Map<String, List<Pair<Long, Double>>>,
    skipDays: Set<Long>,
): List<PortfolioPoint> {
    val days = closes.values.flatten().map { it.first }.toSortedSet()
    val effective = operations.map { if (it.kind == OperationKind.INITIAL) it.copy(epochDay = Long.MIN_VALUE / 2) else it }
    val lastClose = mutableMapOf<String, Double>()
    val byDay = closes.mapValues { (_, list) -> list.toMap() }
    return days.mapNotNull { day ->
        byDay.forEach { (ticker, prices) -> prices[day]?.let { lastClose[ticker] = it } }
        if (day in skipDays) return@mapNotNull null
        val assets = closes.keys.mapNotNull { ticker ->
            val price = lastClose[ticker] ?: return@mapNotNull null
            val position = computePosition(ticker, effective.filter { it.epochDay <= day })
            if (position.quantity == 0L) null
            else ticker to AssetPoint((position.quantity * price * 100).roundToLong(), position.costCents)
        }.toMap()
        if (assets.isEmpty()) null
        else PortfolioPoint(day, assets.values.sumOf { it.valueCents }, assets.values.sumOf { it.costCents }, assets)
    }
}
