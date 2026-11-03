package app.financas.ui

import app.financas.data.Category
import app.financas.data.Transaction
import app.financas.data.TransactionType
import app.financas.util.descriptionTokens
import java.text.Normalizer

enum class SearchPeriod(val label: String, val days: Long?) {
    MONTH("30 dias", 30),
    QUARTER("3 meses", 92),
    YEAR("12 meses", 366),
    ALL("Tudo", null),
}

data class SearchQuery(
    val text: String = "",
    val type: TransactionType? = null,
    val category: Category? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
    val period: SearchPeriod = SearchPeriod.ALL,
)

private val ACCENTS = Regex("\\p{M}+")

private fun normalize(text: String) =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(ACCENTS, "")

/**
 * Filtra os lançamentos. O texto ignora acentos e maiúsculas e precisa ter todas as palavras digitadas,
 * na descrição ou no nome da categoria ("uber" encontra "Uber *Viagem"). Período conta a partir de hoje
 * para trás, incluindo lançamentos futuros.
 */
fun searchTransactions(all: List<Transaction>, query: SearchQuery, todayEpochDay: Long): List<Transaction> {
    val words = descriptionTokens(query.text).ifEmpty {
        // Palavras curtas (ex.: "tv") são ignoradas pela tokenização; usa o texto puro nesse caso.
        normalize(query.text).split(' ').filter { it.isNotBlank() }
    }
    val from = query.period.days?.let { todayEpochDay - it }
    return all.filter { t ->
        (query.type == null || t.type == query.type) &&
            (query.category == null || t.category == query.category) &&
            (query.minCents == null || t.amountCents >= query.minCents) &&
            (query.maxCents == null || t.amountCents <= query.maxCents) &&
            (from == null || t.epochDay >= from) &&
            words.all { w -> w in normalize(t.description) || w in normalize(t.category.label) }
    }.sortedWith(compareByDescending<Transaction> { it.epochDay }.thenByDescending { it.id })
}
