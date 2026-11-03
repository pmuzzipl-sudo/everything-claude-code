package app.financas.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalysisTest {
    private val today = LocalDate.of(2026, 11, 3)
    private fun d(daysAgo: Long) = today.minusDays(daysAgo).toEpochDay()

    @Test
    fun seriesForTotalAndAssetWithinPeriod() {
        val history = listOf(
            PortfolioPoint(today.minusMonths(8).toEpochDay(), 900, 900),
            PortfolioPoint(d(30), 1000, 950, mapOf("IVVB11" to AssetPoint(600, 550))),
            PortfolioPoint(d(10), 1100, 950),
            PortfolioPoint(d(0), 1200, 1000, mapOf("IVVB11" to AssetPoint(700, 600), "IMAB11" to AssetPoint(500, 400))),
        )
        val total = analysisSeries(history, null, AnalysisPeriod.SIX_MONTHS, today)
        assertEquals(listOf(1000L, 1100L, 1200L), total.map { it.valueCents })
        assertEquals(4, analysisSeries(history, null, AnalysisPeriod.ONE_YEAR, today).size)
        // Um ativo: só os dias em que ele foi registrado.
        val ivvb = analysisSeries(history, "IVVB11", AnalysisPeriod.TEN_YEARS, today)
        assertEquals(listOf(SeriesPoint(d(30), 600, 550), SeriesPoint(d(0), 700, 600)), ivvb)
    }

    @Test
    fun reconstructUsesOperationsAndCarriesLastClose() {
        val ops = listOf(
            // Posição inicial cadastrada hoje vale desde o começo do histórico.
            AssetOperation(1, "IVVB11", OperationKind.INITIAL, 10, 1_000_00, 0, d(0)),
            AssetOperation(2, "IVVB11", OperationKind.BUY, 10, 1_100_00, 0, d(5)),
            AssetOperation(3, "IMAB11", OperationKind.BUY, 100, 1_000_00, 0, d(5)),
        )
        val closes = mapOf(
            "IVVB11" to listOf(d(10) to 100.0, d(6) to 105.0, d(5) to 110.0, d(1) to 120.0),
            "IMAB11" to listOf(d(5) to 10.0),
        )
        val points = reconstructHistory(ops, closes, skipDays = setOf(d(6))).associateBy { it.epochDay }
        assertEquals(setOf(d(10), d(5), d(1)), points.keys)
        // 10 dias atrás: só a posição inicial (10 × 100).
        assertEquals(1_000_00, points.getValue(d(10)).valueCents)
        assertTrue(points.getValue(d(10)).assets.keys == setOf("IVVB11"))
        // 5 dias atrás: 20 × 110 + 100 × 10.
        assertEquals(2_200_00 + 1_000_00, points.getValue(d(5)).valueCents)
        // Ontem: IMAB11 sem preço repete o último (10,00): 20 × 120 + 1.000.
        assertEquals(2_400_00 + 1_000_00, points.getValue(d(1)).valueCents)
        assertEquals(points.getValue(d(1)).assets.values.sumOf { it.costCents }, points.getValue(d(1)).investedCents)
    }

    @Test
    fun pointMappingWithAssets() {
        val p = PortfolioPoint(20400, 1200, 1000, mapOf("IVVB11" to AssetPoint(700, 600)))
        assertEquals(p, pointFromCloud(20400, p.toCloud()))
        // Pontos antigos (sem "assets").
        assertEquals(PortfolioPoint(1, 2, 3), pointFromCloud(1, mapOf("valueCents" to 2L, "investedCents" to 3L)))
    }
}
