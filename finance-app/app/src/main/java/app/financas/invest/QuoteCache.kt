package app.financas.invest

import android.content.Context
import org.json.JSONObject

/** Últimas cotações guardadas neste aparelho, para mostrar a carteira na hora (e sem internet). */
class QuoteCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("quotes", Context.MODE_PRIVATE)

    fun load(): Map<String, Quote> = runCatching {
        val json = JSONObject(prefs.getString("quotes", null) ?: return emptyMap())
        json.keys().asSequence().mapNotNull { ticker ->
            val q = json.getJSONObject(ticker)
            ticker to Quote(
                symbol = ticker,
                name = if (q.isNull("name")) null else q.optString("name"),
                price = q.getDouble("price"),
                changePercent = if (q.isNull("change")) null else q.getDouble("change"),
                fetchedAtMillis = q.getLong("at"),
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    fun save(quotes: Map<String, Quote>) {
        val json = JSONObject()
        quotes.forEach { (ticker, q) ->
            json.put(
                ticker,
                JSONObject()
                    .put("name", q.name ?: JSONObject.NULL)
                    .put("price", q.price)
                    .put("change", q.changePercent ?: JSONObject.NULL)
                    .put("at", q.fetchedAtMillis),
            )
        }
        prefs.edit().putString("quotes", json.toString()).apply()
    }
}
