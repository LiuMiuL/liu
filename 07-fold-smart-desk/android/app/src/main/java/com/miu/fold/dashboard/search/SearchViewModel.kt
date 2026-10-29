package com.miu.fold.dashboard.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.recent.RecentType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUi(val query: String = "", val loading: Boolean = false, val hits: List<SearchHit> = emptyList(), val searched: Boolean = false)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val c: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    val query: StateFlow<String> = saved.getStateFlow(KEY_Q, "")
    val types = MutableStateFlow(RecentType.entries.toSet())

    val results: StateFlow<SearchUi> = combine(query.debounce(250), types) { q, t -> q to t }
        .mapLatest { (q, t) ->
            if (q.isBlank()) SearchUi(q) else SearchUi(q, false, c.search.search(q, t), true)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUi())

    fun setQuery(q: String) { saved[KEY_Q] = q.take(200) }
    fun toggle(t: RecentType) { types.value = types.value.let { if (t in it) it - t else it + t } }

    companion object { private const val KEY_Q = "searchQuery" }
}
