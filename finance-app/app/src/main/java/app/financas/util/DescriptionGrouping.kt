package app.financas.util

import java.text.Normalizer

/** Lançamentos cujas descrições foram consideradas parecidas. */
data class DescriptionGroup<T>(val label: String, val items: List<T>)

private val STOPWORDS = setOf(
    "de", "da", "do", "das", "dos", "e", "a", "o", "as", "os", "em", "no", "na", "nos", "nas",
    "para", "pra", "pro", "com", "por", "um", "uma",
)
private val ACCENTS = Regex("\\p{M}+")
private val NON_LETTERS = Regex("[^a-z]+")

/** Mínimo de semelhança (0 a 1) entre duas descrições para ficarem no mesmo grupo. */
private const val SIMILARITY_THRESHOLD = 0.8

/**
 * "Uber *Viagem 23/09" -> [uber, viagem]: sem acentos, maiúsculas, números, pontuação e palavras vazias.
 */
fun descriptionTokens(text: String): List<String> =
    Normalizer.normalize(text.lowercase(PT_BR), Normalizer.Form.NFD)
        .replace(ACCENTS, "")
        .split(NON_LETTERS)
        .filter { it.length >= 2 && it !in STOPWORDS }

/**
 * Duas descrições são parecidas quando as palavras de uma estão contidas na outra
 * ("Uber" e "Uber viagem") ou quando o texto é quase igual ("Lanche" e "Lanches").
 */
fun similarDescriptions(a: List<String>, b: List<String>): Boolean {
    if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty()
    val sa = a.toSet()
    val sb = b.toSet()
    if (sa.containsAll(sb) || sb.containsAll(sa)) return true
    return similarity(a.joinToString(" "), b.joinToString(" ")) >= SIMILARITY_THRESHOLD
}

/**
 * Agrupa itens com descrições parecidas. As descrições mais curtas (mais genéricas) formam os grupos
 * primeiro, assim "Mercado Extra" e "Mercado Carrefour" se juntam ao grupo "Mercado" quando ele existe.
 * O nome do grupo é a descrição mais frequente entre os itens.
 */
fun <T> groupBySimilarDescription(items: List<T>, description: (T) -> String): List<DescriptionGroup<T>> {
    class Cluster(val tokens: List<String>) { val members = mutableListOf<T>() }

    val clusters = mutableListOf<Cluster>()
    items.map { it to descriptionTokens(description(it)) }
        .sortedBy { it.second.size }
        .forEach { (item, tokens) ->
            val cluster = clusters.firstOrNull { similarDescriptions(it.tokens, tokens) }
                ?: Cluster(tokens).also { clusters += it }
            cluster.members += item
        }

    return clusters.map { cluster ->
        val label = cluster.members
            .map { description(it).trim() }
            .filter { it.isNotEmpty() }
            .groupBy { it.lowercase(PT_BR) }
            .values
            .maxWithOrNull(compareBy<List<String>> { it.size }.thenByDescending { it.first().length })
            ?.first()
            ?: "Sem descrição"
        DescriptionGroup(label, cluster.members)
    }
}

/** Semelhança entre 0 e 1 baseada na distância de edição (Levenshtein). */
private fun similarity(a: String, b: String): Double {
    val longest = maxOf(a.length, b.length)
    if (longest == 0) return 1.0
    return 1.0 - levenshtein(a, b).toDouble() / longest
}

private fun levenshtein(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return previous[b.length]
}
