package com.miu.fold.deals.comparison

import com.miu.fold.deals.listings.Listing
import com.miu.fold.deals.listings.SpecNormalizer
import com.miu.fold.deals.observations.PriceObservation
import com.miu.fold.deals.observations.PriceParser
import com.miu.fold.deals.observations.PriceQualifier
import com.miu.fold.deals.observations.PriceState
import com.miu.fold.deals.observations.ShippingState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 비교표의 셀. value가 null이면 미확인이며 좋은 상태로 간주하지 않는다. */
data class Cell(val value: String?)

data class CompareRow(val label: String, val cells: List<Cell>) {
    val hasUnknown: Boolean get() = cells.any { it.value == null }
    val allUnknown: Boolean get() = cells.all { it.value == null }
    /** 확인된 값끼리 다르거나, 확인/미확인이 섞여 있으면 차이로 본다. */
    val differs: Boolean get() = cells.map { it.value }.toSet().size > 1
}

object CompareTable {
    const val UNKNOWN = "미확인"

    fun build(
        items: List<ListingWithObservations>,
        budgetKrw: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CompareRow> {
        val obs = items.map { it.latestPosted }
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
        fun row(label: String, f: (Listing, PriceObservation?) -> String?) =
            CompareRow(label, items.mapIndexed { i, it -> Cell(f(it.listing, obs[i])?.takeIf { v -> v.isNotBlank() }) })

        return listOf(
            row("판매가") { _, o -> o?.let(::priceText) },
            row("배송비") { _, o -> o?.let(::shippingText) },
            row("총비용") { _, o -> o?.totalCostKrw?.let(PriceParser::format) },
            row("예산 대비") { _, o -> ComparisonCalculator.budgetRemaining(budgetKrw, o)?.let(::budgetText) },
            row("모델(정규화)") { l, _ -> l.modelCanonical },
            row("모델 원문") { l, _ -> l.modelRaw },
            row("세부 변형") { l, _ -> l.variant },
            row("RAM") { l, _ -> l.ramGb?.let { ram ->
                if (l.ramModuleGb != null && l.ramModuleCount != null) "${ram}GB (${l.ramModuleGb}GB×${l.ramModuleCount})" else "${ram}GB"
            } },
            row("저장공간") { l, _ -> l.storageGb?.let(SpecNormalizer::formatStorage) },
            row("색상") { l, _ -> l.color },
            row("거래 상태") { l, _ -> l.tradeStatus.label },
            row("내부 화면") { l, _ -> l.innerScreen },
            row("외부 화면") { l, _ -> l.outerScreen },
            row("힌지") { l, _ -> l.hinge },
            row("수리 이력") { l, _ -> l.repairHistory },
            row("배터리") { l, _ -> l.battery },
            row("거래 지역") { l, _ -> l.region },
            row("플랫폼") { l, _ -> l.platform },
            row("등록일") { l, _ -> l.publishedValue },
            row("가격 확인일") { _, o -> o?.let { dateFmt.format(Instant.ofEpochMilli(it.observedAt)) } },
        )
    }

    fun priceText(o: PriceObservation): String? = when (o.priceState) {
        PriceState.UNKNOWN -> null
        PriceState.NEGOTIABLE -> "가격 협의"
        PriceState.KNOWN -> o.amountKrw?.let { amt ->
            val base = PriceParser.format(amt)
            if (o.qualifier == PriceQualifier.NONE) base else "$base (${o.qualifier.label})"
        }
    }

    fun shippingText(o: PriceObservation): String? = when (o.shippingState) {
        ShippingState.UNKNOWN -> null
        ShippingState.INCLUDED -> "포함"
        ShippingState.DIRECT_DEAL -> "직거래(없음)"
        ShippingState.SEPARATE -> o.shippingKrw?.let { "별도 ${PriceParser.format(it)}" } ?: "별도(금액 미확인)"
    }

    private fun budgetText(v: Long): String = if (v >= 0) "${PriceParser.format(v)} 남음" else "${PriceParser.format(-v)} 초과"
}
