package app.financas.ui

import app.financas.data.Category
import app.financas.data.Transaction
import app.financas.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ComparisonTest {
    private fun t(category: Category, cents: Long, date: LocalDate, fixed: Boolean = false, type: TransactionType = TransactionType.EXPENSE) =
        Transaction(
            type = type, amountCents = cents, description = "", category = category,
            epochDay = date.toEpochDay(), fixedExpenseId = if (fixed) 1 else null,
        )

    private val months = comparisonMonths(YearMonth.of(2027, 1), 3)
    private val data = listOf(
        t(Category.FOOD, 100, LocalDate.of(2026, 11, 5)),
        t(Category.FOOD, 250, LocalDate.of(2027, 1, 2)),
        t(Category.FOOD, 50, LocalDate.of(2027, 1, 20)),
        t(Category.HOUSING, 1000, LocalDate.of(2026, 12, 1), fixed = true),
        t(Category.HOUSING, 1000, LocalDate.of(2027, 1, 1), fixed = true),
        t(Category.SALARY, 9999, LocalDate.of(2027, 1, 1), type = TransactionType.INCOME),
        t(Category.LEISURE, 70, LocalDate.of(2026, 10, 31)), // fora do período
    )

    @Test
    fun monthsEndAtSelectedMonth() {
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1)), months)
    }

    @Test
    fun totalsPerCategoryPerMonth() {
        val result = expenseTotalsByCategory(data, months, ExpenseKind.ALL)
        assertEquals(listOf(Category.HOUSING, Category.FOOD), result.map { it.first })
        assertEquals(listOf(0L, 1000L, 1000L), result[0].second)
        assertEquals(listOf(100L, 0L, 300L), result[1].second)
    }

    @Test
    fun filtersFixedAndVariable() {
        assertEquals(listOf(Category.HOUSING), expenseTotalsByCategory(data, months, ExpenseKind.FIXED).map { it.first })
        assertEquals(listOf(Category.FOOD), expenseTotalsByCategory(data, months, ExpenseKind.VARIABLE).map { it.first })
    }

    @Test
    fun monthOverMonth() {
        assertEquals(50, monthOverMonthChange(200, 300))
        assertEquals(-25, monthOverMonthChange(400, 300))
        assertNull(monthOverMonthChange(0, 300))
    }
}
