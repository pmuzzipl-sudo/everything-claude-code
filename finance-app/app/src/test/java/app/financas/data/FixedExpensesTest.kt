package app.financas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FixedExpensesTest {
    private val today = LocalDate.of(2026, 12, 15).toEpochDay()

    private fun installments(total: Long, n: Int, start: YearMonth, day: Int = 10) = FixedExpense(
        id = 7, mode = FixedMode.INSTALLMENTS, description = "Notebook", category = Category.SHOPPING,
        amountCents = total, months = n, startEpochMonth = start.toEpochMonth(), dayOfMonth = day,
    )

    private fun recurring(monthly: Long, months: Int?, start: YearMonth, type: TransactionType = TransactionType.EXPENSE) =
        FixedExpense(
            id = 9, type = type, mode = FixedMode.RECURRING, description = "Aluguel", category = Category.HOUSING,
            amountCents = monthly, months = months, startEpochMonth = start.toEpochMonth(), dayOfMonth = 5,
        )

    @Test
    fun splitKeepsTotalAndPutsRemainderFirst() {
        assertEquals(listOf(3334L, 3333L, 3333L), splitInstallments(10000, 3))
        assertEquals(listOf(10000L), splitInstallments(10000, 1))
    }

    @Test
    fun epochMonthRoundTrip() {
        val m = YearMonth.of(2026, 12)
        assertEquals(m, epochMonthToYearMonth(m.toEpochMonth()))
        assertEquals(YearMonth.of(2027, 1), epochMonthToYearMonth(m.toEpochMonth() + 1))
    }

    @Test
    fun installmentsAcrossYearsWithStableIds() {
        val f = installments(120000, 3, YearMonth.of(2026, 11))
        val list = f.generateInstallments(until = YearMonth.of(2030, 1), todayEpochDay = today)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10)),
            list.map { LocalDate.ofEpochDay(it.epochDay) },
        )
        assertEquals(listOf(40000L, 40000L, 40000L), list.map { it.amountCents })
        assertEquals(listOf(1, 2, 3), list.map { it.installment })
        assertEquals(setOf(3), list.map { it.installmentCount }.toSet())
        // Mesmo id em qualquer aparelho, ids diferentes por parcela.
        assertEquals(list.map { it.id }, f.generateInstallments(YearMonth.of(2030, 1), today).map { it.id })
        assertEquals(3, list.map { it.id }.toSet().size)
        // Datas passadas vêm como pagas; futuras, pendentes.
        assertEquals(listOf(true, true, false), list.map { it.paid })
    }

    @Test
    fun keepsPreviousPaidFlags() {
        val f = installments(3000, 3, YearMonth.of(2026, 11))
        val list = f.generateInstallments(YearMonth.of(2030, 1), today, previousPaid = mapOf(1 to false, 3 to true))
        assertEquals(listOf(false, true, true), list.map { it.paid })
    }

    @Test
    fun dueDayIsClampedToShortMonths() {
        val list = installments(3000, 3, YearMonth.of(2027, 1), day = 31).generateInstallments(YearMonth.of(2030, 1), today)
        assertEquals(
            listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)),
            list.map { LocalDate.ofEpochDay(it.epochDay) },
        )
    }

    @Test
    fun recurringWithAndWithoutEnd() {
        val limited = recurring(150000, 4, YearMonth.of(2026, 10)).generateInstallments(YearMonth.of(2030, 1), today)
        assertEquals(4, limited.size)
        assertEquals(setOf(150000L), limited.map { it.amountCents }.toSet())

        val endless = recurring(150000, null, YearMonth.of(2026, 10))
        val generated = endless.generateInstallments(until = YearMonth.of(2027, 3), todayEpochDay = today)
        assertEquals(6, generated.size)
        assertNull(generated.first().installmentCount)
        assertTrue(endless.isEndless)

        // Continuar a partir da 7ª gera os meses seguintes com ids novos e sem repetir.
        val next = endless.generateInstallments(until = YearMonth.of(2027, 5), todayEpochDay = today, fromNumber = 7)
        assertEquals(listOf(7, 8), next.map { it.installment })
        assertTrue(next.none { n -> generated.any { it.id == n.id } })
    }

    @Test
    fun incomeKeepsType() {
        val salary = recurring(500000, null, YearMonth.of(2026, 12), TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, salary.generateInstallments(YearMonth.of(2026, 12), today).single().type)
    }

    @Test
    fun amountsAndCommitments() {
        val notebook = installments(10000, 3, YearMonth.of(2027, 1))
        val rent = recurring(150000, null, YearMonth.of(2026, 12))
        val salary = recurring(500000, null, YearMonth.of(2026, 12), TransactionType.INCOME)
        assertEquals(0L, notebook.amountIn(YearMonth.of(2026, 12)))
        assertEquals(3334L, notebook.amountIn(YearMonth.of(2027, 1)))
        assertEquals(0L, notebook.amountIn(YearMonth.of(2027, 4)))
        assertEquals(150000L, rent.amountIn(YearMonth.of(2040, 1)))

        val c = fixedCommitments(listOf(notebook, rent, salary), listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 2)))
        assertEquals(MonthCommitment(YearMonth.of(2026, 12), 500000, 150000), c[0])
        assertEquals(MonthCommitment(YearMonth.of(2027, 2), 500000, 153333), c[1])
    }

    @Test
    fun countsInstallmentsDueUntilMonth() {
        val f = installments(1000, 4, YearMonth.of(2026, 10))
        assertEquals(0, f.installmentsUntil(YearMonth.of(2026, 9)))
        assertEquals(3, f.installmentsUntil(YearMonth.of(2026, 12)))
        assertEquals(4, f.installmentsUntil(YearMonth.of(2028, 1)))
        assertEquals(15, recurring(1, null, YearMonth.of(2026, 1)).installmentsUntil(YearMonth.of(2027, 3)))
    }

    @Test
    fun installmentIdsDifferBetweenFixedExpenses() {
        assertNotEquals(installmentId(1, 1), installmentId(2, 1))
        assertFalse(installmentId(1, 1) <= 0)
    }
}
