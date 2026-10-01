package app.financas.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionGroupingTest {
    private fun groups(vararg descriptions: String) =
        groupBySimilarDescription(descriptions.toList()) { it }
            .associate { it.label to it.items.sorted() }

    @Test
    fun normalizesAccentsCaseNumbersAndStopwords() {
        assertEquals(listOf("farmacia", "sao", "joao"), descriptionTokens("Farmácia São João 24h"))
        assertEquals(listOf("uber", "viagem"), descriptionTokens("UBER *Viagem 23/09"))
        assertEquals(listOf("conta", "luz"), descriptionTokens("Conta de luz"))
    }

    @Test
    fun groupsVariationsOfSameDescription() {
        val result = groups("Uber", "uber viagem", "UBER *TRIP 12/03", "Ifood", "iFood pizza")
        assertEquals(2, result.size)
        assertEquals(3, result.getValue("Uber").size)
        assertEquals(2, result.getValue("Ifood").size)
    }

    @Test
    fun genericDescriptionCollectsMoreSpecificOnes() {
        val result = groups("Mercado Extra", "Mercado Carrefour", "Mercado", "mercado")
        assertEquals(1, result.size)
        assertEquals(4, result.values.single().size)
    }

    @Test
    fun keepsDifferentBillsApart() {
        val result = groups("Conta de luz", "Conta de água", "conta luz")
        assertEquals(2, result.size)
        assertEquals(listOf("Conta de luz", "conta luz"), result.getValue("conta luz"))
    }

    @Test
    fun toleratesSmallTypos() {
        assertTrue(similarDescriptions(descriptionTokens("Lanche"), descriptionTokens("Lanches")))
        assertTrue(similarDescriptions(descriptionTokens("Academia"), descriptionTokens("Acadmia")))
        assertFalse(similarDescriptions(descriptionTokens("Aluguel"), descriptionTokens("Academia")))
    }

    @Test
    fun emptyDescriptionsShareOneGroup() {
        val result = groups("", "  ", "Padaria")
        assertEquals(2, result.getValue("Sem descrição").size)
        assertEquals(1, result.getValue("Padaria").size)
    }
}
