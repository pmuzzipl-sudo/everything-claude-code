package app.financas.invest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId

class QuoteException(message: String) : Exception(message)

/**
 * Cotações da B3 pelo brapi.dev. No plano gratuito: 1 ativo por consulta, histórico de até 3 meses e
 * preços com cerca de 30 minutos de atraso.
 */
object Brapi {
    private const val BASE = "https://brapi.dev/api/quote/"
    private const val TIMEOUT_MS = 15_000

    suspend fun quote(ticker: String, token: String, withHistory: Boolean = false): Quote = withContext(Dispatchers.IO) {
        val query = buildString {
            append("?token=").append(URLEncoder.encode(token, "UTF-8"))
            if (withHistory) append("&range=3mo&interval=1d")
        }
        val connection = URL(BASE + URLEncoder.encode(ticker, "UTF-8") + query).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val code = runCatching { connection.responseCode }
                .getOrElse { throw QuoteException("Sem conexão com o serviço de cotações.") }
            if (code != 200) throw QuoteException(errorMessage(code, ticker))
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseQuote(body, System.currentTimeMillis()) ?: throw QuoteException("$ticker: cotação não encontrada.")
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(code: Int, ticker: String) = when (code) {
        401 -> "Chave do brapi inválida. Confira em Configurações."
        402, 403 -> "$ticker: o plano do brapi não permite esta consulta."
        404 -> "$ticker: ativo não encontrado na B3."
        429 -> "Limite de consultas do brapi atingido. Tente mais tarde."
        else -> "$ticker: erro $code no serviço de cotações."
    }
}

/** Lê a resposta do brapi: results[0] com regularMarketPrice e, se pedido, historicalDataPrice. */
fun parseQuote(body: String, fetchedAtMillis: Long, zone: ZoneId = ZoneId.of("America/Sao_Paulo")): Quote? = runCatching {
    val result = JSONObject(body).getJSONArray("results").getJSONObject(0)
    if (!result.has("regularMarketPrice") || result.isNull("regularMarketPrice")) return null
    val history = result.optJSONArray("historicalDataPrice")?.let { array ->
        (0 until array.length()).mapNotNull { i ->
            val point = array.optJSONObject(i) ?: return@mapNotNull null
            if (!point.has("close") || point.isNull("close")) return@mapNotNull null
            val day = Instant.ofEpochSecond(point.getLong("date")).atZone(zone).toLocalDate().toEpochDay()
            day to point.getDouble("close")
        }
    }.orEmpty()
    Quote(
        symbol = result.optString("symbol"),
        name = result.text("longName") ?: result.text("shortName"),
        price = result.getDouble("regularMarketPrice"),
        changePercent = if (result.isNull("regularMarketChangePercent")) null else result.optDouble("regularMarketChangePercent").takeIf { !it.isNaN() },
        fetchedAtMillis = fetchedAtMillis,
        history = history.distinctBy { it.first }.sortedBy { it.first },
    )
}.getOrNull()

/** Texto do campo, ou null se ausente, nulo no JSON ou vazio. */
private fun JSONObject.text(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).trim().ifEmpty { null }
