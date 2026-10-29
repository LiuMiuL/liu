package com.miu.fold.deals.listings

import com.miu.fold.deals.comparison.ListingWithObservations
import java.time.LocalDate
import java.time.YearMonth

/**
 * 보드 조건 필터. 비어 있는 집합·null은 조건 없음.
 * 값이 미확인인 매물은 조건을 통과시키지 않고 "확인 필요"로 따로 센다.
 */
data class SpecFilter(
    val category: Category? = null,
    /** 정규화 모델 키 기준 정확 일치. */
    val models: Set<String> = emptySet(),
    val ramGb: Set<Int> = emptySet(),
    val storageGb: Set<Int> = emptySet(),
    /** 등록일이 오늘 기준 N일 이내(오늘-N ~ 오늘). */
    val publishedWithinDays: Int? = null,
    /** 최신 게시 가격 확인 시각이 N일(×24시간) 이내. 등록일과 독립. */
    val checkedWithinDays: Int? = null,
    /** 조건 값이 미확인인 매물도 목록에 함께 보여줄지. */
    val includeUnknown: Boolean = false,
) : java.io.Serializable {
    val activeCount: Int
        get() = listOf(category != null, models.isNotEmpty(), ramGb.isNotEmpty(), storageGb.isNotEmpty(),
            publishedWithinDays != null, checkedWithinDays != null).count { it }
}

enum class FilterMatch { PASS, FAIL, UNKNOWN }

/** 평가 결과. unknownFields는 미확인이라 판단할 수 없었던 조건 이름. */
data class FilterEvaluation(val item: ListingWithObservations, val match: FilterMatch, val unknownFields: List<String>)

data class FilterResult(val passed: List<FilterEvaluation>, val needsCheck: List<FilterEvaluation>) {
    /** includeUnknown이면 확인 필요 매물을 뒤에 붙여 보여준다. */
    fun visible(includeUnknown: Boolean): List<FilterEvaluation> = if (includeUnknown) passed + needsCheck else passed
}

/** 필터 선택지. 저장한 매물에 실제로 있는 값만 보여준다. */
data class FilterFacets(val models: List<String>, val ramGb: List<Int>, val storageGb: List<Int>)

object ListingFilter {

    fun evaluate(item: ListingWithObservations, f: SpecFilter, today: LocalDate, nowMs: Long): FilterEvaluation {
        val l = item.listing
        val unknown = mutableListOf<String>()
        var failed = false
        fun check(name: String, r: FilterMatch) {
            when (r) {
                FilterMatch.FAIL -> failed = true
                FilterMatch.UNKNOWN -> unknown += name
                FilterMatch.PASS -> Unit
            }
        }
        if (f.category != null) check("분류", if (l.category == f.category) FilterMatch.PASS else FilterMatch.FAIL)
        if (f.models.isNotEmpty()) check("기종", member(l.modelCanonical, f.models))
        if (f.ramGb.isNotEmpty()) check("RAM", member(l.ramGb, f.ramGb))
        if (f.storageGb.isNotEmpty()) check("저장공간", member(l.storageGb, f.storageGb))
        f.publishedWithinDays?.let { check("등록일", publishedWithin(l, it, today)) }
        f.checkedWithinDays?.let { days ->
            val at = item.latestPosted?.observedAt
            check("가격 확인일", when {
                at == null -> FilterMatch.UNKNOWN
                nowMs - at <= days * 86_400_000L -> FilterMatch.PASS
                else -> FilterMatch.FAIL
            })
        }
        val match = when {
            failed -> FilterMatch.FAIL
            unknown.isNotEmpty() -> FilterMatch.UNKNOWN
            else -> FilterMatch.PASS
        }
        return FilterEvaluation(item, match, unknown)
    }

    fun apply(items: List<ListingWithObservations>, f: SpecFilter, today: LocalDate, nowMs: Long): FilterResult {
        val evaluated = items.map { evaluate(it, f, today, nowMs) }
        return FilterResult(
            passed = evaluated.filter { it.match == FilterMatch.PASS },
            needsCheck = evaluated.filter { it.match == FilterMatch.UNKNOWN },
        )
    }

    /** 분류를 고르면 그 분류의 값만 선택지로 보여준다. 이미 고른 값은 사라지지 않게 포함한다. */
    fun facets(items: List<ListingWithObservations>, f: SpecFilter): FilterFacets {
        val scope = items.map { it.listing }.filter { f.category == null || it.category == f.category }
        return FilterFacets(
            models = (scope.mapNotNull { it.modelCanonical } + f.models).distinct().sorted(),
            ramGb = (scope.mapNotNull { it.ramGb } + f.ramGb).distinct().sorted(),
            storageGb = (scope.mapNotNull { it.storageGb } + f.storageGb).distinct().sorted(),
        )
    }

    private fun <T> member(value: T?, allowed: Set<T>): FilterMatch = when {
        value == null -> FilterMatch.UNKNOWN
        value in allowed -> FilterMatch.PASS
        else -> FilterMatch.FAIL
    }

    /**
     * 등록일 N일 이내. 일 단위 날짜만 확정 판단한다.
     * 연월만 아는 경우 그 달 전체가 기간 안/밖일 때만 판단하고, 걸치면 미확인이다.
     */
    fun publishedWithin(l: Listing, days: Int, today: LocalDate): FilterMatch {
        val cutoff = today.minusDays(days.toLong())
        val value = l.publishedValue ?: return FilterMatch.UNKNOWN
        return when (l.publishedPrecision) {
            DatePrecision.DAY -> {
                val d = runCatching { LocalDate.parse(value) }.getOrNull() ?: return FilterMatch.UNKNOWN
                if (d >= cutoff) FilterMatch.PASS else FilterMatch.FAIL
            }
            DatePrecision.YEAR_MONTH -> {
                val ym = runCatching { YearMonth.parse(value) }.getOrNull() ?: return FilterMatch.UNKNOWN
                when {
                    ym.atEndOfMonth() < cutoff -> FilterMatch.FAIL
                    ym.atDay(1) >= cutoff -> FilterMatch.PASS
                    else -> FilterMatch.UNKNOWN
                }
            }
            DatePrecision.UNKNOWN -> FilterMatch.UNKNOWN
        }
    }
}
