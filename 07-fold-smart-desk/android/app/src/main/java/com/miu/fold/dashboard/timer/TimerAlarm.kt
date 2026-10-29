package com.miu.fold.dashboard.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miu.fold.dashboard.FoldDeskApp
import com.miu.fold.dashboard.MainActivity
import com.miu.fold.dashboard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 타이머 종료 예약. 상태가 바뀔 때마다 [sync]를 호출하면 RUNNING일 때만 예약하고 나머지는 취소한다. */
interface TimerAlarm {
    fun sync(s: TimerSnapshot?)

    object None : TimerAlarm {
        override fun sync(s: TimerSnapshot?) = Unit
    }
}

/** 앱이 화면에 보이는지. 보이면 시스템 알림 대신 앱 안 안내만 한다. */
object AppVisibility {
    @Volatile var visible: Boolean = false
}

enum class AlarmPrecision { EXACT, INEXACT }

/**
 * AlarmManager 기반 종료 알림.
 * - ELAPSED_REALTIME_WAKEUP(부팅 후 경과 시간) 기준이라 시스템 시각·시간대 변경에 영향받지 않는다.
 * - 정확 알람 권한이 있으면 setExactAndAllowWhileIdle, 없으면 setAndAllowWhileIdle(지연될 수 있음). 권한을 강제로 얻지 않는다.
 * - 재부팅하면 예약이 지워진다. 정책상 재부팅된 세션은 완료가 아니라 '중단됨'이므로 다시 예약하지 않는다.
 * - WorkManager는 초 단위 종료 알림에 쓰지 않는다.
 */
class AndroidTimerAlarm(private val context: Context) : TimerAlarm {
    private val am = context.getSystemService(AlarmManager::class.java)

    override fun sync(s: TimerSnapshot?) {
        val pi = pendingIntent(s?.id)
        if (s == null || s.state != TimerState.RUNNING || s.remainingMs <= 0) {
            am.cancel(pi)
            return
        }
        val at = s.monotonicAnchor + s.remainingMs
        if (precision(context) == AlarmPrecision.EXACT) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(sessionId: String?): PendingIntent {
        val i = Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_TIMER_DONE)
        sessionId?.let { i.putExtra(EXTRA_SESSION, it) }
        // 요청 코드를 고정해 활성 세션 하나에 예약 하나만 남게 한다.
        return PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_TIMER_DONE = "com.miu.fold.dashboard.TIMER_DONE"
        const val EXTRA_SESSION = "sessionId"

        fun precision(context: Context): AlarmPrecision {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return AlarmPrecision.EXACT
            val am = context.getSystemService(AlarmManager::class.java)
            return if (am.canScheduleExactAlarms()) AlarmPrecision.EXACT else AlarmPrecision.INEXACT
        }
    }
}

/** 예약 시각에 깨어나 상태를 정리(완료 기록은 세션당 1건)하고, 앱이 안 보이면 알림을 띄운다. */
class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidTimerAlarm.ACTION_TIMER_DONE) return
        val sessionId = intent.getStringExtra(AndroidTimerAlarm.EXTRA_SESSION) ?: return
        val app = context.applicationContext as? FoldDeskApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val s = app.container.timer.reconcile()
                if (s != null && s.id == sessionId && s.state == TimerState.COMPLETED && !AppVisibility.visible) {
                    TimerNotifier.showCompleted(context, s)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

object TimerNotifier {
    private const val CHANNEL = "focus_timer"
    private const val ID = 1001

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "집중 타이머", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "집중·휴식 타이머가 끝났을 때 알립니다." },
            )
        }
    }

    fun showCompleted(context: Context, s: TimerSnapshot) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val minutes = s.plannedDurationMs / 60_000
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("${s.label} ${minutes}분 끝")
            .setContentText("타이머가 끝났습니다. 눌러서 기록을 확인하세요.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (e: SecurityException) {
            // 권한이 방금 회수된 경우. 앱 안 타이머 기록은 이미 저장됐다.
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}
