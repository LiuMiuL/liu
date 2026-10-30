package com.miu.lastcontact.domain

import com.miu.lastcontact.core.Phones

/** 통화 기록·문자함 한 줄(번호·시각·종류만, 문자 본문은 읽지 않는다). */
data class LogRow(val number: String?, val atMillis: Long, val kind: Kind, val durationSec: Long = 0) {
    enum class Kind { CALL_IN, CALL_OUT, CALL_MISSED, SMS_IN, SMS_OUT, OTHER }
}

data class MatchedLog(val personId: String, val atMillis: Long, val channel: Channel, val direction: Direction)

object CallSmsRules {
    /**
     * 등록한 번호와의 실제 연락만 고른다.
     * - 통화: 받은·건 통화 중 통화 시간이 1초 이상인 것(부재중·연결 안 된 발신은 연락으로 보지 않음)
     * - 문자: 받은 문자·보낸 문자
     * 등록하지 않은 번호는 버린다(모르는 번호를 앱에 남기지 않음).
     */
    fun match(rows: List<LogRow>, phoneToPerson: Map<String, String>): List<MatchedLog> = rows.mapNotNull { r ->
        val n = Phones.normalize(r.number) ?: return@mapNotNull null
        val pid = phoneToPerson[n] ?: return@mapNotNull null
        when (r.kind) {
            LogRow.Kind.CALL_IN -> if (r.durationSec > 0) MatchedLog(pid, r.atMillis, Channel.CALL, Direction.IN) else null
            LogRow.Kind.CALL_OUT -> if (r.durationSec > 0) MatchedLog(pid, r.atMillis, Channel.CALL, Direction.OUT) else null
            LogRow.Kind.SMS_IN -> MatchedLog(pid, r.atMillis, Channel.SMS, Direction.IN)
            LogRow.Kind.SMS_OUT -> MatchedLog(pid, r.atMillis, Channel.SMS, Direction.OUT)
            else -> null
        }
    }
}
