package com.miu.fold.dashboard

import com.miu.fold.dashboard.dashboard.CardInfo
import com.miu.fold.dashboard.dashboard.CardType
import com.miu.fold.dashboard.dashboard.DefaultLayouts
import com.miu.fold.dashboard.dashboard.GridPolicy
import com.miu.fold.dashboard.dashboard.LayoutEditor
import com.miu.fold.dashboard.dashboard.LayoutProfile
import com.miu.fold.dashboard.dashboard.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEditorTest {
    private val cards = CardType.entries.mapIndexed { i, t -> CardInfo("c$i", t.typeId) }
    private fun ids(list: List<Placement>) = LayoutEditor.sorted(list).map { it.cardId }

    @Test fun defaultsDifferPerProfile() {
        val narrow = DefaultLayouts.placements(cards, LayoutProfile.NARROW)
        val wide = DefaultLayouts.placements(cards, LayoutProfile.WIDE)
        assertEquals(cards.size, narrow.size)
        assertEquals(cards.size, wide.size)
        assertTrue(ids(narrow) != ids(wide))
    }

    @Test fun moveForwardBackwardAndByRow() {
        val base = DefaultLayouts.placements(cards, LayoutProfile.NARROW)
        val first = ids(base)[0]
        val moved = LayoutEditor.move(base, first, 1)
        assertEquals(first, ids(moved)[1])
        val back = LayoutEditor.move(moved, first, -1)
        assertEquals(ids(base), ids(back))
        // 범위를 넘으면 끝에 붙는다
        assertEquals(first, ids(LayoutEditor.move(base, first, 100)).last())
        assertEquals(first, ids(LayoutEditor.move(base, first, -100)).first())
        // 2열에서 '아래로' = +2
        assertEquals(first, ids(LayoutEditor.move(base, first, 2))[2])
    }

    @Test fun operationsDoNotMutateOriginal() {
        val base = DefaultLayouts.placements(cards, LayoutProfile.WIDE)
        val copy = base.toList()
        LayoutEditor.move(base, base[0].cardId, 3)
        LayoutEditor.setVisible(base, base[0].cardId, false)
        assertEquals(copy, base)
    }

    @Test fun spanValidation() {
        val note = CardType.QUICK_NOTE.typeId
        assertEquals(1, LayoutEditor.validSpan(2, note, LayoutProfile.NARROW))
        assertEquals(2, LayoutEditor.validSpan(2, note, LayoutProfile.WIDE))
        assertEquals(2, LayoutEditor.validSpan(3, note, LayoutProfile.WIDE)) // 메모는 3열 미지원
        assertEquals(3, LayoutEditor.validSpan(3, CardType.SHORTCUTS.typeId, LayoutProfile.WIDE))
        assertEquals(1, LayoutEditor.validSpan(3, "unknown_type", LayoutProfile.WIDE))
        assertEquals(1, LayoutEditor.validSpan(0, note, LayoutProfile.WIDE))
    }

    @Test fun renderableClampsSpanAndHidesHidden() {
        val base = DefaultLayouts.placements(cards, LayoutProfile.WIDE)
        val edited = LayoutEditor.setVisible(LayoutEditor.setSpan(base, "c3", 3, CardType.SHORTCUTS.typeId, LayoutProfile.WIDE), "c0", false)
        val r = LayoutEditor.renderable(edited, 2)
        assertFalse(r.any { it.cardId == "c0" })
        assertEquals(2, r.first { it.cardId == "c3" }.span)
    }

    @Test fun reconcileKeepsUserLayoutAndAppendsNew() {
        val user = LayoutEditor.move(DefaultLayouts.placements(cards.take(3), LayoutProfile.WIDE), "c2", -2)
            .let { LayoutEditor.setVisible(it, "c1", false) }
        val r = LayoutEditor.reconcile(user, cards, LayoutProfile.WIDE)
        assertEquals(ids(user), ids(r).take(3))
        assertFalse(r.first { it.cardId == "c1" }.visible)
        assertEquals(cards.size, r.size)
        // 삭제된 카드의 배치는 제거
        val removed = LayoutEditor.reconcile(r, cards.drop(1), LayoutProfile.WIDE)
        assertFalse(removed.any { it.cardId == "c0" })
    }

    @Test fun packRowsKeepsOrderAndWraps() {
        val items = listOf(Placement("a", 0, 1), Placement("b", 1, 2), Placement("c", 2, 1), Placement("d", 3, 1))
        val rows = LayoutEditor.packRows(items, 2)
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c", "d")), rows.map { r -> r.map { it.cardId } })
        // 1열이면 한 줄에 하나, span은 1로 줄어든다
        val one = LayoutEditor.packRows(items, 1)
        assertEquals(4, one.size)
        assertTrue(one.flatten().all { it.span == 1 })
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), LayoutEditor.packRows(items, 3).map { r -> r.map { it.cardId } })
        assertTrue(LayoutEditor.packRows(emptyList(), 2).isEmpty())
    }

    @Test fun gridPolicyUsesAvailableWidthNotDevice() {
        assertEquals(1, GridPolicy.columnsFor(360))
        assertEquals(1, GridPolicy.columnsFor(520, 600)) // 600dp 창 - 레일 80dp: 2열 최소 너비 미달
        assertEquals(2, GridPolicy.columnsFor(600, 680))
        assertEquals(2, GridPolicy.columnsFor(760, 840))
        assertEquals(2, GridPolicy.columnsFor(1000, 1080))
        assertEquals(3, GridPolicy.columnsFor(1120, 1200))
        assertEquals(LayoutProfile.NARROW, GridPolicy.profileFor(1))
        assertEquals(LayoutProfile.WIDE, GridPolicy.profileFor(3))
    }
}
