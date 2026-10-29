package com.miu.fold.dashboard.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.data.LocalEventEntity
import com.miu.fold.dashboard.recent.RecentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class EventsViewModel(private val c: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    val today = MutableStateFlow(c.clock.today())
    val selected: StateFlow<String?> = saved.getStateFlow(KEY_DATE, null)

    val all: StateFlow<List<LocalEventEntity>> = c.events.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEvents: StateFlow<List<LocalEventEntity>> = combine(c.events.all, today) { list, d ->
        list.filter { e -> runCatching { EventRules.occursOn(e.span(), d, c.clock.zone()) }.getOrDefault(false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun zone() = c.clock.zone()
    fun refreshToday() { today.value = c.clock.today() }
    fun select(d: LocalDate) { saved[KEY_DATE] = d.toString() }

    fun eventsOn(list: List<LocalEventEntity>, d: LocalDate) =
        list.filter { runCatching { EventRules.occursOn(it.span(), d, c.clock.zone()) }.getOrDefault(false) }

    fun save(id: String?, title: String, span: EventSpan, zoneId: String?, memo: String) =
        viewModelScope.launch { c.events.save(id, title, span, zoneId, memo) }

    fun delete(id: String) = viewModelScope.launch { c.events.delete(id); c.recent.remove(RecentType.EVENT.key, id) }
    fun opened(id: String) = viewModelScope.launch { c.recent.record(RecentType.EVENT, id) }

    companion object { private const val KEY_DATE = "selectedDate" }
}
