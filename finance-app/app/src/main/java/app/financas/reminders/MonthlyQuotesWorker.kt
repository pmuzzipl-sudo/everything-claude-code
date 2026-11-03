package app.financas.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.financas.MainActivity
import app.financas.R
import app.financas.data.AppSettings
import app.financas.data.toEpochMonth
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "monthly_quotes"
private const val WORK_NAME = "monthly-quotes-reminder"
private const val NOTIFICATION_ID = 1002
private val REMINDER_TIME: LocalTime = LocalTime.of(10, 0)

/** Até que dia do mês o lembrete ainda é enviado, se o aparelho estava desligado no dia 1º. */
private const val LAST_REMINDER_DAY = 3

/** Extra do Intent que abre o app direto na área de investimentos. */
const val EXTRA_OPEN_AREA = "open_area"
const val AREA_INVESTMENTS = "investments"

/** O lembrete deve sair hoje? Só nos primeiros dias do mês e uma vez por mês. */
fun shouldSendMonthlyReminder(today: LocalDate, lastSentEpochMonth: Int): Boolean =
    today.dayOfMonth <= LAST_REMINDER_DAY && YearMonth.from(today).toEpochMonth() != lastSentEpochMonth

/**
 * Roda uma vez por dia (por volta das 10h); no começo de cada mês lembra de abrir a área de investimentos
 * para atualizar as cotações e registrar o mês no acompanhamento de longo prazo.
 */
class MonthlyQuotesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        val today = LocalDate.now()
        if (!settings.monthlyQuotesReminder || !shouldSendMonthlyReminder(today, settings.lastMonthlyReminderMonth)) {
            return Result.success()
        }
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        // Sem ativos cadastrados não há o que atualizar. Se não conseguir verificar, lembra mesmo assim.
        val hasAssets = runCatching {
            !FirebaseFirestore.getInstance().collection("users").document(uid).collection("assets")
                .limit(1).get().await().isEmpty
        }.getOrDefault(true)
        if (hasAssets) notify(applicationContext)
        settings.lastMonthlyReminderMonth = YearMonth.from(today).toEpochMonth()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(REMINDER_TIME)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<MonthlyQuotesWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

private fun notify(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Atualização mensal da carteira", NotificationManager.IMPORTANCE_DEFAULT),
    )
    val open = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_AREA, AREA_INVESTMENTS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Hora de atualizar sua carteira")
        .setContentText("Abra Investimentos para registrar as cotações do mês no acompanhamento de longo prazo.")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("Abra Investimentos para atualizar as cotações e registrar o valor da carteira deste mês no acompanhamento de longo prazo."),
        )
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}
