package com.miu.fold.dashboard.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 종일 일정은 LocalDate 범위, 시간 지정 일정은 Instant 범위로 따로 관리한다.
 * 하나의 timestamp로 억지 통합하지 않는다.
 */
data class EventSpan(
    val allDay: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startInstant: Long?,
    val endInstant: Long?,
)

sealed interface EventInputResult {
    data class Ok(val span: EventSpan, val zoneId: String?) : EventInputResult
    data class Error(val message: String) : EventInputResult
}

object EventRules {
    fun parseTime(s: String): LocalTime? = runCatching {
        val t = s.trim()
        val (h, m) = t.split(':').let { it[0].toInt() to (it.getOrNull(1)?.toInt() ?: 0) }
        LocalTime.of(h, m)
    }.getOrNull()

    fun build(
        allDay: Boolean,
        startDate: LocalDate,
        endDate: LocalDate,
        startTime: String,
        endTime: String,
        zone: ZoneId,
    ): EventInputResult {
        if (endDate.isBefore(startDate)) return EventInputResult.Error("종료 날짜가 시작 날짜보다 빠릅니다.")
        if (allDay) return EventInputResult.Ok(EventSpan(true, startDate, endDate, null, null), null)
        val st = parseTime(startTime) ?: return EventInputResult.Error("시작 시각을 HH:mm 형식으로 입력하세요.")
        val et = parseTime(endTime) ?: return EventInputResult.Error("종료 시각을 HH:mm 형식으로 입력하세요.")
        val s = LocalDateTime.of(startDate, st).atZone(zone).toInstant().toEpochMilli()
        val e = LocalDateTime.of(endDate, et).atZone(zone).toInstant().toEpochMilli()
        if (e < s) return EventInputResult.Error("종료 시각이 시작 시각보다 빠릅니다. 날짜를 확인하세요.")
        return EventInputResult.Ok(EventSpan(false, startDate, endDate, s, e), zone.id)
    }

    /** 해당 날짜(기기 시간대 기준)에 걸치는지. */
    fun occursOn(span: EventSpan, day: LocalDate, zone: ZoneId): Boolean {
        if (span.allDay) return !day.isBefore(span.startDate) && !day.isAfter(span.endDate)
        val s = span.startInstant ?: return false
        val e = span.endInstant ?: s
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return s < dayEnd && (e > dayStart || (e == s && s >= dayStart))
    }

    fun localDateOf(instant: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()
}
