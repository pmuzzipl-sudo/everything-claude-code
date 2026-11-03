package app.financas.invest

import kotlin.math.roundToLong

/** 100% em pontos-base. */
const val FULL_BPS = 10_000

/** Situação de um ativo em relação à sua meta de participação na carteira. */
data class TargetRow(
    val holding: Holding,
    /** Participação atual (0 a 1) pelo valor de mercado. */
    val currentRatio: Double,
    /** Meta (0 a 1), ou null se não definida. */
    val targetRatio: Double?,
) {
    val ticker get() = holding.ticker
    /** Diferença em pontos percentuais/100 (positivo = acima da meta). */
    val diffRatio: Double? get() = targetRatio?.let { currentRatio - it }

    /**
     * Quanto comprar (positivo) ou quanto está acima da meta (negativo) para chegar exatamente na meta,
     * mantendo o total atual da carteira.
     */
    fun adjustCents(totalCents: Long): Long? = targetRatio?.let { (it * totalCents).roundToLong() - holding.valueCents }
}

/**
 * [totalCents] é a base das metas: o valor total da carteira, incluindo os congelados. Os congelados ficam em
 * [frozen]: ocupam parte do total, mas não têm meta nem recebem aportes.
 */
data class TargetsSummary(val rows: List<TargetRow>, val totalCents: Long, val frozen: List<Holding> = emptyList()) {
    val frozenCents: Long get() = frozen.sumOf { it.valueCents }
    /** Participação atual dos congelados no total, em pontos-base. */
    val frozenBps: Int get() = if (totalCents == 0L) 0 else (frozenCents * FULL_BPS / totalCents).toInt()
    /** Metas passam de 100% do total. */
    val isOverAllocated get() = targetSumBps > FULL_BPS
    val targetSumBps: Int get() = rows.sumOf { row -> row.targetRatio?.let { (it * FULL_BPS).roundToLong().toInt() } ?: 0 }
    val hasTargets get() = rows.any { it.targetRatio != null }
    val isComplete get() = targetSumBps == FULL_BPS
}

/**
 * Participação atual de cada ativo e a meta, sobre o valor total da carteira (congelados incluídos). Inclui
 * ativos em carteira e ativos com meta ainda sem cotas (para mostrar que falta comprar). Ordenado do mais
 * abaixo da meta para o mais acima. Congelados não aparecem nas linhas e suas metas são ignoradas.
 */
fun targetsSummary(portfolio: PortfolioSummary): TargetsSummary {
    val (frozen, active) = portfolio.holdings.partition { it.asset.frozen }
    val total = portfolio.valueCents
    val candidates = active + portfolio.closed.filter { it.asset.targetBps != null && !it.asset.frozen }
    val rows = candidates.map { h ->
        TargetRow(
            holding = h,
            currentRatio = if (total == 0L || h.position.quantity == 0L) 0.0 else h.valueCents.toDouble() / total,
            targetRatio = h.asset.targetBps?.let { it.toDouble() / FULL_BPS },
        )
    }.sortedWith(compareBy<TargetRow> { it.diffRatio ?: Double.MAX_VALUE }.thenBy { it.ticker })
    return TargetsSummary(rows, total, frozen)
}

/** Sugestão para um aporte: valor e quantas cotas inteiras dá para comprar pela cotação atual. */
data class ContributionSuggestion(val ticker: String, val amountCents: Long, val quantity: Long?, val unitPrice: Double?)

/**
 * Divide um aporte entre os ativos abaixo da meta, sem vender nada: calcula quanto cada um precisa para
 * atingir a meta no novo total (carteira + aporte) e reparte proporcionalmente se o aporte não cobrir tudo.
 * Se sobrar dinheiro depois de todos atingirem a meta, a sobra é dividida pelas metas.
 */
fun suggestContribution(summary: TargetsSummary, amountCents: Long): List<ContributionSuggestion> {
    val withTarget = summary.rows.filter { it.targetRatio != null }
    if (amountCents <= 0 || withTarget.isEmpty()) return emptyList()
    val newTotal = summary.totalCents + amountCents
    val currentValue = { row: TargetRow -> if (row.holding.position.quantity == 0L) 0L else row.holding.valueCents }
    val needs = withTarget.associateWith { row ->
        ((row.targetRatio!! * newTotal).roundToLong() - currentValue(row)).coerceAtLeast(0)
    }
    val totalNeed = needs.values.sum()
    val targetSum = withTarget.sumOf { it.targetRatio!! }
    val shares = withTarget.associateWith { row ->
        val need = needs.getValue(row)
        when {
            totalNeed >= amountCents -> (need.toDouble() * amountCents / totalNeed).roundToLong()
            else -> need + ((amountCents - totalNeed) * row.targetRatio!! / targetSum).roundToLong()
        }
    }
    return withTarget.mapNotNull { row ->
        val cents = shares.getValue(row)
        if (cents <= 0) return@mapNotNull null
        val price = row.holding.quote?.price
        ContributionSuggestion(
            ticker = row.ticker,
            amountCents = cents,
            quantity = price?.let { (cents / 100.0 / it).toLong() },
            unitPrice = price,
        )
    }.sortedByDescending { it.amountCents }
}
