package com.miu.fold.deals

import com.miu.fold.deals.Fixtures.NOW
import com.miu.fold.deals.comparison.ComparisonCalculator
import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.comparison.SameSpecCriteria
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.observations.PriceKind
import com.miu.fold.deals.observations.ShippingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonCalculatorTest {
    private val fold6 = SameSpecCriteria("z fold6", 12, 512)

    // MKT-T06 명세 4·3 예시 — 최저 605000 중앙 620000 표본 3
    @Test fun mkt_T06_specExampleAggregate() {
        val agg = ComparisonCalculator.aggregate(Fixtures.aggregateExample(), fold6, NOW)
        assertEquals(3, agg.sampleCount)
        assertEquals(605_000L, agg.minKrw)
        assertEquals(620_000L, agg.medianKrw)
        assertEquals(625_000L, agg.maxKrw)
        assertFalse(agg.sampleInsufficient)
        assertEquals(setOf("A", "B", "C"), agg.included.map { it.first.id }.toSet())
        // Fold SE(E)는 다른 모델이라 제외 목록에도 넣지 않는다.
        val reasons = agg.excluded.associate { it.listing.id to it.reason }
        assertEquals(setOf("D", "F"), reasons.keys)
        assertTrue(reasons["D"]!!.contains("배송비 미확인"))
        assertTrue(reasons["F"]!!.contains("판매 완료"))
    }

    // 예산 650000 대비 A 잔액 45000
    @Test fun budgetRemainingFromSpecExample() {
        val a = Fixtures.aggregateExample().first { it.listing.id == "A" }
        assertEquals(45_000L, ComparisonCalculator.budgetRemaining(650_000L, a.latestPosted))
        assertEquals(-5_000L, ComparisonCalculator.budgetRemaining(600_000L, a.latestPosted))
        assertNull(ComparisonCalculator.budgetRemaining(null, a.latestPosted))
    }

    // MKT-T05 배송비 미상이면 총비용 미확인 — 무료 처리하지 않음
    @Test fun mkt_T05_unknownShippingMeansUnknownTotal() {
        val d = Fixtures.aggregateExample().first { it.listing.id == "D" }.latestPosted!!
        assertEquals(590_000L, d.amountKrw)
        assertNull(d.totalCostKrw)
        assertEquals("배송비 미확인", d.totalCostIssue)
        val sep = Fixtures.item("S", Fixtures.draft("Fold6", price = "60만", ship = ShippingState.SEPARATE)).latestPosted!!
        assertNull(sep.totalCostKrw)
        assertNull(ComparisonCalculator.budgetRemaining(700_000L, sep))
    }

    // MKT-T07 매물 1개에 가격 이력 10개여도 표본 1개
    @Test fun mkt_T07_tenObservationsCountAsOneSample() {
        val base = Fixtures.item("X", Fixtures.draft("Galaxy Z Fold6"))
        val history = (1..10).map { Fixtures.obs("X", "o$it", 700_000L - it * 1_000, NOW + it) }
        val agg = ComparisonCalculator.aggregate(listOf(base.copy(observations = history)), fold6, NOW + 100)
        assertEquals(1, agg.sampleCount)
        assertEquals(690_000L, agg.minKrw)
        assertTrue(agg.sampleInsufficient)
    }

    // 협상 제안은 최신 게시 가격을 대체하지 않는다
    @Test fun negotiationDoesNotReplacePostedPrice() {
        val obs = listOf(
            Fixtures.obs("X", "p1", 700_000, NOW),
            Fixtures.obs("X", "n1", 600_000, NOW + 10, kind = PriceKind.NEGOTIATION),
        )
        assertEquals("p1", ComparisonCalculator.latestPosted(obs)!!.id)
    }

    // MKT-T11 필수 RAM 미확인이면 집계 대신 확인 필요
    @Test fun mkt_T11_unknownRequiredRamNeedsCheck() {
        val noRam = Fixtures.item("R", Fixtures.draft("Galaxy Z Fold6", ram = "", price = "60만", ship = ShippingState.INCLUDED))
        val agg = ComparisonCalculator.aggregate(listOf(noRam), fold6, NOW)
        assertEquals(0, agg.sampleCount)
        assertTrue(agg.excluded.single().reason.contains("확인 필요"))
        assertEquals(ComparisonCalculator.SpecMatch.UNKNOWN, ComparisonCalculator.specMatchState(listOf(noRam.listing)))
    }

    // MKT-T13 판매 완료는 기본 제외, 옵션으로 포함
    @Test fun mkt_T13_soldExcludedByDefault() {
        val sold = Fixtures.item("S", Fixtures.draft("Fold6", price = "55만", ship = ShippingState.INCLUDED, status = TradeStatus.SOLD))
        assertEquals(0, ComparisonCalculator.aggregate(listOf(sold), fold6, NOW).sampleCount)
        assertEquals(1, ComparisonCalculator.aggregate(listOf(sold), fold6.copy(includeSold = true), NOW).sampleCount)
    }

    // 삭제된 매물과 오래된 관측은 집계에서 빠진다
    @Test fun deletedAndStaleAreExcluded() {
        val items = Fixtures.aggregateExample()
        val deleted = items.map { if (it.listing.id == "A") it.copy(listing = it.listing.copy(deletedAt = NOW)) else it }
        assertEquals(2, ComparisonCalculator.aggregate(deleted, fold6, NOW).sampleCount)
        val stale = ComparisonCalculator.aggregate(items, fold6.copy(freshnessHours = 24), NOW + 48 * 3_600_000L)
        assertEquals(0, stale.sampleCount)
        assertTrue(stale.excluded.any { it.reason.contains("오래됨") })
    }

    // 중앙값 짝수 개는 가운데 두 값 평균 내림
    @Test fun medianOfEvenCountFloorsAverage() {
        assertEquals(615_000L, ComparisonCalculator.median(listOf(620_000, 605_000, 610_000, 625_000)))
        assertEquals(1L, ComparisonCalculator.median(listOf(1, 2)))
        assertNull(ComparisonCalculator.median(emptyList()))
    }

    // 일괄 가격과 가격 협의는 총비용에 넣지 않는다
    @Test fun bundleAndNegotiablePricesHaveNoTotal() {
        val bundle = Fixtures.item("B", Fixtures.draft("Fold6", price = "일괄 120만", ship = ShippingState.INCLUDED)).latestPosted!!
        val nego = Fixtures.item("N", Fixtures.draft("Fold6", price = "가격 협의", ship = ShippingState.INCLUDED)).latestPosted!!
        assertNull(bundle.totalCostKrw); assertNull(nego.totalCostKrw)
        val zero = Fixtures.item("Z", Fixtures.draft("Fold6", price = "0원", ship = ShippingState.DIRECT_DEAL)).latestPosted!!
        assertEquals(0L, zero.totalCostKrw)
    }

    private fun ListingWithObservations.copy(observations: List<com.miu.fold.deals.observations.PriceObservation>) =
        ListingWithObservations(listing, observations)
}
