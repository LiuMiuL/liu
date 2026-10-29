package com.miu.fold.deals

import com.miu.fold.deals.Fixtures.NOW
import com.miu.fold.deals.listings.DatePrecision
import com.miu.fold.deals.listings.ListingDraft
import com.miu.fold.deals.observations.PriceState
import com.miu.fold.deals.observations.ShippingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingDraftTest {
    // 빈 필드는 미확인(null)으로 저장되고 0이나 정상으로 바뀌지 않는다
    @Test fun blankFieldsAreStoredAsUnknown() {
        val l = ListingDraft(modelRaw = "Galaxy Z Fold6").toListing("id", null, NOW)
        assertNull(l.ramGb); assertNull(l.storageGb); assertNull(l.innerScreen); assertNull(l.publishedValue)
        assertEquals(DatePrecision.UNKNOWN, l.publishedPrecision)
        assertEquals(NOW, l.savedAt)
    }

    // 가격 미확인으로도 저장 가능 — 관측을 만들지 않음
    @Test fun canSaveWithoutPrice() {
        val d = ListingDraft(modelRaw = "Fold6")
        assertTrue(d.validate().isEmpty())
        assertNull(d.toObservationIfChanged("id", "o", null, NOW))
    }

    // 원문이 보존된다
    @Test fun rawTextIsPreserved() {
        val d = ListingDraft(modelRaw = "갤럭시 Z 폴드6 자급제", ram = "12기가", storage = "512GB", price = "70만 네고X")
        val l = d.toListing("id", null, NOW)
        assertEquals("갤럭시 Z 폴드6 자급제", l.modelRaw)
        assertEquals("12기가", l.ramRaw); assertEquals(12, l.ramGb)
        val o = d.toObservationIfChanged("id", "o", null, NOW)!!
        assertEquals("70만 네고X", o.rawText); assertEquals(700_000L, o.amountKrw)
    }

    // 가격을 바꾸면 새 관측 추가, 같으면 추가하지 않음
    @Test fun priceChangeAppendsObservation() {
        val first = ListingDraft(modelRaw = "Fold6", price = "70만", shippingState = ShippingState.INCLUDED)
        val o1 = first.toObservationIfChanged("id", "o1", null, NOW)!!
        assertNull(first.toObservationIfChanged("id", "o2", o1, NOW + 1))
        val o2 = first.copy(price = "65만").toObservationIfChanged("id", "o2", o1, NOW + 1)!!
        assertEquals(650_000L, o2.amountKrw)
        assertEquals("o2", o2.id)
        // 배송 상태만 바뀌어도 새 관측
        assertNotNull(first.copy(shippingState = ShippingState.UNKNOWN).toObservationIfChanged("id", "o3", o1, NOW + 2))
    }

    // 수정 화면 왕복 시 사용자가 고치지 않은 정규화 키는 자동값 유지
    @Test fun canonicalKeyRoundTrip() {
        val l = ListingDraft(modelRaw = "Galaxy Z Fold6").toListing("id", null, NOW)
        val back = ListingDraft.fromListing(l, null)
        assertEquals("", back.modelCanonical)
        val custom = ListingDraft(modelRaw = "폴드6", modelCanonical = "Z Fold6 Ultra").toListing("id", null, NOW)
        assertEquals("z fold6 ultra", custom.modelCanonical)
        assertEquals("Z Fold6 Ultra".lowercase(), ListingDraft.fromListing(custom, null).modelCanonical.lowercase())
    }

    // http(s) 외 링크와 해석 불가 사양은 저장 전에 거절
    @Test fun rejectsNonWebUrlsAndUnparseableSpecs() {
        assertTrue(ListingDraft(modelRaw = "x", url = "javascript:alert(1)").validate().isNotEmpty())
        assertTrue(ListingDraft(modelRaw = "x", url = "intent://foo").validate().isNotEmpty())
        assertTrue(ListingDraft(modelRaw = "x", ram = "많음").validate().isNotEmpty())
        assertTrue(ListingDraft().validate().isNotEmpty())
        assertTrue(ListingDraft(url = "https://m.bunjang.co.kr/products/1").validate().isEmpty())
    }

    // 공유 텍스트에서 링크·제목·플랫폼을 분리
    @Test fun sharedTextSplitsUrlTitlePlatform() {
        val d = ListingDraft.fromSharedText("갤럭시 Z 폴드6 512GB 팝니다\nhttps://m.bunjang.co.kr/products/123456 ")
        assertEquals("https://m.bunjang.co.kr/products/123456", d.url)
        assertEquals("번개장터", d.platform)
        assertEquals("갤럭시 Z 폴드6 512GB 팝니다", d.sellerTitle)
        val noUrl = ListingDraft.fromSharedText("그냥 메모")
        assertEquals("", noUrl.url)
        assertEquals("그냥 메모", noUrl.sellerTitle)
    }

    // MKT-T08 저장은 오늘이어도 등록일 미상은 등록일 미확인
    @Test fun mkt_T08_unknownPublishedDateStaysUnknown() {
        val l = ListingDraft(modelRaw = "Fold6", published = "방금 전").toListing("id", null, NOW)
        assertEquals(NOW, l.savedAt)
        assertNull(l.publishedValue)
        assertEquals("방금 전", l.publishedRaw)
        val p = ListingDraft(modelRaw = "Fold6", price = "가격협의").toObservationIfChanged("id", "o", null, NOW)!!
        assertEquals(PriceState.NEGOTIABLE, p.priceState)
    }
}
