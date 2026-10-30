package com.miu.lastcontact.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.miu.lastcontact.LastContactApp
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.KakaoNotificationRules
import com.miu.lastcontact.domain.NotificationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 카톡 알림에서 '보낸 사람 이름·시각'만 기록한다. 메시지 본문은 읽어 들인 객체에서 꺼내지 않고 저장하지도 않는다.
 * 사용자가 시스템 설정 '알림 접근'에서 이 앱을 직접 허용해야 동작한다. 다른 앱 알림은 패키지 이름만 보고 바로 버린다.
 */
class KakaoNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != KakaoNotificationRules.KAKAO_PACKAGE) return
        val app = applicationContext as? LastContactApp ?: return
        val info = toInfo(sbn)
        val sightings = KakaoNotificationRules.extract(info)
        if (sightings.isEmpty()) return
        scope.launch {
            if (!app.container.settings.current().kakaoCaptureOn) return@launch
            sightings.forEach { runCatching { app.container.repo.onSighting(it, Channel.KAKAO_NOTIFICATION) } }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun toInfo(sbn: StatusBarNotification): NotificationInfo {
        val n = sbn.notification
        val extras = n.extras
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        return NotificationInfo(
            packageName = sbn.packageName,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            isOngoing = sbn.isOngoing,
            category = n.category,
            hasMessagingStyle = style != null,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            conversationTitle = style?.conversationTitle?.toString() ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            isGroupConversation = style?.isGroupConversation ?: extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            messages = style?.messages?.map { it.person?.name?.toString() to it.timestamp }.orEmpty(),
            postTime = sbn.postTime,
        )
    }

    companion object {
        /** 시스템 설정에서 이 앱의 알림 접근이 켜져 있는지 */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, KakaoNotificationListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }
    }
}
