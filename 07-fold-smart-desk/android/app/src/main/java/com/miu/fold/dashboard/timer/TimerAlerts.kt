package com.miu.fold.dashboard.timer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.ui.Vms

data class AlertStatus(val notificationsOn: Boolean, val precision: AlarmPrecision)

fun alertStatus(context: Context) = AlertStatus(TimerNotifier.canNotify(context), AndroidTimerAlarm.precision(context))

/**
 * 타이머 시작 함수를 감싸서, Android 13+에서 알림 권한을 '처음 한 번만' 묻는다.
 * 거절해도 타이머는 그대로 시작한다(앱 안 타이머는 권한과 무관).
 */
@Composable
fun rememberStartWithNotificationAsk(vms: Vms): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val settings by vms.settings.settings.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return { start ->
        start()
        if (Build.VERSION.SDK_INT >= 33 && !settings.notificationAsked && !TimerNotifier.canNotify(context)) {
            vms.settings.markNotificationAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** 종료 알림 상태 안내. 설정에서 돌아오면 다시 확인한다. */
@Composable
fun TimerAlertStatus(compact: Boolean) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val status = remember(refresh) { alertStatus(context) }
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        when {
            !status.notificationsOn -> {
                Text("알림이 꺼져 있어 앱 밖에서는 종료를 알려주지 않습니다. 앱에 돌아오면 실제 경과 시간으로 계산됩니다.", style = small, color = muted)
                TextButton(onClick = { openAppNotificationSettings(context) }) { Text("알림 켜기") }
            }
            status.precision == AlarmPrecision.INEXACT -> {
                Text("종료 알림이 기기 절전 정책에 따라 조금 늦게 올 수 있습니다.", style = small, color = muted)
                if (!compact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    TextButton(onClick = { openExactAlarmSettings(context) }) { Text("정확한 알림 허용") }
                }
            }
            else -> Text("앱을 나가도 끝나면 알림으로 알려줍니다.", style = small, color = muted)
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(i) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) } }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(i) }
}
