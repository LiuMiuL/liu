package com.miu.fold.deals.observations

/** "70만", "700,000원", "700000" 같은 가격 원문을 정규화한다. 원문은 호출자가 그대로 보존한다. */
object PriceParser {
    data class Parsed(val state: PriceState, val amountKrw: Long?, val qualifier: PriceQualifier)

    private val manPattern = Regex("""(\d+(?:\.\d+)?)\s*만(?:\s*(\d+)\s*천)?""")
    private val chunPattern = Regex("""^(\d+)\s*천""")
    private val plainNumber = Regex("""\d[\d,]*""")

    fun parse(raw: String?): Parsed {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return Parsed(PriceState.UNKNOWN, null, PriceQualifier.NONE)
        val qualifier = when {
            text.contains("보증금") -> PriceQualifier.DEPOSIT
            text.contains("일괄") -> PriceQualifier.BUNDLE
            text.contains("개당") -> PriceQualifier.PER_UNIT
            else -> PriceQualifier.NONE
        }
        val compact = text.replace(" ", "")
        val amount: Long? = manPattern.find(compact)?.let { m ->
            val man = m.groupValues[1].toBigDecimal().multiply(10_000.toBigDecimal())
            val chun = m.groupValues[2].takeIf { it.isNotEmpty() }?.toBigDecimal()?.multiply(1_000.toBigDecimal())
            val total = if (chun != null) man + chun else man
            runCatching { total.longValueExact() }.getOrNull()
        } ?: chunPattern.find(compact)?.let { it.groupValues[1].toLong() * 1_000 }
            ?: plainNumber.find(compact)?.value?.let { num ->
                // 쉼표는 천 단위 구분일 때만 허용한다.
                if (num.contains(',') && !Regex("""^\d{1,3}(,\d{3})+$""").matches(num)) null
                else num.replace(",", "").toLongOrNull()
            }
        if (amount != null) return Parsed(PriceState.KNOWN, amount, qualifier)
        if (text.contains("협의") || text.contains("제안") || text.contains("문의")) {
            return Parsed(PriceState.NEGOTIABLE, null, qualifier)
        }
        return Parsed(PriceState.UNKNOWN, null, qualifier)
    }

    fun format(amount: Long): String = "%,d원".format(amount)
}
