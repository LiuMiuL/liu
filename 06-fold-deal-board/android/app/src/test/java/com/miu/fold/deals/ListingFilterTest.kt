package com.miu.fold.deals

import com.miu.fold.deals.Fixtures.NOW
import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.listings.Category
import com.miu.fold.deals.listings.DatePrecision
import com.miu.fold.deals.listings.FilterMatch
import com.miu.fold.deals.listings.ListingDraft
import com.miu.fold.deals.listings.ListingFilter
import com.miu.fold.deals.listings.PublishedDateParser
import com.miu.fold.deals.listings.SpecFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ListingFilterTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val now = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun item(id: String, model: String, ram: String = "12GB", storage: String = "512GB", published: String = "",
                     category: Category = Category.PHONE, price: String = "70만"): ListingWithObservations {
        val d = ListingDraft(modelRaw = model, ram = ram, storage = storage, published = published, category = category, price = price)
        val l = d.toListing(id, null, now)
        return ListingWithObservations(l, listOfNotNull(d.toObservationIfChanged(id, "$id-o", null, now)))
    }

    private fun ids(f: SpecFilter, vararg items: ListingWithObservations) =
        ListingFilter.apply(items.toList(), f, today, now).let { r -> r.passed.map { it.item.listing.id } to r.needsCheck.map { it.item.listing.id } }

    // 일주일 안 등록: 오늘-7일까지 통과, 8일 전은 제외, 등록일 미상은 확인 필요(MKT-T08)
    @Test fun mkt_T08_publishedWithinWeekSeparatesUnknown() {
        val result = ids(
            SpecFilter(publishedWithinDays = 7),
            item("today", "Fold6", published = "2026-10-30"),
            item("edge", "Fold6", published = "2026-10-23"),
            item("old", "Fold6", published = "2026-10-22"),
            item("unknown", "Fold6", published = ""),
        )
        assertEquals(listOf("today", "edge"), result.first)
        assertEquals(listOf("unknown"), result.second)
    }

    // 연월만 아는 등록일은 그 달 전체가 기간 안/밖일 때만 판단
    @Test fun monthPrecisionIsDecidedOnlyWhenWholeMonthFits() {
        val f = SpecFilter(publishedWithinDays = 7)
        assertEquals(FilterMatch.FAIL, ListingFilter.evaluate(item("a", "x", published = "2026년 9월"), f, today, now).match)
        assertEquals(FilterMatch.UNKNOWN, ListingFilter.evaluate(item("b", "x", published = "2026년 10월"), f, today, now).match)
        assertEquals(FilterMatch.PASS, ListingFilter.evaluate(item("c", "x", published = "2026-10"), SpecFilter(publishedWithinDays = 30), today, now).match)
    }

    // 휴대폰 기종·저장공간 조건: 정확 일치, 미확인 용량은 확인 필요
    @Test fun phoneModelAndStorageFilter() {
        val result = ids(
            SpecFilter(category = Category.PHONE, models = setOf("z fold6"), storageGb = setOf(512, 1024)),
            item("f6-512", "갤럭시 Z 폴드6", storage = "512GB"),
            item("f6-1t", "Galaxy Z Fold6", storage = "1TB"),
            item("f6-256", "Galaxy Z Fold6", storage = "256GB"),
            item("fse", "Galaxy Z Fold SE", storage = "512GB"),
            item("f6-unk", "Galaxy Z Fold6", storage = ""),
            item("ssd", "Galaxy Z Fold6", storage = "512GB", category = Category.PC_PART),
        )
        assertEquals(listOf("f6-512", "f6-1t"), result.first)
        assertEquals(listOf("f6-unk"), result.second)
    }

    // 조건 하나라도 확실히 불일치면 다른 조건이 미확인이어도 제외(확인 필요에 넣지 않음)
    @Test fun knownMismatchWinsOverUnknown() {
        val r = ListingFilter.evaluate(item("x", "Galaxy Z Fold5", storage = ""), SpecFilter(models = setOf("z fold6"), storageGb = setOf(512)), today, now)
        assertEquals(FilterMatch.FAIL, r.match)
        val u = ListingFilter.evaluate(item("y", "Galaxy Z Fold6", ram = "", storage = ""), SpecFilter(ramGb = setOf(12), storageGb = setOf(512)), today, now)
        assertEquals(listOf("RAM", "저장공간"), u.unknownFields)
    }

    // 가격 확인일 조건은 등록일과 독립, 가격 기록이 없으면 확인 필요
    @Test fun checkedDateIsIndependentOfPublished() {
        val f = SpecFilter(checkedWithinDays = 7)
        val fresh = item("fresh", "Fold6", published = "2026-01-01")
        val stale = ListingWithObservations(fresh.listing.copy(id = "stale"),
            listOf(fresh.observations.single().copy(listingId = "stale", observedAt = now - 8 * 86_400_000L)))
        val noPrice = item("noPrice", "Fold6", price = "")
        val result = ids(f, fresh, stale, noPrice)
        assertEquals(listOf("fresh"), result.first)
        assertEquals(listOf("noPrice"), result.second)
    }

    // 선택지는 저장한 값만, 분류를 고르면 그 분류 값만
    @Test fun facetsComeFromSavedValues() {
        val items = listOf(item("a", "Galaxy Z Fold6", ram = "12GB"), item("b", "Galaxy Z Fold5", ram = "16GB×2", category = Category.PC_PART))
        val all = ListingFilter.facets(items, SpecFilter())
        assertEquals(listOf("z fold5", "z fold6"), all.models)
        assertEquals(listOf(12, 32), all.ramGb)
        assertEquals(listOf("z fold6"), ListingFilter.facets(items, SpecFilter(category = Category.PHONE)).models)
    }

    // 상대 날짜 표현 해석
    @Test fun relativeDatesNeedReferenceTime() {
        val ref = LocalDate.of(2026, 10, 30).atTime(1, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        fun p(s: String) = PublishedDateParser.parse(s, ref, ZoneOffset.UTC)
        assertEquals("2026-10-27", p("3일 전").value)
        assertEquals("2026-10-29", p("어제").value)
        assertEquals("2026-10-30", p("방금 전").value)
        assertEquals("2026-10-29", p("5시간 전").value) // 01시 기준 5시간 전은 전날
        assertEquals(DatePrecision.UNKNOWN, p("2주 전").precision)
        assertEquals(DatePrecision.UNKNOWN, PublishedDateParser.parse("3일 전").precision)
        check(NOW > 0)
    }
}
