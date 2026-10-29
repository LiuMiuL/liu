package com.miu.fold.dashboard.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.data.NoteEntity
import com.miu.fold.dashboard.recent.RecentType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val c: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> = c.notes.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trash: StateFlow<List<NoteEntity>> = c.notes.trash.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 편집기 초안. SavedState에는 메모 ID만 둔다(본문은 DB). */
    val editor = NoteDraft(viewModelScope, c.notes, onIdAssigned = { saved[KEY_EDIT] = it })

    /** 홈 '빠른 메모' 카드 초안. */
    val quick = NoteDraft(viewModelScope, c.notes, onIdAssigned = { saved[KEY_QUICK] = it })

    init {
        viewModelScope.launch {
            saved.get<String>(KEY_EDIT)?.let { id -> c.notes.get(id)?.let(editor::load) }
            saved.get<String>(KEY_QUICK)?.let { id -> c.notes.get(id)?.let(quick::load) }
        }
    }

    val editingId: String? get() = editor.state.value.id

    fun open(id: String) = viewModelScope.launch {
        editor.flush().join()
        val n = c.notes.get(id) ?: return@launch
        editor.load(n)
        c.recent.record(RecentType.NOTE, id)
    }

    fun newNote(initialBody: String = "") = viewModelScope.launch {
        editor.flush().join()
        editor.reset()
        if (initialBody.isNotEmpty()) editor.edit(body = initialBody)
    }

    fun closeEditor() = viewModelScope.launch {
        editor.flush().join()
        editor.reset()
    }

    fun newQuick() = viewModelScope.launch {
        quick.flush().join()
        quick.reset()
    }

    fun togglePin(n: NoteEntity) = viewModelScope.launch { c.notes.setPinned(n.id, !n.pinned) }

    fun trash(id: String) = viewModelScope.launch {
        if (editor.state.value.id == id) { editor.flush().join(); editor.reset() }
        if (quick.state.value.id == id) quick.reset()
        c.notes.moveToTrash(id)
    }

    fun restore(id: String) = viewModelScope.launch { c.notes.restore(id) }
    fun purge(id: String) = viewModelScope.launch { c.notes.purge(id); c.recent.remove(RecentType.NOTE.key, id) }
    fun emptyTrash() = viewModelScope.launch { c.notes.emptyTrash() }

    /** 외부 공유로 받은 텍스트를 사용자가 확인한 뒤 새 메모로 저장. */
    fun saveShared(text: String, subject: String?) = viewModelScope.launch {
        val n = c.notes.create(subject?.take(100) ?: "", text.take(NoteRules.SHARED_TEXT_MAX))
        c.recent.record(RecentType.NOTE, n.id)
    }

    override fun onCleared() {
        // viewModelScope가 취소되기 전에 마지막 저장을 시도할 수 없으므로 화면 쪽 ON_STOP flush에 의존한다.
        super.onCleared()
    }

    companion object {
        private const val KEY_EDIT = "editingNoteId"
        private const val KEY_QUICK = "quickNoteId"
    }
}
