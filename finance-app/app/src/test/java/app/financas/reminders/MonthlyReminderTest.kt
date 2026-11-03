package app.financas.reminders

import app.financas.data.toEpochMonth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlyReminderTest {
    private val december = YearMonth.of(2026, 12).toEpochMonth()
    private val november = YearMonth.of(2026, 11).toEpochMonth()

    @Test
    fun sendsOnFirstDaysOnceAMonth() {
        assertTrue(shouldSendMonthlyReminder(LocalDate.of(2026, 12, 1), lastSentEpochMonth = november))
        // Aparelho desligado no dia 1º: ainda envia até o dia 3.
        assertTrue(shouldSendMonthlyReminder(LocalDate.of(2026, 12, 3), lastSentEpochMonth = november))
        assertFalse(shouldSendMonthlyReminder(LocalDate.of(2026, 12, 4), lastSentEpochMonth = november))
        // Já enviado neste mês.
        assertFalse(shouldSendMonthlyReminder(LocalDate.of(2026, 12, 2), lastSentEpochMonth = december))
        // Nunca enviado.
        assertTrue(shouldSendMonthlyReminder(LocalDate.of(2027, 1, 1), lastSentEpochMonth = -1))
    }
}
