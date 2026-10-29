package com.miu.fold.deals.listings

import java.time.LocalDate
import java.time.YearMonth

/** 등록일 원문을 확인 가능한 정밀도까지만 해석한다. "3일 전" 같은 상대 표현은 미확인으로 둔다. */
object PublishedDateParser {
    data class Parsed(val value: String?, val precision: DatePrecision)

    private val full = Regex("""(\d{4})\s*[-./년]\s*(\d{1,2})\s*[-./월]\s*(\d{1,2})""")
    private val month = Regex("""(\d{4})\s*[-./년]\s*(\d{1,2})""")

    fun parse(raw: String?): Parsed {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return Parsed(null, DatePrecision.UNKNOWN)
        full.find(text)?.let { m ->
            val date = runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull() ?: return Parsed(null, DatePrecision.UNKNOWN)
            return Parsed(date.toString(), DatePrecision.DAY)
        }
        month.find(text)?.let { m ->
            val ym = runCatching { YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
                ?: return Parsed(null, DatePrecision.UNKNOWN)
            return Parsed(ym.toString(), DatePrecision.YEAR_MONTH)
        }
        return Parsed(null, DatePrecision.UNKNOWN)
    }
}
