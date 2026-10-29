package com.miu.fold.deals.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.deals.comparison.Comparison
import com.miu.fold.deals.comparison.ComparisonCalculator
import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.data.ListingRepository
import com.miu.fold.deals.listings.FilterFacets
import com.miu.fold.deals.listings.ListingDraft
import com.miu.fold.deals.listings.ListingFilter
import com.miu.fold.deals.listings.SpecFilter
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.listings.UserRating
import com.miu.fold.deals.observations.PriceKind
import com.miu.fold.deals.observations.PriceObservation
import com.miu.fold.deals.observations.PriceParser
import com.miu.fold.deals.observations.ShippingState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** 화면 경로. SavedStateHandle에 문자열로 보관해 접기·펼치기·프로세스 재생성 후에도 복원한다. */
sealed interface Route {
    data object Board : Route
    data class Detail(val id: String) : Route
    data class Edit(val id: String?) : Route
    data object Compare : Route

    fun encode(): String = when (this) {
        Board -> "board"
        is Detail -> "detail/$id"
        is Edit -> "edit/${id ?: ""}"
        Compare -> "compare"
    }

    companion object {
        fun decode(s: String?): Route = when {
            s == null -> Board
            s.startsWith("detail/") -> Detail(s.removePrefix("detail/"))
            s.startsWith("edit/") -> Edit(s.removePrefix("edit/").ifEmpty { null })
            s == "compare" -> Compare
            else -> Board
        }
    }
}

enum class SortOrder(val label: String) { SAVED_DESC("최근 저장순"), TOTAL_ASC("총비용 낮은순"), CHECKED_DESC("최근 확인순") }

data class BoardFilter(
    val query: String = "",
    val sort: SortOrder = SortOrder.SAVED_DESC,
    val onSaleOnly: Boolean = false,
    val hideExcluded: Boolean = true,
    val spec: SpecFilter = SpecFilter(),
)

data class UiState(
    val loaded: Boolean = false,
    val all: List<ListingWithObservations> = emptyList(),
    val visible: List<ListingWithObservations> = emptyList(),
    /** 조건 값이 미확인이라 "확인 필요"로 분리된 매물(includeUnknown이면 visible 뒤에도 포함). */
    val needsCheck: List<ListingWithObservations> = emptyList(),
    /** 확인 필요 매물 id → 미확인 조건 이름. */
    val unknownFields: Map<String, List<String>> = emptyMap(),
    val facets: FilterFacets = FilterFacets(emptyList(), emptyList(), emptyList()),
    val compareIds: List<String> = emptyList(),
    val comparisons: List<Comparison> = emptyList(),
) {
    val active: List<ListingWithObservations> get() = all.filter { it.listing.deletedAt == null }
    fun find(id: String) = all.firstOrNull { it.listing.id == id }
    val compareItems: List<ListingWithObservations> get() = compareIds.mapNotNull { find(it) }.filter { it.listing.deletedAt == null }
}

class AppViewModel(private val state: SavedStateHandle, private val repo: ListingRepository) : ViewModel() {

    val route: StateFlow<Route> = state.getStateFlow(KEY_ROUTE, Route.Board.encode())
        .map { Route.decode(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, Route.decode(state[KEY_ROUTE]))

    val filter: StateFlow<BoardFilter> = combine(
        state.getStateFlow(KEY_QUERY, ""),
        state.getStateFlow(KEY_SORT, SortOrder.SAVED_DESC.name),
        state.getStateFlow(KEY_ON_SALE, false),
        state.getStateFlow(KEY_HIDE_EXCLUDED, true),
        state.getStateFlow(KEY_SPEC, SpecFilter()),
    ) { q, s, onSale, hide, spec -> BoardFilter(q, SortOrder.valueOf(s), onSale, hide, spec) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BoardFilter())

    val budgetText: StateFlow<String> = state.getStateFlow(KEY_BUDGET, "")
    val diffOnly: StateFlow<Boolean> = state.getStateFlow(KEY_DIFF_ONLY, false)
    val includeUnknownRows: StateFlow<Boolean> = state.getStateFlow(KEY_INCLUDE_UNKNOWN, true)
    val forceTable: StateFlow<Boolean> = state.getStateFlow(KEY_FORCE_TABLE, false)

    /** 편집 중 초안. 화면 재생성과 프로세스 재생성 모두에서 유지한다. */
    val draft: StateFlow<ListingDraft?> = state.getStateFlow<ListingDraft?>(KEY_DRAFT, null)
    val draftErrors: StateFlow<List<String>> = state.getStateFlow(KEY_DRAFT_ERRORS, arrayListOf<String>())

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val ui: StateFlow<UiState> = combine(
        repo.allItems, filter, state.getStateFlow(KEY_COMPARE, arrayListOf<String>()), repo.comparisons,
    ) { all, f, compareIds, comparisons -> buildUi(all, f, compareIds, comparisons, repo.now()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun buildUi(
        all: List<ListingWithObservations>, f: BoardFilter, compareIds: List<String>, comparisons: List<Comparison>, nowMs: Long,
    ): UiState {
        val base = applyBasicFilter(all, f)
        val today = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val result = ListingFilter.apply(base, f.spec, today, nowMs)
        val active = all.filter { it.listing.deletedAt == null }
        return UiState(
            loaded = true,
            all = all,
            visible = result.visible(f.spec.includeUnknown).map { it.item },
            needsCheck = result.needsCheck.map { it.item },
            unknownFields = result.needsCheck.associate { it.item.listing.id to it.unknownFields },
            facets = ListingFilter.facets(active, f.spec),
            compareIds = compareIds,
            comparisons = comparisons,
        )
    }

    private fun applyBasicFilter(all: List<ListingWithObservations>, f: BoardFilter): List<ListingWithObservations> {
        val q = f.query.trim().lowercase()
        return all.asSequence()
            .filter { it.listing.deletedAt == null }
            .filter { !f.onSaleOnly || it.listing.tradeStatus == TradeStatus.ON_SALE }
            .filter { !f.hideExcluded || it.listing.userRating != UserRating.EXCLUDED }
            .filter {
                q.isEmpty() || listOfNotNull(
                    it.listing.modelRaw, it.listing.modelCanonical, it.listing.sellerTitle, it.listing.platform,
                    it.listing.region, it.listing.memo, it.listing.color, it.listing.variant,
                ).any { v -> v.lowercase().contains(q) }
            }
            .sortedWith(
                when (f.sort) {
                    SortOrder.SAVED_DESC -> compareByDescending { it.listing.savedAt }
                    // 총비용 미확인은 0원처럼 앞에 오지 않도록 맨 뒤로 보낸다.
                    SortOrder.TOTAL_ASC -> compareBy<ListingWithObservations, Long?>(nullsLast()) { it.latestPosted?.totalCostKrw }
                    SortOrder.CHECKED_DESC -> compareByDescending<ListingWithObservations, Long?>(nullsFirst()) { it.latestPosted?.observedAt }
                },
            ).toList()
    }

    fun navigate(route: Route) {
        state[KEY_ROUTE] = route.encode()
    }

    fun back(): Boolean {
        val r = route.value
        return when (r) {
            Route.Board -> false
            is Route.Edit -> { closeEditor(); true }
            is Route.Detail, Route.Compare -> { navigate(Route.Board); true }
        }
    }

    fun setQuery(q: String) { state[KEY_QUERY] = q }
    fun setSort(s: SortOrder) { state[KEY_SORT] = s.name }
    fun setOnSaleOnly(v: Boolean) { state[KEY_ON_SALE] = v }
    fun setHideExcluded(v: Boolean) { state[KEY_HIDE_EXCLUDED] = v }
    fun updateSpec(transform: (SpecFilter) -> SpecFilter) {
        state[KEY_SPEC] = transform(filter.value.spec)
    }
    fun clearSpec() { state[KEY_SPEC] = SpecFilter() }
    fun setBudget(v: String) { state[KEY_BUDGET] = v }
    fun setDiffOnly(v: Boolean) { state[KEY_DIFF_ONLY] = v }
    fun setIncludeUnknownRows(v: Boolean) { state[KEY_INCLUDE_UNKNOWN] = v }
    fun setForceTable(v: Boolean) { state[KEY_FORCE_TABLE] = v }

    fun budgetKrw(): Long? = PriceParser.parse(budgetText.value).amountKrw

    fun toggleCompare(id: String) {
        val current = ArrayList(state.get<ArrayList<String>>(KEY_COMPARE).orEmpty())
        if (current.contains(id)) current.remove(id)
        else if (current.size >= MAX_COMPARE) { messages.trySend("비교는 최대 ${MAX_COMPARE}개까지 선택할 수 있어요."); return }
        else current.add(id)
        state[KEY_COMPARE] = current
    }

    fun clearCompare() { state[KEY_COMPARE] = arrayListOf<String>() }

    // ---- 편집 ----

    fun startNew(prefill: ListingDraft = ListingDraft()) {
        state[KEY_DRAFT] = prefill
        state[KEY_DRAFT_ERRORS] = arrayListOf<String>()
        navigate(Route.Edit(null))
    }

    /** 공유받은 텍스트. 같은 Intent를 두 번 처리하지 않도록 호출자가 한 번만 넘긴다. */
    fun onSharedText(text: String) = startNew(ListingDraft.fromSharedText(text))

    fun startEdit(id: String) {
        val item = ui.value.find(id) ?: return
        state[KEY_DRAFT] = ListingDraft.fromListing(item.listing, item.latestPosted)
        state[KEY_DRAFT_ERRORS] = arrayListOf<String>()
        navigate(Route.Edit(id))
    }

    fun updateDraft(transform: (ListingDraft) -> ListingDraft) {
        val d = draft.value ?: return
        state[KEY_DRAFT] = transform(d)
    }

    fun closeEditor() {
        val r = route.value
        state[KEY_DRAFT] = null
        state[KEY_DRAFT_ERRORS] = arrayListOf<String>()
        navigate(if (r is Route.Edit && r.id != null) Route.Detail(r.id) else Route.Board)
    }

    private var saving = false

    fun saveDraft() {
        val d = draft.value ?: return
        val r = route.value as? Route.Edit ?: return
        val errors = d.validate()
        state[KEY_DRAFT_ERRORS] = ArrayList(errors)
        if (errors.isNotEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val now = repo.now()
                if (r.id == null) {
                    val id = repo.newId()
                    val listing = d.toListing(id, null, now)
                    repo.create(listing, d.toObservationIfChanged(id, repo.newId(), null, now))
                    state[KEY_DRAFT] = null
                    navigate(Route.Detail(id))
                } else {
                    val existing = repo.get(r.id) ?: error("매물을 찾을 수 없어요.")
                    val latest = ComparisonCalculator.latestPosted(repo.observations(r.id))
                    repo.update(d.toListing(r.id, existing, now), d.toObservationIfChanged(r.id, repo.newId(), latest, now))
                    state[KEY_DRAFT] = null
                    navigate(Route.Detail(r.id))
                }
                messages.send("저장했어요.")
            } catch (e: Exception) {
                // 초안은 그대로 남겨 다시 시도할 수 있게 한다.
                messages.send("저장 실패: ${e.message ?: e::class.simpleName}. 입력 내용은 유지됩니다.")
            } finally {
                saving = false
            }
        }
    }

    // ---- 상세 ----

    fun addObservation(
        listingId: String, priceRaw: String, kind: PriceKind, shippingState: ShippingState, shippingRaw: String, note: String,
    ) {
        val parsed = PriceParser.parse(priceRaw)
        val obs = PriceObservation(
            id = repo.newId(), listingId = listingId, priceState = parsed.state, amountKrw = parsed.amountKrw,
            qualifier = parsed.qualifier, rawText = priceRaw.trim().ifEmpty { null }, shippingState = shippingState,
            shippingKrw = if (shippingState == ShippingState.SEPARATE) PriceParser.parse(shippingRaw).amountKrw else null,
            priceKind = kind, observedAt = repo.now(), note = note.trim().ifEmpty { null },
        )
        launchSafely("가격 기록을 추가했어요.") { repo.addObservation(obs) }
    }

    fun setTradeStatus(id: String, status: TradeStatus) = launchSafely(null) {
        repo.get(id)?.let { repo.update(it.copy(tradeStatus = status), null) }
    }

    fun setRating(id: String, rating: UserRating) = launchSafely(null) {
        repo.get(id)?.let { repo.update(it.copy(userRating = rating), null) }
    }

    fun delete(id: String) {
        val current = ArrayList(state.get<ArrayList<String>>(KEY_COMPARE).orEmpty())
        current.remove(id)
        state[KEY_COMPARE] = current
        navigate(Route.Board)
        launchSafely("삭제했어요. 이전 비교 기록에는 '삭제된 매물'로 남습니다.") { repo.softDelete(id) }
    }

    fun restore(id: String) = launchSafely("복원했어요.") { repo.restore(id) }

    // ---- 비교 ----

    fun saveComparison() {
        val items = ui.value.compareItems
        if (items.size < 2) { messages.trySend("2개 이상 선택해야 저장할 수 있어요."); return }
        val c = Comparison(
            id = repo.newId(),
            title = items.joinToString(" vs ") { it.listing.displayName },
            listingIds = items.joinToString("\n") { it.listing.id },
            observationIds = items.joinToString("\n") { it.latestPosted?.id.orEmpty() },
            budgetKrw = budgetKrw(),
            notes = null,
            createdAt = repo.now(),
        )
        launchSafely("비교를 저장했어요. 사용한 가격 관측이 함께 고정됩니다.") { repo.saveComparison(c) }
    }

    fun openComparison(c: Comparison) {
        val ids = c.listingIdList.filter { id -> ui.value.find(id)?.listing?.deletedAt == null }
        state[KEY_COMPARE] = ArrayList(ids.take(MAX_COMPARE))
        if (ids.size < c.listingIdList.size) messages.trySend("삭제된 매물은 현재 비교에서 빠졌어요.")
        navigate(Route.Compare)
    }

    fun deleteComparison(id: String) = launchSafely(null) { repo.deleteComparison(id) }

    private fun launchSafely(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { messages.send(it) }
            } catch (e: Exception) {
                messages.send("저장 실패: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    companion object {
        const val MAX_COMPARE = 4
        private const val KEY_ROUTE = "route"
        private const val KEY_QUERY = "query"
        private const val KEY_SORT = "sort"
        private const val KEY_ON_SALE = "onSale"
        private const val KEY_HIDE_EXCLUDED = "hideExcluded"
        private const val KEY_SPEC = "specFilter"
        private const val KEY_COMPARE = "compareIds"
        private const val KEY_BUDGET = "budget"
        private const val KEY_DIFF_ONLY = "diffOnly"
        private const val KEY_INCLUDE_UNKNOWN = "includeUnknown"
        private const val KEY_FORCE_TABLE = "forceTable"
        private const val KEY_DRAFT = "draft"
        private const val KEY_DRAFT_ERRORS = "draftErrors"
    }
}
