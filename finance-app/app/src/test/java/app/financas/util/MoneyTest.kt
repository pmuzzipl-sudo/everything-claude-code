package app.financas.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesBrazilianFormats() {
        assertEquals(1200L, parseMoney("12"))
        assertEquals(1250L, parseMoney("12,5"))
        assertEquals(123456L, parseMoney("1.234,56"))
        assertEquals(123456L, parseMoney("R$ 1.234,56"))
        assertEquals(123456L, parseMoney("1234.56"))
        assertEquals(1L, parseMoney("0,005"))
    }

    @Test
    fun rejectsInvalidOrNonPositive() {
        assertNull(parseMoney(""))
        assertNull(parseMoney("abc"))
        assertNull(parseMoney("0"))
        assertNull(parseMoney("-5"))
    }

    @Test
    fun inputRoundTrip() {
        assertEquals("1234,56", centsToInput(123456))
        assertEquals(123456L, parseMoney(centsToInput(123456)))
    }
}
