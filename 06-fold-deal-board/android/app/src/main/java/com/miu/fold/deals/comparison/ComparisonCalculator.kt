package com.miu.fold.deals.comparison

import com.miu.fold.deals.listings.Listing
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.observations.PriceKind
import com.miu.fold.deals.observations.PriceObservation
import com.miu.fold.deals.observations.PriceParser

/** 매물과 그 매물의 가격 관측 목록. */
data class ListingWithObservations(val listing: Listing, val observations: List<PriceObservation>) {
    /** 유효한 최신 게시 가격 관측 1개. 협상·거래가는 포함하지 않는다. */
    val latestPosted: PriceObservation? get() = ComparisonCalculator.latestPosted(observations)
}

/** 동일 조건 집계 기준. null 필드는 조건 없음. */
data class SameSpecCriteria(
    val modelCanonical: String?,
    val ramGb: Int?,
    val storageGb: Int?,
    val includeSold: Boolean = false,
    /** 관측값 최신성(시간). null이면 제한 없음. */
    val freshnessHours: Long? = null,
)

data class Exclusion(val listing: Listing, val reason: String)

/** 저장한 매물 기준 집계. 시장 전체 시세가 아니다. */
data class Aggregate(
    val sampleCount: Int,
    val minKrw: Long?,
    val medianKrw: Long?,
    val maxKrw: Long?,
    val included: List<Pair<Listing, PriceObservation>>,
    val excluded: List<Exclusion>,
) {
    /** 초기 정책: 표본 3개 미만은 표본 부족. 3개 이상도 시장 대표성은 보장하지 않는다. */
    val sampleInsufficient: Boolean get() = sampleCount < 3
}

object ComparisonCalculator {

    fun latestPosted(observations: List<PriceObservation>): PriceObservation? =
        observations.filter { it.priceKind == PriceKind.POSTED }
            .maxWithOrNull(compareBy<PriceObservation> { it.observedAt }.thenBy { it.id })

    /**
     * 중앙값. 짝수 개면 가운데 두 값의 평균을 원 단위 내림으로 계산한다.
     */
    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else Math.floorDiv(sorted[mid - 1] + sorted[mid], 2L)
    }

    /**
     * 같은 조건을 만족하는 저장 매물의 총비용 집계.
     * 한 매물당 최신 게시 가격 1개만 표본으로 쓴다.
     */
    fun aggregate(items: List<ListingWithObservations>, criteria: SameSpecCriteria, nowMs: Long): Aggregate {
        val included = mutableListOf<Pair<Listing, PriceObservation>>()
        val excluded = mutableListOf<Exclusion>()
        for (item in items) {
            val l = item.listing
            if (l.deletedAt != null) continue
            val reason = mismatchReason(l, criteria) ?: run {
                val obs = item.latestPosted
                when {
                    obs == null -> "게시 가격 기록 없음"
                    criteria.freshnessHours != null && nowMs - obs.observedAt > criteria.freshnessHours * 3_600_000L ->
                        "가격 확인일이 기준보다 오래됨"
                    obs.totalCostIssue != null -> "총비용 미확인: ${obs.totalCostIssue}"
                    else -> {
                        included += l to obs
                        null
                    }
                }
            }
            if (reason != null && reason != SKIP) excluded += Exclusion(l, reason)
        }
        val costs = included.map { it.second.totalCostKrw!! }
        return Aggregate(
            sampleCount = costs.size,
            minKrw = costs.minOrNull(),
            medianKrw = median(costs),
            maxKrw = costs.maxOrNull(),
            included = included,
            excluded = excluded,
        )
    }

    private const val SKIP = "\u0000skip"

    /** 다른 모델은 제외 사유로 남기지 않고 건너뛴다. 조건 필드가 미확인이면 "확인 필요"로 제외한다. */
    private fun mismatchReason(l: Listing, c: SameSpecCriteria): String? {
        if (c.modelCanonical != null) {
            if (l.modelCanonical == null) return "모델 미확인(확인 필요)"
            if (l.modelCanonical != c.modelCanonical) return SKIP
        }
        if (c.ramGb != null) {
            if (l.ramGb == null) return "RAM 미확인(확인 필요)"
            if (l.ramGb != c.ramGb) return SKIP
        }
        if (c.storageGb != null) {
            if (l.storageGb == null) return "저장공간 미확인(확인 필요)"
            if (l.storageGb != c.storageGb) return SKIP
        }
        if (!c.includeSold && l.tradeStatus == TradeStatus.SOLD) return "판매 완료(현재 구매 후보 아님)"
        if (l.tradeStatus == TradeStatus.UNVERIFIABLE) return "거래 상태 확인 불가"
        return null
    }

    /** 예산 대비 잔액. 총비용을 모르면 null. 음수면 예산 초과. */
    fun budgetRemaining(budgetKrw: Long?, obs: PriceObservation?): Long? {
        val cost = obs?.totalCostKrw ?: return null
        return budgetKrw?.minus(cost)
    }

    /** 비교 대상 매물들의 핵심 사양이 모두 확인되고 서로 같은지. */
    fun specMatchState(listings: List<Listing>): SpecMatch {
        val keys = listings.map { Triple(it.modelCanonical, it.ramGb, it.storageGb) }
        if (keys.any { it.first == null || it.second == null || it.third == null }) return SpecMatch.UNKNOWN
        return if (keys.toSet().size == 1) SpecMatch.SAME else SpecMatch.DIFFERENT
    }

    enum class SpecMatch(val label: String) {
        SAME("모델·RAM·용량 일치"), DIFFERENT("사양이 서로 다름 — 같은 조건 비교 아님"), UNKNOWN("일부 사양 미확인 — 확인 필요")
    }

    fun formatKrw(v: Long?): String = v?.let { PriceParser.format(it) } ?: "미확인"
}
