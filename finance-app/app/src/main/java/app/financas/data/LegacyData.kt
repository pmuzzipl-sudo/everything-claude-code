package app.financas.data

import android.content.Context
import kotlin.random.Random

/**
 * Dados salvos só neste aparelho pelas versões anteriores à sincronização (banco Room local).
 * São enviados uma única vez para a conta do usuário, se ele quiser.
 */
class LegacyData(private val context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    val alreadyHandled: Boolean get() = prefs.getBoolean(KEY_DONE, false)

    suspend fun load(): LegacySnapshot {
        val dao = FinanceDatabase.get(context).dao()
        return LegacySnapshot(dao.allTransactions(), dao.allBudgets(), dao.allFixedExpenses())
    }

    /** Sal fixo por aparelho, para que reenviar gere os mesmos ids e não duplique nada. */
    fun salt(): Long {
        if (!prefs.contains(KEY_SALT)) prefs.edit().putLong(KEY_SALT, Random.nextLong()).apply()
        return prefs.getLong(KEY_SALT, 0)
    }

    fun markHandled() {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val KEY_DONE = "legacy_handled"
        const val KEY_SALT = "legacy_salt"
    }
}
