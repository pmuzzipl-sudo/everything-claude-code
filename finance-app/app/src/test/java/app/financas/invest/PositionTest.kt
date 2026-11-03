package app.financas.invest

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionTest {
    private var nextId = 1L
    private fun op(kind: OperationKind, qty: Long, priceCents: Long, day: Long, fees: Long = 0, ticker: String = "IVVB11") =
        AssetOperation(id = nextId++, ticker = ticker, kind = kind, quantity = qty, amountCents = qty * priceCents, feesCents = fees, epochDay = day)

    @Test
    fun buyUpdatesAveragePrice() {
        // Exemplo combinado: 100 a R$ 50 + 50 a R$ 56 = 150 a R$ 52.
        val ops = listOf(op(OperationKind.INITIAL, 100, 5000, 1), op(OperationKind.BUY, 50, 5600, 2))
        val p = computePosition("IVVB11", ops)
        assertEquals(150, p.quantity)
        assertEquals(52.0, p.averagePrice, 1e-9)
    }

    @Test
    fun feesEnterTheCost() {
        val p = computePosition("IVVB11", listOf(op(OperationKind.BUY, 10, 1000, 1, fees = 100)))
        assertEquals(10100, p.costCents)
        assertEquals(10.10, p.averagePrice, 1e-9)
    }

    @Test
    fun sellKeepsAveragePriceAndRealizesProfit() {
        val ops = listOf(
            op(OperationKind.INITIAL, 100, 5000, 1),
            op(OperationKind.BUY, 50, 5600, 2),
            op(OperationKind.SELL, 30, 6000, 3, fees = 500),
        )
        val steps = computeSteps("IVVB11", ops)
        val after = steps.last().after
        assertEquals(120, after.quantity)
        assertEquals(52.0, after.averagePrice, 1e-9)
        // (30 × 60 - 5) - 30 × 52 = 1795 - 1560 = 235.
        assertEquals(23500, steps.last().realizedCents)
        assertEquals(23500, after.realizedCents)
    }

    @Test
    fun sellingEverythingZerosPosition() {
        val ops = listOf(op(OperationKind.BUY, 3, 1000, 1), op(OperationKind.SELL, 3, 900, 2))
        val p = computePosition("IVVB11", ops)
        assertEquals(0, p.quantity)
        assertEquals(0, p.costCents)
        assertEquals(-300, p.realizedCents)
    }

    @Test
    fun ordersByDateThenKindAndIgnoresOtherTickers() {
        val ops = listOf(
            op(OperationKind.SELL, 10, 2000, 5),
            op(OperationKind.BUY, 10, 1000, 5),
            op(OperationKind.BUY, 99, 1, 1, ticker = "BOVA11"),
        )
        val steps = computeSteps("IVVB11", ops)
        assertEquals(listOf(OperationKind.BUY, OperationKind.SELL), steps.map { it.operation.kind })
        assertEquals(10000, steps.last().realizedCents)
    }

    @Test
    fun portfolioTotalsAndAllocation() {
        val ops = listOf(
            op(OperationKind.INITIAL, 10, 10000, 1, ticker = "IVVB11"),
            op(OperationKind.INITIAL, 100, 1000, 1, ticker = "IMAB11"),
            op(OperationKind.INITIAL, 5, 2000, 1, ticker = "HASH11"),
            op(OperationKind.SELL, 5, 3000, 2, ticker = "HASH11"),
        )
        val assets = listOf(Asset("IVVB11", AssetClass.EQUITY), Asset("IMAB11", AssetClass.FIXED_INCOME), Asset("HASH11", AssetClass.CRYPTO))
        val quotes = mapOf(
            "IVVB11" to Quote("IVVB11", null, 110.0, 1.0, 0),
            "IMAB11" to Quote("IMAB11", null, 9.5, null, 0),
        )
        val p = buildPortfolio(assets, ops, quotes)
        assertEquals(listOf("IVVB11", "IMAB11"), p.holdings.map { it.ticker })
        assertEquals(listOf("HASH11"), p.closed.map { it.ticker })
        assertEquals(110000 + 95000, p.valueCents)
        assertEquals(100000 + 100000, p.investedCents)
        assertEquals(5000, p.resultCents)
        assertEquals(5000, p.realizedCents)
        assertEquals(listOf(AssetClass.EQUITY to 110000L, AssetClass.FIXED_INCOME to 95000L), p.allocation)
        // 1% de alta hoje sobre R$ 1.100 => valor de ontem 1089,11 => +10,89.
        assertEquals(1089, p.holdings.first().dayChangeCents)
    }

    @Test
    fun withoutQuoteValueIsCost() {
        val p = buildPortfolio(emptyList(), listOf(op(OperationKind.BUY, 2, 1000, 1, ticker = "XYZ11")), emptyMap())
        assertEquals(2000, p.valueCents)
        assertEquals(AssetClass.OTHER, p.holdings.single().asset.assetClass)
    }

    @Test
    fun normalizesTicker() {
        assertEquals("IVVB11", normalizeTicker(" ivvb11 "))
        assertEquals("HASH11", normalizeTicker("hash-11"))
    }
}
