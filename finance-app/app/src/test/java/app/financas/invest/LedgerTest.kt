package app.financas.invest

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerTest {
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    private val ops = listOf(
        AssetOperation(1, "IVVB11", OperationKind.INITIAL, 100, 5_000_00, 0, day(9, 1)),
        AssetOperation(2, "IVVB11", OperationKind.BUY, 50, 2_800_00, 1_00, day(10, 5)),
        AssetOperation(3, "IMAB11", OperationKind.BUY, 10, 1_000_00, 0, day(10, 20)),
        AssetOperation(4, "IVVB11", OperationKind.SELL, 30, 1_800_00, 5_00, day(11, 2)),
    )

    @Test
    fun groupsByMonthNewestFirstWithTotals() {
        val months = ledger(ops)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9)), months.map { it.month })
        val october = months[1]
        assertEquals(listOf(3L, 2L), october.entries.map { it.operation.id })
        assertEquals(3_801_00, october.boughtCents)
        val november = months[0]
        assertEquals(1_795_00, november.soldCents)
        // PM após a compra de out: (5.000 + 2.801) / 150 = 52,0066...; custo vendido 30/150 × 7.801 = 1.560,20.
        assertEquals(1_795_00 - 1_560_20, november.realizedCents)
    }

    @Test
    fun filtersKeepPositionComputedWithAllOperations() {
        val sells = ledger(ops, kind = OperationKind.SELL)
        assertEquals(1, sells.size)
        assertEquals(120, sells.single().entries.single().after.quantity)
        val imab = ledger(ops, ticker = "IMAB11")
        assertEquals(listOf(3L), imab.flatMap { m -> m.entries.map { it.operation.id } })
    }
}
