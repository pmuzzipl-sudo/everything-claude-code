package app.financas.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BrapiParseTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun parsesQuoteWithHistory() {
        val day1 = LocalDate.of(2026, 10, 1).atStartOfDay(zone).plusHours(13).toEpochSecond()
        val day2 = LocalDate.of(2026, 10, 2).atStartOfDay(zone).plusHours(13).toEpochSecond()
        val body = """
            {"results":[{"symbol":"IVVB11","shortName":"ISHARES SP500","longName":null,
              "regularMarketPrice":321.45,"regularMarketChangePercent":-0.85,
              "historicalDataPrice":[
                {"date":$day2,"close":320.1},
                {"date":$day1,"close":318.5},
                {"date":$day1,"close":null}
              ]}]}
        """.trimIndent()
        val q = parseQuote(body, 99, zone)!!
        assertEquals("IVVB11", q.symbol)
        assertEquals("ISHARES SP500", q.name)
        assertEquals(321.45, q.price, 1e-9)
        assertEquals(-0.85, q.changePercent!!, 1e-9)
        assertEquals(99, q.fetchedAtMillis)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 1).toEpochDay() to 318.5, LocalDate.of(2026, 10, 2).toEpochDay() to 320.1),
            q.history,
        )
    }

    @Test
    fun missingPriceOrInvalidBodyIsNull() {
        assertNull(parseQuote("""{"results":[{"symbol":"XYZ11","regularMarketPrice":null}]}""", 0, zone))
        assertNull(parseQuote("""{"error":true,"message":"not found"}""", 0, zone))
        assertNull(parseQuote("not json", 0, zone))
    }

    @Test
    fun nullChangePercent() {
        val q = parseQuote("""{"results":[{"symbol":"A","regularMarketPrice":10,"regularMarketChangePercent":null}]}""", 0, zone)!!
        assertNull(q.changePercent)
        assertNull(q.name)
    }
}
