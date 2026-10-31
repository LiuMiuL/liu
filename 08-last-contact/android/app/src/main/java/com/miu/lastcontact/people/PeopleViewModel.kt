package com.miu.lastcontact.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.lastcontact.AppContainer
import com.miu.lastcontact.data.ContactEventEntity
import com.miu.lastcontact.data.PersonEntity
import com.miu.lastcontact.data.PersonSummary
import com.miu.lastcontact.data.SavePersonResult
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.ChannelLatestView
import com.miu.lastcontact.domain.ContactRules
import com.miu.lastcontact.domain.Direction
import com.miu.lastcontact.domain.SortMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PeopleUi(
    val items: List<PersonSummary> = emptyList(),
    val groups: List<String> = emptyList(),
    val total: Int = 0,
    val overdueCount: Int = 0,
    val loaded: Boolean = false,
)

data class DetailUi(
    val person: PersonEntity? = null,
    val events: List<ContactEventEntity> = emptyList(),
    val eventCount: Int = 0,
    val byChannel: List<ChannelLatestView> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModel(private val c: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    /** 자정이 지나면 화면 복귀 시 다시 계산한다. */
    val today = MutableStateFlow(c.clock.today())
    val query: StateFlow<String> = saved.getStateFlow("q", "")
    val group: StateFlow<String?> = saved.getStateFlow("group", null)
    val selectedId: StateFlow<String?> = saved.getStateFlow("selected", null)
    val sort: StateFlow<SortMode> = c.settings.settings.map { it.sortMode }.stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.DUE)

    val ui: StateFlow<PeopleUi> = combine(c.repo.summaries, today, query, group, sort) { list, d, q, g, s ->
        val groups = list.map { it.person.groupName }.filter { it.isNotBlank() }.distinct().sorted()
        val filtered = list.filter { ps ->
            (g == null || ps.person.groupName == g) &&
                (q.isBlank() || ps.person.name.contains(q.trim(), true) || ps.person.aliases.contains(q.trim(), true))
        }
        PeopleUi(
            items = ContactRules.sort(filtered, s, d, { it.person.name }, { it.last?.day }, { it.person.goalDays }),
            groups = groups, total = list.size,
            overdueCount = list.count { ContactRules.isOverdue(it.last?.day, it.person.goalDays, d) },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleUi())

    val detail: StateFlow<DetailUi> = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(DetailUi())
        else combine(c.repo.observePerson(id), c.repo.observeEvents(id), c.repo.observeEventCount(id), c.repo.observeChannelLatest(id)) { p, ev, n, ch ->
            DetailUi(p, ev, n, ch.mapNotNull { r -> Channel.of(r.channel)?.let { ChannelLatestView(it, LocalDate.parse(r.day)) } }.sortedByDescending { it.day })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUi())

    fun refreshToday() { today.value = c.clock.today() }
    fun setQuery(q: String) { saved["q"] = q.take(40) }
    fun setGroup(g: String?) { saved["group"] = g }
    fun select(id: String?) { saved["selected"] = id }
    fun setSort(m: SortMode) = viewModelScope.launch { c.settings.update { it.copy(sort = m.name) } }

    /** '오늘 연락함'. 새로 생긴 기록이면 되돌리기 콜백을 넘긴다. */
    fun contactedToday(personId: String, onDone: (undo: (() -> Unit)?) -> Unit) = viewModelScope.launch {
        val d = c.clock.today()
        val fresh = c.repo.record(personId, d, Channel.MANUAL, Direction.UNKNOWN, c.clock.nowMillis())
        onDone(if (fresh) ({ viewModelScope.launch { c.repo.deleteRecord(personId, d, Channel.MANUAL) } }) else null)
    }

    fun recordOn(personId: String, day: LocalDate, note: String, onError: (String) -> Unit) = viewModelScope.launch {
        if (!ContactRules.validManualDay(day, c.clock.today())) { onError("미래 날짜는 기록할 수 없습니다."); return@launch }
        c.repo.record(personId, day, Channel.MANUAL, Direction.UNKNOWN, null, note)
    }

    fun deleteEvent(id: String) = viewModelScope.launch { c.repo.deleteEvent(id) }

    suspend fun savePerson(id: String?, name: String, aliases: String, phones: String, group: String, goal: Int?, memo: String): SavePersonResult =
        c.repo.savePerson(id, name, aliases, phones, group, goal, memo)

    fun deletePerson(id: String) = viewModelScope.launch {
        if (selectedId.value == id) select(null)
        c.repo.deletePerson(id)
    }
}
