package com.miu.fold.deals

import com.miu.fold.deals.observations.PriceParser
import com.miu.fold.deals.observations.PriceQualifier
import com.miu.fold.deals.observations.PriceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParserTest {
    private fun amount(s: String) = PriceParser.parse(s).amountKrw

    // 만 단위와 쉼표·원 표기를 정규화한다
    @Test fun normalizesManAndCommaFormats() {
        assertEquals(700_000L, amount("70만"))
        assertEquals(700_000L, amount("70만원"))
        assertEquals(700_000L, amount("700,000원"))
        assertEquals(700_000L, amount("700000"))
        assertEquals(705_000L, amount("70만 5천원"))
        assertEquals(15_000L, amount("1.5만"))
        assertEquals(5_000L, amount("5천원"))
        assertEquals(1_200_000L, amount("₩1,200,000"))
    }

    // MKT-T04 미확인·협의·0원은 서로 다른 상태
    @Test fun mkt_T04_unknownNegotiableZeroAreDistinct() {
        val unknown = PriceParser.parse("")
        val negotiable = PriceParser.parse("가격 협의")
        val zero = PriceParser.parse("0원")
        assertEquals(PriceState.UNKNOWN, unknown.state); assertNull(unknown.amountKrw)
        assertEquals(PriceState.NEGOTIABLE, negotiable.state); assertNull(negotiable.amountKrw)
        assertEquals(PriceState.KNOWN, zero.state); assertEquals(0L, zero.amountKrw)
        assertEquals(PriceState.UNKNOWN, PriceParser.parse("싸게 드려요").state)
    }

    // 일괄·개당·보증금은 단순 판매가로 바꾸지 않고 구분한다
    @Test fun qualifiersAreNotPlainPrices() {
        assertEquals(PriceQualifier.BUNDLE, PriceParser.parse("일괄 120만").qualifier)
        assertEquals(PriceQualifier.PER_UNIT, PriceParser.parse("개당 3만").qualifier)
        assertEquals(PriceQualifier.DEPOSIT, PriceParser.parse("보증금 10만").qualifier)
        assertEquals(PriceQualifier.NONE, PriceParser.parse("70만").qualifier)
    }

    // 천 단위가 아닌 쉼표는 해석하지 않는다
    @Test fun nonThousandsCommaIsUnparsed() {
        assertEquals(PriceState.UNKNOWN, PriceParser.parse("1,2345").state)
    }
}
