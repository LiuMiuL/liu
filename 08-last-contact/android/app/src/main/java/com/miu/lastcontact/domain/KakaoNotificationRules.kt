package com.miu.lastcontact.domain

/** 알림에서 뽑은 값만 담는 순수 데이터(본문 없음). 안드로이드 Notification을 여기로 옮겨 테스트한다. */
data class NotificationInfo(
    val packageName: String,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val category: String?,
    val hasMessagingStyle: Boolean,
    val title: String?,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    /** MessagingStyle 메시지들의 (보낸 사람 이름, 시각). 보낸 사람이 null이면 '나'가 보낸 것. */
    val messages: List<Pair<String?, Long>>,
    val postTime: Long,
)

data class Sighting(val sender: String, val atMillis: Long, val roomName: String?)

object KakaoNotificationRules {
    const val KAKAO_PACKAGE = "com.kakao.talk"

    /**
     * 카톡 대화 알림에서 '누가 언제' 보냈는지만 뽑는다.
     * 요약 알림·진행 중 알림·대화형이 아닌 알림(광고·시스템)은 무시한다.
     * 1:1 방은 보낸 사람 = 상대, 단체방은 각 메시지의 보낸 사람(방 이름은 참고로만).
     */
    fun extract(n: NotificationInfo): List<Sighting> {
        if (n.packageName != KAKAO_PACKAGE) return emptyList()
        if (n.isGroupSummary || n.isOngoing) return emptyList()
        val isChat = n.hasMessagingStyle || n.category == "msg"
        if (!isChat) return emptyList()
        val room = n.conversationTitle?.takeIf { n.isGroupConversation }
        val fromStyle = n.messages.mapNotNull { (sender, at) -> sender?.trim()?.takeIf { it.isNotEmpty() }?.let { Sighting(it, at, room) } }
        if (fromStyle.isNotEmpty()) return fromStyle.distinctBy { it.sender to it.atMillis }
        if (n.messages.isNotEmpty()) return emptyList() // 모두 내가 보낸 메시지
        // MessagingStyle이 없으면 제목을 보낸 사람으로(1:1 방). 단체방이면 제목이 방 이름일 수 있어 기록하지 않는다.
        val t = n.title?.trim()
        if (t.isNullOrEmpty() || n.isGroupConversation) return emptyList()
        return listOf(Sighting(t, n.postTime, null))
    }
}
