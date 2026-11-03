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
import app.financas.data.CloudRepository
import app.financas.ui.DUE_LOOKBACK_DAYS
import app.financas.ui.dueBills
import app.financas.util.formatMoney
import com.google.firebase.auth.FirebaseAuth
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "due_bills"
private const val WORK_NAME = "due-bills-daily"
private const val NOTIFICATION_ID = 1001
private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

/** Uma vez por dia (por volta das 9h) avisa as contas não pagas que vencem hoje, amanhã ou estão atrasadas. */
class DueBillsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!AppSettings(applicationContext).remindersEnabled) return Result.success()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val today = LocalDate.now().toEpochDay()
        val bills = runCatching {
            dueBills(CloudRepository(uid).transactionsOnce(today - DUE_LOOKBACK_DAYS, today + 1), today, aheadDays = 1)
        }.getOrElse { return Result.retry() }
        if (bills.isNotEmpty()) notify(applicationContext, bills.map { it.description.ifBlank { it.category.label } to it.amountCents }, bills.count { it.epochDay < today })
        return Result.success()
    }

    companion object {
        /** Agenda (ou mantém) o lembrete diário. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(REMINDER_TIME)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DueBillsWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

private fun notify(context: Context, bills: List<Pair<String, Long>>, overdue: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Contas a vencer", NotificationManager.IMPORTANCE_DEFAULT),
    )
    val title = when {
        overdue > 0 -> "Você tem ${bills.size} conta(s) pendente(s), $overdue atrasada(s)"
        bills.size == 1 -> "1 conta vence hoje ou amanhã"
        else -> "${bills.size} contas vencem hoje ou amanhã"
    }
    val lines = bills.map { (name, cents) -> "$name • ${formatMoney(cents)}" }
    val openApp = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(lines.joinToString(", "))
        .setStyle(NotificationCompat.InboxStyle().also { style -> lines.take(6).forEach(style::addLine) })
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}
