package com.miu.lastcontact.capture

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
import com.miu.lastcontact.LastContactApp
import com.miu.lastcontact.MainActivity
import com.miu.lastcontact.R
import com.miu.lastcontact.domain.ContactRules
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** 오늘 연락할 때가 된 사람 목록 문구. 없으면 null. */
object ReminderText {
    fun build(overdue: List<Pair<String, Long>>): String? {
        if (overdue.isEmpty()) return null
        val top = overdue.sortedByDescending { it.second }.take(3).joinToString(", ") { (n, d) -> if (d == Long.MAX_VALUE) "$n(기록 없음)" else "$n(${d}일 초과)" }
        return if (overdue.size > 3) "$top 외 ${overdue.size - 3}명" else top
    }
}

/**
 * 하루 한 번: (켜져 있으면) 통화·문자 가져오기 → 목표 주기를 넘긴 사람이 있으면 알림 1개.
 * WorkManager는 정확한 시각을 보장하지 않으므로 '대략 그 시각쯤'으로 안내한다.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? LastContactApp ?: return Result.success()
        val c = app.container
        val s = c.settings.current()
        if (s.callSmsOn) runCatching { c.callSms.sync(c.clock.nowMillis()) }
        if (!s.reminderOn) return Result.success()
        val today = c.clock.today()
        val overdue = c.repo.summaries.first().mapNotNull { ps ->
            val by = ContactRules.overdueBy(ps.last?.day, ps.person.goalDays, today) ?: return@mapNotNull null
            if (by > 0) ps.person.name to by else null
        }
        ReminderText.build(overdue)?.let { Reminders.notify(applicationContext, "연락할 때가 됐어요", it) }
        return Result.success()
    }
}

object Reminders {
    private const val WORK = "daily_reminder"
    private const val CHANNEL = "reminder"

    /** 이미 예약돼 있으면 그대로 둔다(앱을 열 때마다 시각이 밀리지 않게). */
    fun ensure(context: Context, hour: Int) = enqueue(context, hour, ExistingPeriodicWorkPolicy.KEEP)

    /** 시각을 바꿨을 때: 새 시각으로 다시 예약. */
    fun schedule(context: Context, hour: Int) = enqueue(context, hour, ExistingPeriodicWorkPolicy.UPDATE)

    private fun enqueue(context: Context, hour: Int, policy: ExistingPeriodicWorkPolicy) {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).setInitialDelay(delay, TimeUnit.MINUTES).build()
        // WorkManager 초기화 실패 등으로 앱 실행 자체가 죽지 않게 한다(예약만 건너뜀).
        runCatching { WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, policy, req) }
    }

    fun cancel(context: Context) { runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) } }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notify(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "연락 알림", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_contact)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open).build()
        try { NotificationManagerCompat.from(context).notify(2001, n) } catch (_: SecurityException) { }
    }
}
