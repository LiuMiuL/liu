package com.miu.fold.dashboard.todos

import java.time.LocalDate

enum class TodoBucket(val label: String) {
    OVERDUE("기한 지남"),
    TODAY("오늘"),
    NO_DATE("날짜 없음"),
    UPCOMING("예정"),
    DONE("완료"),
}

enum class TodoFilter(val label: String) {
    TODAY("오늘"), UPCOMING("예정"), OVERDUE("기한 지남"), NO_DATE("날짜 없음"), DONE("완료"), ALL("전체")
}

/**
 * 마감 '날짜'만 있는 할 일은 LocalDate로 비교한다(UTC 자정 치환 금지).
 * 오늘은 호출 시점의 기기 시간대 기준으로 매번 계산해 넘겨받는다.
 */
object TodoRules {
    fun bucket(dueDate: LocalDate?, done: Boolean, today: LocalDate): TodoBucket = when {
        done -> TodoBucket.DONE
        dueDate == null -> TodoBucket.NO_DATE
        dueDate.isBefore(today) -> TodoBucket.OVERDUE
        dueDate.isEqual(today) -> TodoBucket.TODAY
        else -> TodoBucket.UPCOMING
    }

    fun matches(filter: TodoFilter, b: TodoBucket): Boolean = when (filter) {
        TodoFilter.ALL -> true
        TodoFilter.TODAY -> b == TodoBucket.TODAY || b == TodoBucket.OVERDUE
        TodoFilter.UPCOMING -> b == TodoBucket.UPCOMING
        TodoFilter.OVERDUE -> b == TodoBucket.OVERDUE
        TodoFilter.NO_DATE -> b == TodoBucket.NO_DATE
        TodoFilter.DONE -> b == TodoBucket.DONE
    }

    /** 홈 '오늘 할 일' 카드: 기한 지남 + 오늘 + 날짜 없음 중 미완료. */
    fun onHomeCard(b: TodoBucket): Boolean = b == TodoBucket.OVERDUE || b == TodoBucket.TODAY || b == TodoBucket.NO_DATE

    fun parseDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
