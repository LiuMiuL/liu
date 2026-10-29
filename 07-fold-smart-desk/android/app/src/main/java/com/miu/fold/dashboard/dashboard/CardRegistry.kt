package com.miu.fold.dashboard.dashboard

/**
 * 카드 종류 레지스트리. 카드 인스턴스(DashboardCard.id)와 종류(typeId)는 분리되어 있다.
 * 첫 버전은 종류당 1개(maxInstances)만 허용한다.
 */
enum class CardType(
    val typeId: String,
    val defaultTitle: String,
    val supportedSpans: Set<Int>,
    val maxInstances: Int = 1,
) {
    QUICK_NOTE("quick_note", "빠른 메모", setOf(1, 2)),
    TODAY_TODOS("today_todos", "오늘 할 일", setOf(1, 2)),
    FOCUS_TIMER("focus_timer", "집중 타이머", setOf(1, 2)),
    SHORTCUTS("shortcuts", "바로가기", setOf(1, 2, 3)),
    TODAY_EVENTS("today_events", "오늘 일정", setOf(1, 2)),
    RECENT("recent", "최근 항목", setOf(1, 2));

    companion object {
        /** 알 수 없는 typeId는 null. 호출부는 크래시 대신 '지원하지 않는 카드'로 표시해야 한다. */
        fun fromTypeId(typeId: String): CardType? = entries.firstOrNull { it.typeId == typeId }
    }
}

enum class LayoutProfile(val key: String, val label: String) {
    NARROW("narrow", "좁은 화면"),
    WIDE("wide", "넓은 화면");

    companion object {
        fun fromKey(key: String): LayoutProfile? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 현재 창 가용 너비(dp, 탐색 레일 등 제외)로 열 수를 고른다. 기기명/물리 해상도는 쓰지 않는다.
 * 카드 한 열의 최소 너비는 [MIN_CARD_WIDTH_DP]. '펼침 = 무조건 3열'이 아니다.
 */
object GridPolicy {
    const val MIN_CARD_WIDTH_DP = 260
    private const val GUTTER_DP = 16

    fun columnsFor(availableWidthDp: Int, windowWidthDp: Int = availableWidthDp): Int {
        if (windowWidthDp < 600) return 1
        val maxCols = if (windowWidthDp >= 1200) 3 else 2
        for (cols in maxCols downTo 2) {
            val per = (availableWidthDp - GUTTER_DP * (cols + 1)) / cols
            if (per >= MIN_CARD_WIDTH_DP) return cols
        }
        return 1
    }

    fun profileFor(columns: Int): LayoutProfile = if (columns <= 1) LayoutProfile.NARROW else LayoutProfile.WIDE

    /** 프로필이 허용하는 최대 span. 실제 렌더 시에는 현재 열 수로 한 번 더 자른다. */
    fun maxSpan(profile: LayoutProfile): Int = if (profile == LayoutProfile.NARROW) 1 else 3
}
