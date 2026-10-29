package com.miu.fold.deals.listings

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 등록일 원문을 확인 가능한 정밀도까지만 해석한다.
 * "3일 전", "5시간 전", "어제" 같은 상대 표현은 입력 시각([nowMs])이 있을 때만 날짜로 바꾼다.
 * "2주 전", "한 달 전"처럼 범위가 넓은 표현은 미확인으로 둔다.
 */
object PublishedDateParser {
    data class Parsed(val value: String?, val precision: DatePrecision)

    private val full = Regex("""(\d{4})\s*[-./년]\s*(\d{1,2})\s*[-./월]\s*(\d{1,2})""")
    private val month = Regex("""(\d{4})\s*[-./년]\s*(\d{1,2})""")
    private val relative = Regex("""(\d{1,3})\s*(분|시간|일)\s*전""")

    private val unknown = Parsed(null, DatePrecision.UNKNOWN)

    fun parse(raw: String?, nowMs: Long? = null, zone: ZoneId = ZoneId.systemDefault()): Parsed {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return unknown
        full.find(text)?.let { m ->
            val date = runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull() ?: return unknown
            return Parsed(date.toString(), DatePrecision.DAY)
        }
        month.find(text)?.let { m ->
            val ym = runCatching { YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
                ?: return unknown
            return Parsed(ym.toString(), DatePrecision.YEAR_MONTH)
        }
        if (nowMs == null) return unknown
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val compact = text.replace(" ", "")
        val date = when {
            compact.startsWith("방금") || compact.startsWith("오늘") -> now.toLocalDate()
            compact.startsWith("어제") -> now.toLocalDate().minusDays(1)
            compact.startsWith("그제") || compact.startsWith("그저께") -> now.toLocalDate().minusDays(2)
            else -> relative.find(text)?.let { m ->
                val n = m.groupValues[1].toLong()
                when (m.groupValues[2]) {
                    "분" -> now.minusMinutes(n).toLocalDate()
                    "시간" -> now.minusHours(n).toLocalDate()
                    else -> now.toLocalDate().minusDays(n)
                }
            }
        } ?: return unknown
        return Parsed(date.toString(), DatePrecision.DAY)
    }
}
