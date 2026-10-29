package com.miu.fold.dashboard.shortcuts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.data.ShortcutEntity
import com.miu.fold.dashboard.recent.RecentType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShortcutsViewModel(private val c: AppContainer) : ViewModel() {
    val shortcuts: StateFlow<List<ShortcutEntity>> = c.shortcuts.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun save(id: String?, type: ShortcutType, label: String, target: String, fallback: String?): ShortcutSaveResult =
        c.shortcuts.save(id, type, label, target, fallback)

    fun delete(id: String) = viewModelScope.launch { c.shortcuts.delete(id); c.recent.remove(RecentType.SHORTCUT.key, id) }

    /** 바로가기를 '열었다'는 사실만 최근 항목에 남긴다. 상대 앱 작업 완료로 기록하지 않는다. */
    fun opened(id: String) = viewModelScope.launch { c.recent.record(RecentType.SHORTCUT, id) }
}
