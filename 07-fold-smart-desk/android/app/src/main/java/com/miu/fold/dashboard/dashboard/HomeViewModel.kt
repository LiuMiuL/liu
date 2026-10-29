package com.miu.fold.dashboard.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.data.DashboardCardEntity
import com.miu.fold.dashboard.notes.NoteRules
import com.miu.fold.dashboard.recent.RecentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 편집 중인 프로필은 고정된다. 접기/펼치기로 현재 프로필이 바뀌어도 이 초안은 원래 프로필에만 저장된다. */
data class LayoutEdit(val profile: LayoutProfile, val original: List<Placement>, val draft: List<Placement>) {
    val changed: Boolean get() = LayoutEditor.normalize(original) != LayoutEditor.normalize(draft)
}

data class RecentEntry(val type: RecentType?, val typeKey: String, val id: String, val title: String?, val openedAt: Long)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * 기본 카드 생성(ensureDefaults)이 끝난 뒤에만 배치를 구독한다.
     * 먼저 구독하면 첫 실행에 빈 목록이 그려져 '카드가 없습니다'가 잘못 보일 수 있다.
     */
    val state: StateFlow<DashboardState?> = _ready.filter { it }.flatMapLatest { c.dashboard.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _edit = MutableStateFlow<LayoutEdit?>(null)
    val edit: StateFlow<LayoutEdit?> = _edit.asStateFlow()

    val recent: StateFlow<List<RecentEntry>> = combine(c.recent.items, c.notes.active, c.todos.all, c.events.all, c.shortcuts.all) { items, notes, todos, events, shortcuts ->
        items.map { r ->
            val type = RecentType.fromKey(r.type)
            val title = when (type) {
                RecentType.NOTE -> notes.firstOrNull { it.id == r.localId }?.let { NoteRules.suggestedTitle(it.title, it.body) }
                RecentType.TODO -> todos.firstOrNull { it.id == r.localId }?.title
                RecentType.EVENT -> events.firstOrNull { it.id == r.localId }?.title
                RecentType.SHORTCUT -> shortcuts.firstOrNull { it.id == r.localId }?.label
                null -> null
            }
            RecentEntry(type, r.type, r.localId, title, r.openedAt)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // 실패해도 홈은 열리게 한다(빈 상태 안내 + 편집으로 카드 추가 가능).
            runCatching { c.dashboard.ensureDefaults() }
            _ready.value = true
        }
    }

    fun startEdit(profile: LayoutProfile) {
        if (_edit.value != null) return
        val current = state.value?.layouts?.get(profile).orEmpty()
        _edit.value = LayoutEdit(profile, current, current)
    }

    private fun updateDraft(f: (List<Placement>) -> List<Placement>) {
        _edit.value = _edit.value?.let { it.copy(draft = f(it.draft)) }
    }

    fun move(cardId: String, delta: Int) = updateDraft { LayoutEditor.move(it, cardId, delta) }

    /** 드래그 중 손가락 아래 카드 자리로 옮긴다(초안만 바뀜, 저장 전까지 DB 불변). */
    fun moveTo(cardId: String, index: Int) = updateDraft { LayoutEditor.moveTo(it, cardId, index) }

    fun setSpan(cardId: String, span: Int, typeId: String?) {
        val p = _edit.value?.profile ?: return
        updateDraft { LayoutEditor.setSpan(it, cardId, span, typeId, p) }
    }

    fun setVisible(cardId: String, visible: Boolean) = updateDraft { LayoutEditor.setVisible(it, cardId, visible) }

    fun saveEdit() = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        c.dashboard.saveLayout(e.profile, e.draft)
        _edit.value = null
    }

    /** 취소: DB를 건드리지 않았으므로 원래 배치가 그대로 남는다. */
    fun cancelEdit() { _edit.value = null }

    fun resetLayout(profile: LayoutProfile) = viewModelScope.launch {
        if (_edit.value?.profile == profile) _edit.value = null
        c.dashboard.resetLayout(profile)
    }

    fun addCard(type: CardType) = viewModelScope.launch {
        c.dashboard.addCard(type)
        // 편집 중이면 새 카드를 초안에도 반영한다.
        _edit.value?.let { e ->
            val s = state.value ?: return@let
            val infos = s.cards.map { CardInfo(it.id, it.type) }
            _edit.value = e.copy(draft = LayoutEditor.reconcile(e.draft, infos, e.profile))
        }
    }

    fun deleteCard(card: DashboardCardEntity) = viewModelScope.launch {
        c.dashboard.deleteCard(card.id)
        _edit.value = _edit.value?.let { e -> e.copy(draft = e.draft.filter { it.cardId != card.id }, original = e.original.filter { it.cardId != card.id }) }
    }

    fun removeRecent(e: RecentEntry) = viewModelScope.launch { c.recent.remove(e.typeKey, e.id) }
}
