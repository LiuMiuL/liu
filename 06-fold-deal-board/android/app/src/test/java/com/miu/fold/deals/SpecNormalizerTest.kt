package com.miu.fold.deals

import com.miu.fold.deals.listings.DatePrecision
import com.miu.fold.deals.listings.PublishedDateParser
import com.miu.fold.deals.listings.SpecNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecNormalizerTest {
    // MKT-T03 RAM 16x2는 총32 모듈16 개수2로 분리
    @Test fun mkt_T03_ramModulesSplit() {
        val r = SpecNormalizer.parseRam("16GB×2")!!
        assertEquals(32, r.totalGb); assertEquals(16, r.moduleGb); assertEquals(2, r.moduleCount)
        assertEquals(SpecNormalizer.RamSpec(32, 16, 2), SpecNormalizer.parseRam("16x2"))
        assertEquals(SpecNormalizer.RamSpec(32, 16, 2), SpecNormalizer.parseRam("16G * 2"))
        assertEquals(SpecNormalizer.RamSpec(16, null, null), SpecNormalizer.parseRam("16GB"))
        assertNull(SpecNormalizer.parseRam(""))
        assertNull(SpecNormalizer.parseRam("1TB"))
    }

    // 저장공간은 RAM과 별도 필드로 해석
    @Test fun storageParsedSeparately() {
        assertEquals(512, SpecNormalizer.parseStorageGb("512GB"))
        assertEquals(1024, SpecNormalizer.parseStorageGb("1TB"))
        assertEquals("1TB", SpecNormalizer.formatStorage(1024))
        assertNull(SpecNormalizer.parseStorageGb("모름"))
    }

    // 같은 모델의 표기 차이는 같은 키
    @Test fun sameModelSpellingsShareKey() {
        val keys = listOf("갤럭시 Z 폴드6", "Galaxy Z Fold6", "z폴드6", "Samsung Galaxy Z Fold 6", "폴드6")
            .map { SpecNormalizer.canonicalModel(it) }
        assertEquals(setOf("z fold6"), keys.toSet())
    }

    // MKT-T01 같은 계열 다른 세대·파생 모델은 합치지 않는다
    @Test fun mkt_T01_generationsAndVariantsNotMerged() {
        val fold6 = SpecNormalizer.canonicalModel("Galaxy Z Fold6")
        assertNotEquals(fold6, SpecNormalizer.canonicalModel("Galaxy Z Fold SE"))
        assertNotEquals(fold6, SpecNormalizer.canonicalModel("Galaxy Z Fold5"))
        assertNotEquals(fold6, SpecNormalizer.canonicalModel("Galaxy Z Fold6 Ultra"))
        assertNotEquals(fold6, SpecNormalizer.canonicalModel("Galaxy Z Flip6"))
        assertEquals("pixel fold", SpecNormalizer.canonicalModel("Pixel Fold"))
    }

    // 등록일은 확인 가능한 정밀도까지만 해석
    @Test fun publishedDateKeepsPrecision() {
        assertEquals(PublishedDateParser.Parsed("2026-10-28", DatePrecision.DAY), PublishedDateParser.parse("2026.10.28"))
        assertEquals(PublishedDateParser.Parsed("2026-10", DatePrecision.YEAR_MONTH), PublishedDateParser.parse("2026년 10월"))
        assertEquals(PublishedDateParser.Parsed(null, DatePrecision.UNKNOWN), PublishedDateParser.parse("3일 전"))
        assertEquals(PublishedDateParser.Parsed(null, DatePrecision.UNKNOWN), PublishedDateParser.parse("2026-02-30"))
    }
}
