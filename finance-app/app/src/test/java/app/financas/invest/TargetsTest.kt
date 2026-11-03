package app.financas.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetsTest {
    private fun initial(ticker: String, qty: Long, priceCents: Long) =
        AssetOperation(id = ticker.hashCode().toLong(), ticker = ticker, kind = OperationKind.INITIAL, quantity = qty, amountCents = qty * priceCents, epochDay = 1)

    // Carteira de R$ 10.000: IVVB11 R$ 5.000 (50%), IMAB11 R$ 3.000 (30%), HASH11 R$ 2.000 (20%).
    private val ops = listOf(initial("IVVB11", 50, 10000), initial("IMAB11", 300, 1000), initial("HASH11", 40, 5000))
    private val quotes = mapOf(
        "IVVB11" to Quote("IVVB11", null, 100.0, null, 0),
        "IMAB11" to Quote("IMAB11", null, 10.0, null, 0),
        "HASH11" to Quote("HASH11", null, 50.0, null, 0),
    )

    private fun summary(vararg targets: Pair<String, Int?>): TargetsSummary {
        val assets = targets.map { (t, bps) -> Asset(t, AssetClass.EQUITY, bps) }
        return targetsSummary(buildPortfolio(assets, ops, quotes))
    }

    @Test
    fun currentShareDiffAndAdjustment() {
        val s = summary("IVVB11" to 4000, "IMAB11" to 4000, "HASH11" to 2000)
        assertTrue(s.isComplete)
        assertEquals(10_000_00, s.totalCents)
        val byTicker = s.rows.associateBy { it.ticker }
        assertEquals(0.5, byTicker.getValue("IVVB11").currentRatio, 1e-9)
        assertEquals(0.1, byTicker.getValue("IVVB11").diffRatio!!, 1e-9)
        // IVVB11 está R$ 1.000 acima; IMAB11 precisa de R$ 1.000; HASH11 na meta.
        assertEquals(-1_000_00L, byTicker.getValue("IVVB11").adjustCents(s.totalCents))
        assertEquals(1_000_00L, byTicker.getValue("IMAB11").adjustCents(s.totalCents))
        assertEquals(0L, byTicker.getValue("HASH11").adjustCents(s.totalCents))
        // Mais abaixo da meta primeiro.
        assertEquals(listOf("IMAB11", "HASH11", "IVVB11"), s.rows.map { it.ticker })
    }

    @Test
    fun incompleteTargetsAndMissingOnes() {
        val s = summary("IVVB11" to 5000, "IMAB11" to null)
        assertEquals(5000, s.targetSumBps)
        assertFalse(s.isComplete)
        assertNull(s.rows.first { it.ticker == "IMAB11" }.adjustCents(s.totalCents))
        // Sem meta vai para o fim da lista.
        assertNull(s.rows.last().targetRatio)
    }

    @Test
    fun contributionGoesToAssetsBelowTarget() {
        // Aporte de R$ 2.000 → novo total R$ 12.000. Metas 40/40/20:
        // IVVB11 precisa 4.800 - 5.000 < 0 → 0; IMAB11 4.800 - 3.000 = 1.800; HASH11 2.400 - 2.000 = 400.
        // Necessidade 2.200 > 2.000 → proporcional: IMAB11 1.636,36; HASH11 363,64.
        val s = summary("IVVB11" to 4000, "IMAB11" to 4000, "HASH11" to 2000)
        val suggestion = suggestContribution(s, 2_000_00)
        assertEquals(listOf("IMAB11", "HASH11"), suggestion.map { it.ticker })
        assertEquals(1_636_36L, suggestion[0].amountCents)
        assertEquals(363_64L, suggestion[1].amountCents)
        assertEquals(163L, suggestion[0].quantity)
        assertEquals(7L, suggestion[1].quantity)
    }

    @Test
    fun leftoverIsSplitByTarget() {
        // Aporte de R$ 10.000 → novo total R$ 20.000; necessidades: IVVB11 3.000, IMAB11 5.000, HASH11 2.000 = 10.000.
        val s = summary("IVVB11" to 4000, "IMAB11" to 4000, "HASH11" to 2000)
        val suggestion = suggestContribution(s, 10_000_00).associate { it.ticker to it.amountCents }
        assertEquals(mapOf("IMAB11" to 5_000_00L, "IVVB11" to 3_000_00L, "HASH11" to 2_000_00L), suggestion)
        // Com R$ 20.000, sobram 10.000 divididos 40/40/20.
        val more = suggestContribution(s, 20_000_00).associate { it.ticker to it.amountCents }
        assertEquals(mapOf("IMAB11" to 9_000_00L, "IVVB11" to 7_000_00L, "HASH11" to 4_000_00L), more)
    }

    @Test
    fun frozenAssetHasNoTargetButCountsInTheBase() {
        // IVVB11 congelado (R$ 5.000 = 50% de R$ 10.000). Base das metas = carteira inteira.
        val assets = listOf(
            Asset("IVVB11", AssetClass.EQUITY, targetBps = 5000, frozen = true),
            Asset("IMAB11", AssetClass.FIXED_INCOME, targetBps = 3000),
            Asset("HASH11", AssetClass.CRYPTO, targetBps = 2000),
        )
        val s = targetsSummary(buildPortfolio(assets, ops, quotes))
        assertEquals(10_000_00, s.totalCents)
        assertEquals(listOf("IVVB11"), s.frozen.map { it.ticker })
        assertEquals(5_000_00, s.frozenCents)
        assertEquals(5000, s.frozenBps)
        assertEquals(setOf("IMAB11", "HASH11"), s.rows.map { it.ticker }.toSet())
        // Meta do congelado é ignorada: 30% + 20% (+ 50% congelado).
        assertEquals(5000, s.targetSumBps)
        assertFalse(s.isOverAllocated)
        assertEquals(0.3, s.rows.first { it.ticker == "IMAB11" }.currentRatio, 1e-9)
        assertEquals(0L, s.rows.first { it.ticker == "IMAB11" }.adjustCents(s.totalCents))
        // Aporte de R$ 1.000 → total R$ 11.000: IMAB11 precisa 3.300 - 3.000 = 300; HASH11 2.200 - 2.000 = 200.
        // Sobram 500 (fatia que seria do congelado), divididos 30/20 entre os ativos com meta.
        val suggestion = suggestContribution(s, 1_000_00).associate { it.ticker to it.amountCents }
        assertEquals(mapOf("IMAB11" to 600_00L, "HASH11" to 400_00L), suggestion)
    }

    @Test
    fun noTargetsNoSuggestion() {
        assertTrue(suggestContribution(summary("IVVB11" to null), 1_000_00).isEmpty())
        assertTrue(suggestContribution(summary("IVVB11" to 10000), 0).isEmpty())
    }
}
