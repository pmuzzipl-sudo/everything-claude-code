package app.financas.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvestMappingTest {
    @Test
    fun roundTrips() {
        val op = AssetOperation(7, "IVVB11", OperationKind.SELL, 30, 180000, 500, 20400)
        assertEquals(op, operationFromCloud(7, op.toCloud()))
        assertEquals(Asset("IVVB11", AssetClass.EQUITY), assetFromCloud("IVVB11", Asset("IVVB11", AssetClass.EQUITY).toCloud()))
        val full = Asset("HASH11", AssetClass.CRYPTO, targetBps = 1250, frozen = true)
        assertEquals(full, assetFromCloud("HASH11", full.toCloud()))
        val point = PortfolioPoint(20400, 123456, 100000)
        assertEquals(point, pointFromCloud(20400, point.toCloud()))
    }

    @Test
    fun invalidOrUnknownValues() {
        assertNull(operationFromCloud(1, mapOf("ticker" to "A", "kind" to "SWAP", "quantity" to 1L, "amountCents" to 1L, "epochDay" to 1L)))
        assertEquals(AssetClass.OTHER, assetFromCloud("A", emptyMap())?.assetClass)
    }
}
