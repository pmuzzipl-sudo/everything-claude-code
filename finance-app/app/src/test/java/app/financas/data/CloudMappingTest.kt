package app.financas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CloudMappingTest {
    private val transaction = Transaction(
        id = 42, type = TransactionType.EXPENSE, amountCents = 12345, description = "Mercado",
        category = Category.FOOD, epochDay = 20000, fixedExpenseId = 7, installment = 2, installmentCount = 10,
    )

    @Test
    fun transactionRoundTrip() {
        assertEquals(transaction, transactionFromCloud(42, transaction.toCloud()))
        val variable = transaction.copy(fixedExpenseId = null, installment = null, installmentCount = null)
        assertEquals(variable, transactionFromCloud(42, variable.toCloud()))
    }

    @Test
    fun acceptsFirestoreNumberTypes() {
        // O Firestore devolve inteiros como Long; aceita também Int e Double.
        val data = transaction.toCloud().toMutableMap().apply {
            put("amountCents", 12345.0)
            put("installment", 2L)
        }
        assertEquals(transaction, transactionFromCloud(42, data))
    }

    @Test
    fun invalidDataIsSkipped() {
        assertNull(transactionFromCloud(1, transaction.toCloud() + ("category" to "CATEGORIA_NOVA")))
        assertNull(transactionFromCloud(1, transaction.toCloud() - "amountCents"))
        assertNull(budgetFromCloud("NAO_EXISTE", mapOf("limitCents" to 100L)))
    }

    @Test
    fun budgetAndFixedRoundTrip() {
        val budget = Budget(Category.FOOD, 80000)
        assertEquals(budget, budgetFromCloud("FOOD", budget.toCloud()))
        val fixed = FixedExpense(9, "Aluguel", Category.HOUSING, 300000, 12, 24316, 5)
        assertEquals(fixed, fixedExpenseFromCloud(9, fixed.toCloud()))
    }

    @Test
    fun cloudIdsArePositive() {
        val random = Random(1)
        repeat(1000) { assertTrue(newCloudId(random) > 0) }
    }

    @Test
    fun legacyIdsAreStableAndKeepInstallmentLinks() {
        val snapshot = LegacySnapshot(
            transactions = listOf(transaction.copy(id = 1, fixedExpenseId = 1), transaction.copy(id = 2, fixedExpenseId = null)),
            budgets = listOf(Budget(Category.FOOD, 1000)),
            fixedExpenses = listOf(FixedExpense(1, "Aluguel", Category.HOUSING, 100, 1, 24316, 1)),
        )
        val a = snapshot.withCloudIds(salt = 123)
        // Reenviar gera os mesmos ids (não duplica).
        assertEquals(a, snapshot.withCloudIds(salt = 123))
        // A parcela continua apontando para a despesa fixa.
        assertEquals(a.fixedExpenses.single().id, a.transactions[0].fixedExpenseId)
        // Lançamento 1 e despesa fixa 1 não colidem.
        assertNotEquals(a.fixedExpenses.single().id, a.transactions[0].id)
        // Outro aparelho (outro sal) gera ids diferentes.
        assertNotEquals(a.transactions[0].id, snapshot.withCloudIds(salt = 456).transactions[0].id)
        assertTrue((a.transactions + a.transactions).all { it.id > 0 })
    }
}
