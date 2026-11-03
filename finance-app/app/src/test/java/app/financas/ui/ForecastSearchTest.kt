package app.financas.ui

import app.financas.data.Category
import app.financas.data.Transaction
import app.financas.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastSearchTest {
    private val today = 20000L

    private fun t(
        id: Long, description: String, cents: Long, day: Long, paid: Boolean = true,
        type: TransactionType = TransactionType.EXPENSE, category: Category = Category.FOOD,
    ) = Transaction(id = id, type = type, amountCents = cents, description = description, category = category, epochDay = day, paid = paid)

    @Test
    fun dueBillsAreUnpaidExpensesOverdueOrDueSoon() {
        val list = listOf(
            t(1, "Aluguel", 1500, today + 3, paid = false),
            t(2, "Internet", 100, today - 2, paid = false),
            t(3, "Mercado", 50, today, paid = true),
            t(4, "Escola", 900, today + 8, paid = false),
            t(5, "Salário", 5000, today + 1, paid = false, type = TransactionType.INCOME),
        )
        assertEquals(listOf(2L, 1L), dueBills(list, today).map { it.id })
    }

    @Test
    fun dueLabels() {
        assertEquals("Atrasada há 3 dias", dueLabel(today - 3, today))
        assertEquals("Atrasada desde ontem", dueLabel(today - 1, today))
        assertEquals("Vence hoje", dueLabel(today, today))
        assertEquals("Vence amanhã", dueLabel(today + 1, today))
        assertEquals("Vence em 5 dias", dueLabel(today + 5, today))
    }

    @Test
    fun searchByTextIgnoresAccentsAndCase() {
        val list = listOf(
            t(1, "UBER *Viagem", 2500, today),
            t(2, "Farmácia São João", 4000, today - 10, category = Category.HEALTH),
            t(3, "Padaria", 800, today - 400),
            t(4, "Salário", 500000, today, type = TransactionType.INCOME, category = Category.SALARY),
        )
        assertEquals(listOf(1L), searchTransactions(list, SearchQuery(text = "uber"), today).map { it.id })
        assertEquals(listOf(2L), searchTransactions(list, SearchQuery(text = "farmacia sao"), today).map { it.id })
        // Também acha pelo nome da categoria.
        assertEquals(listOf(2L), searchTransactions(list, SearchQuery(text = "saúde"), today).map { it.id })
        assertEquals(listOf(4L), searchTransactions(list, SearchQuery(type = TransactionType.INCOME), today).map { it.id })
        assertEquals(listOf(1L, 2L), searchTransactions(list, SearchQuery(minCents = 2000, maxCents = 5000), today).map { it.id })
        assertEquals(listOf(4L, 1L, 2L), searchTransactions(list, SearchQuery(period = SearchPeriod.MONTH), today).map { it.id })
        assertEquals(4, searchTransactions(list, SearchQuery(), today).size)
    }
}
