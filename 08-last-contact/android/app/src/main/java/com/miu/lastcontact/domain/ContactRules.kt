package com.miu.lastcontact.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Channel(val label: String) {
    MANUAL("직접 기록"),
    KAKAO_EXPORT("카톡(내보내기)"),
    KAKAO_NOTIFICATION("카톡(알림)"),
    CALL("통화"),
    SMS("문자");

    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } }
}

enum class Direction(val label: String) {
    IN("받음"), OUT("보냄"), BOTH("주고받음"), UNKNOWN("");

    fun merge(other: Direction): Direction = when {
        this == other -> this
        this == UNKNOWN -> other
        other == UNKNOWN -> this
        else -> BOTH
    }

    companion object { fun of(s: String) = entries.firstOrNull { it.name == s } ?: UNKNOWN }
}

data class LastContact(val day: LocalDate, val channel: Channel, val atMillis: Long?)

enum class SortMode(val label: String) { DUE("연락할 때 지난 순"), OLDEST("오래된 순"), NAME("이름순") }

object ContactRules {
    fun dedupeKey(personId: String, day: LocalDate, channel: Channel) = "$personId|$day|${channel.name}"

    /** 오늘 기준 경과 일수. 기록이 없으면 null(=모름, 0으로 바꾸지 않음). */
    fun daysSince(last: LocalDate?, today: LocalDate): Long? = last?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }

    /** 목표 주기를 넘긴 일수(넘지 않았으면 0 이하). 목표가 없으면 null. */
    fun overdueBy(last: LocalDate?, goalDays: Int?, today: LocalDate): Long? {
        if (goalDays == null || goalDays <= 0) return null
        val since = daysSince(last, today) ?: return Long.MAX_VALUE // 기록이 없으면 가장 급함
        return since - goalDays
    }

    fun isOverdue(last: LocalDate?, goalDays: Int?, today: LocalDate): Boolean = (overdueBy(last, goalDays, today) ?: 0) > 0

    fun relative(last: LocalDate?, today: LocalDate): String = when (val d = daysSince(last, today)) {
        null -> "연락 기록 없음"
        0L -> "오늘"
        1L -> "어제"
        in 2..30 -> "${d}일 전"
        in 31..364 -> "${d / 30}개월 전 (${d}일)"
        else -> "${d / 365}년 이상 전 (${d}일)"
    }

    /** 정렬: DUE = 목표 초과가 큰 순 → 목표 없는 사람은 오래된 순으로 뒤에. */
    fun <T> sort(items: List<T>, mode: SortMode, today: LocalDate, name: (T) -> String, last: (T) -> LocalDate?, goal: (T) -> Int?): List<T> {
        val byName = compareBy<T> { name(it) }
        return when (mode) {
            SortMode.NAME -> items.sortedWith(byName)
            SortMode.OLDEST -> items.sortedWith(compareBy<T> { last(it) ?: LocalDate.MIN }.then(byName))
            SortMode.DUE -> items.sortedWith(
                compareByDescending<T> { overdueBy(last(it), goal(it), today) ?: Long.MIN_VALUE }
                    .thenBy { last(it) ?: LocalDate.MIN }
                    .then(byName),
            )
        }
    }

    /** 직접 기록 날짜 검증: 미래 날짜 거절. */
    fun validManualDay(day: LocalDate, today: LocalDate): Boolean = !day.isAfter(today)
}

data class ChannelLatestView(val channel: Channel, val day: LocalDate)
