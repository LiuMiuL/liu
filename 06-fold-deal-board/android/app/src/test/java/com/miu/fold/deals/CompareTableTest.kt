package com.miu.fold.deals

import com.miu.fold.deals.comparison.CompareTable
import com.miu.fold.deals.comparison.ComparisonCalculator
import com.miu.fold.deals.observations.ShippingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CompareTableTest {
    private fun rows(vararg items: com.miu.fold.deals.comparison.ListingWithObservations) =
        CompareTable.build(items.toList(), 650_000L, ZoneOffset.UTC).associateBy { it.label }

    // MKT-T02 RAM16 저장512 vs RAM12 저장512 는 RAM 차이 표시
    @Test fun mkt_T02_ramDifferenceIsMarked() {
        val a = Fixtures.item("a", Fixtures.draft("Galaxy Z Fold6", ram = "16GB", price = "70만", ship = ShippingState.INCLUDED))
        val b = Fixtures.item("b", Fixtures.draft("Galaxy Z Fold6", ram = "12GB", price = "65만", ship = ShippingState.INCLUDED))
        val r = rows(a, b)
        assertTrue(r.getValue("RAM").differs)
        assertFalse(r.getValue("저장공간").differs)
        assertEquals(listOf("16GB", "12GB"), r.getValue("RAM").cells.map { it.value })
        assertEquals(ComparisonCalculator.SpecMatch.DIFFERENT, ComparisonCalculator.specMatchState(listOf(a.listing, b.listing)))
    }

    // 미확인 값은 null 셀이며 확인값과 섞이면 차이로 표시
    @Test fun unknownCellsStayNullAndCountAsDifference() {
        val a = Fixtures.item("a", Fixtures.draft("Fold6", price = "70만", ship = ShippingState.UNKNOWN))
        val b = Fixtures.item("b", Fixtures.draft("Fold6", price = "70만", ship = ShippingState.INCLUDED))
        val r = rows(a, b)
        val total = r.getValue("총비용")
        assertNull(total.cells[0].value)
        assertEquals("700,000원", total.cells[1].value)
        assertTrue(total.differs)
        assertTrue(total.hasUnknown)
        assertEquals("50,000원 초과", r.getValue("예산 대비").cells[1].value)
        assertNull(r.getValue("예산 대비").cells[0].value)
        assertTrue(r.getValue("내부 화면").allUnknown)
    }

    // PC 부품 RAM 모듈 구성이 표에 보존된다
    @Test fun pcRamModulesArePreserved() {
        val a = Fixtures.item("a", Fixtures.draft("DDR5 5600", ram = "16GB×2"))
        assertEquals("32GB (16GB×2)", rows(a).getValue("RAM").cells[0].value)
    }
}
