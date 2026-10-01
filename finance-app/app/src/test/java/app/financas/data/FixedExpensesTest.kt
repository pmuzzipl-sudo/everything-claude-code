package app.financas.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FixedExpensesTest {
    private fun fixed(total: Long, installments: Int, start: YearMonth, day: Int = 10) = FixedExpense(
        id = 7,
        description = "Notebook",
        category = Category.SHOPPING,
        totalCents = total,
        installments = installments,
        startEpochMonth = start.toEpochMonth(),
        dayOfMonth = day,
    )

    @Test
    fun splitKeepsTotalAndPutsRemainderFirst() {
        assertEquals(listOf(3334L, 3333L, 3333L), splitInstallments(10000, 3))
        assertEquals(listOf(5000L, 5000L), splitInstallments(10000, 2))
        assertEquals(listOf(10000L), splitInstallments(10000, 1))
    }

    @Test
    fun epochMonthRoundTrip() {
        val m = YearMonth.of(2026, 12)
        assertEquals(m, epochMonthToYearMonth(m.toEpochMonth()))
        assertEquals(YearMonth.of(2027, 1), epochMonthToYearMonth(m.toEpochMonth() + 1))
    }

    @Test
    fun generatesOneInstallmentPerMonthAcrossYears() {
        val list = fixed(120000, 3, YearMonth.of(2026, 11)).toInstallments()
        assertEquals(3, list.size)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10)),
            list.map { LocalDate.ofEpochDay(it.epochDay) },
        )
        assertEquals(listOf(1, 2, 3), list.map { it.installment })
        assertEquals(setOf(3), list.map { it.installmentCount }.toSet())
        assertEquals(setOf(7L), list.map { it.fixedExpenseId }.toSet())
        assertEquals(setOf(TransactionType.EXPENSE), list.map { it.type }.toSet())
        assertEquals(120000L, list.sumOf { it.amountCents })
    }

    @Test
    fun dueDayIsClampedToShortMonths() {
        val list = fixed(3000, 3, YearMonth.of(2027, 1), day = 31).toInstallments()
        assertEquals(
            listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)),
            list.map { LocalDate.ofEpochDay(it.epochDay) },
        )
    }

    @Test
    fun countsInstallmentsDueUntilMonth() {
        val f = fixed(1000, 4, YearMonth.of(2026, 10))
        assertEquals(0, f.installmentsUntil(YearMonth.of(2026, 9)))
        assertEquals(1, f.installmentsUntil(YearMonth.of(2026, 10)))
        assertEquals(3, f.installmentsUntil(YearMonth.of(2026, 12)))
        assertEquals(4, f.installmentsUntil(YearMonth.of(2028, 1)))
    }
}
