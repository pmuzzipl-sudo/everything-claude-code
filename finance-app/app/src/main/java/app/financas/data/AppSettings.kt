package app.financas.data

import android.content.Context

/** Preferências deste aparelho (não sincronizam): bloqueio do app e lembretes de contas. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock_enabled", false)
        set(value) = prefs.edit().putBoolean("lock_enabled", value).apply()

    var remindersEnabled: Boolean
        get() = prefs.getBoolean("reminders_enabled", true)
        set(value) = prefs.edit().putBoolean("reminders_enabled", value).apply()

    /** Já pediu permissão de notificação uma vez (não insiste se o usuário recusar). */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(value) = prefs.edit().putBoolean("asked_notifications", value).apply()

    var monthlyQuotesReminder: Boolean
        get() = prefs.getBoolean("monthly_quotes_reminder", true)
        set(value) = prefs.edit().putBoolean("monthly_quotes_reminder", value).apply()

    /** Mês (epoch month) do último lembrete mensal enviado, para não repetir. */
    var lastMonthlyReminderMonth: Int
        get() = prefs.getInt("last_monthly_reminder", -1)
        set(value) = prefs.edit().putInt("last_monthly_reminder", value).apply()
}
