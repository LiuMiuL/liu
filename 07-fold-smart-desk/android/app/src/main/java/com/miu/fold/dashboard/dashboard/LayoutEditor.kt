package com.miu.fold.dashboard.dashboard

data class Placement(
    val cardId: String,
    val orderKey: Int,
    val span: Int = 1,
    val visible: Boolean = true,
)

data class CardInfo(val id: String, val typeId: String)

/**
 * 한 프로필 배치에 대한 순수 편집 로직. 저장은 [com.miu.fold.dashboard.data.DashboardRepository]가 트랜잭션으로 한다.
 * 모든 연산은 새 리스트를 돌려주며 원본을 바꾸지 않으므로 '취소'는 원본을 그대로 쓰면 된다.
 */
object LayoutEditor {

    fun sorted(items: List<Placement>): List<Placement> = items.sortedBy { it.orderKey }

    fun normalize(items: List<Placement>): List<Placement> =
        sorted(items).mapIndexed { i, p -> p.copy(orderKey = i) }

    /** 앞으로(-1) / 뒤로(+1), 위로(-columns) / 아래로(+columns). 범위를 넘으면 끝에 붙는다. */
    fun move(items: List<Placement>, cardId: String, delta: Int): List<Placement> {
        val list = normalize(items).toMutableList()
        val from = list.indexOfFirst { it.cardId == cardId }
        if (from < 0 || delta == 0) return list
        val to = (from + delta).coerceIn(0, list.lastIndex)
        val item = list.removeAt(from)
        list.add(to, item)
        return list.mapIndexed { i, p -> p.copy(orderKey = i) }
    }

    fun moveTo(items: List<Placement>, cardId: String, targetIndex: Int): List<Placement> {
        val from = normalize(items).indexOfFirst { it.cardId == cardId }
        if (from < 0) return normalize(items)
        return move(items, cardId, targetIndex - from)
    }

    fun setSpan(
        items: List<Placement>,
        cardId: String,
        span: Int,
        typeId: String?,
        profile: LayoutProfile,
    ): List<Placement> {
        val valid = validSpan(span, typeId, profile)
        return items.map { if (it.cardId == cardId) it.copy(span = valid) else it }
    }

    fun setVisible(items: List<Placement>, cardId: String, visible: Boolean): List<Placement> =
        items.map { if (it.cardId == cardId) it.copy(visible = visible) else it }

    /** 지원 크기·프로필 최대 span을 벗어난 값은 허용 범위 안의 가장 가까운 값으로 바꾼다. */
    fun validSpan(span: Int, typeId: String?, profile: LayoutProfile): Int {
        val max = GridPolicy.maxSpan(profile)
        val supported = (typeId?.let { CardType.fromTypeId(it)?.supportedSpans } ?: setOf(1))
            .filter { it <= max }
            .ifEmpty { listOf(1) }
        return supported.minBy { kotlin.math.abs(it - span) }
    }

    /**
     * 새 카드가 생겼을 때 이 프로필에 배치가 없으면 맨 뒤에 추가한다.
     * 기존 사용자 배치는 순서·크기·숨김 모두 그대로 둔다(초기 배치 변경이 사용자 배치를 덮어쓰지 않음).
     * 카드 목록에 없는 배치(삭제된 카드)는 제거한다.
     */
    fun reconcile(
        items: List<Placement>,
        cards: List<CardInfo>,
        profile: LayoutProfile,
    ): List<Placement> {
        val ids = cards.map { it.id }.toSet()
        val kept = normalize(items.filter { it.cardId in ids })
        val present = kept.map { it.cardId }.toSet()
        val added = cards.filter { it.id !in present }.mapIndexed { i, c ->
            Placement(c.id, kept.size + i, validSpan(1, c.typeId, profile), true)
        }
        return kept + added
    }

    /**
     * 순서를 바꾸지 않고 앞에서부터 한 줄(열 수)씩 채운다. 남은 칸보다 큰 카드는 다음 줄로 넘긴다.
     * 같은 줄 카드는 높이를 맞춰 그린다.
     */
    fun packRows(items: List<Placement>, columns: Int): List<List<Placement>> {
        val cols = columns.coerceAtLeast(1)
        val rows = mutableListOf<MutableList<Placement>>()
        var used = cols
        for (p in items) {
            val span = p.span.coerceIn(1, cols)
            if (used + span > cols) { rows += mutableListOf<Placement>(); used = 0 }
            rows.last() += p.copy(span = span)
            used += span
        }
        return rows
    }

    /** 렌더용: 보이는 카드만, 현재 열 수로 span을 자른다. */
    fun renderable(items: List<Placement>, columns: Int): List<Placement> =
        sorted(items).filter { it.visible }.map { it.copy(span = it.span.coerceIn(1, columns.coerceAtLeast(1))) }
}

object DefaultLayouts {
    /** 접힘: 메모 → 할 일 → 타이머 → 바로가기 → 일정 → 최근 */
    val narrowOrder = listOf(
        CardType.QUICK_NOTE, CardType.TODAY_TODOS, CardType.FOCUS_TIMER,
        CardType.SHORTCUTS, CardType.TODAY_EVENTS, CardType.RECENT,
    )

    /** 펼침(2열): [메모][타이머] / [할 일][일정] / [바로가기][최근] */
    val wideOrder = listOf(
        CardType.QUICK_NOTE, CardType.FOCUS_TIMER, CardType.TODAY_TODOS,
        CardType.TODAY_EVENTS, CardType.SHORTCUTS, CardType.RECENT,
    )

    fun placements(cards: List<CardInfo>, profile: LayoutProfile): List<Placement> {
        val order = if (profile == LayoutProfile.NARROW) narrowOrder else wideOrder
        val byType = cards.groupBy { it.typeId }
        val ordered = order.flatMap { t -> byType[t.typeId].orEmpty() }
        val rest = cards.filter { c -> ordered.none { it.id == c.id } }
        return (ordered + rest).mapIndexed { i, c -> Placement(c.id, i, 1, true) }
    }
}
