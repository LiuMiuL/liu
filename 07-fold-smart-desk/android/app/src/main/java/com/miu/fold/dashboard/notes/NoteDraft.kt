package com.miu.fold.dashboard.notes

import com.miu.fold.dashboard.core.SaveStatus
import com.miu.fold.dashboard.data.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DraftState(
    val id: String? = null,
    val title: String = "",
    val body: String = "",
    val tags: String = "",
    val status: SaveStatus = SaveStatus.IDLE,
    val error: String? = null,
    val localRev: Long = 0,
    val savedRev: Long = 0,
) {
    val hasUnsaved: Boolean get() = localRev != savedRev
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()
}

/**
 * 메모 초안 자동 저장(디바운스 500ms). 실패하면 FAILED로 표시하고 초안은 메모리에 그대로 둔다(조용히 버리지 않음).
 * 빈 초안은 새 메모로 만들지 않는다.
 */
class NoteDraft(
    private val scope: CoroutineScope,
    private val repo: NoteWriter,
    private val onIdAssigned: (String?) -> Unit = {},
    private val debounceMs: Long = NoteRules.AUTOSAVE_DEBOUNCE_MS,
) {
    private val _state = MutableStateFlow(DraftState())
    val state: StateFlow<DraftState> = _state.asStateFlow()
    private var job: Job? = null
    private val mutex = Mutex()

    fun load(note: NoteEntity) {
        job?.cancel()
        _state.value = DraftState(note.id, note.title, note.body, note.tags, SaveStatus.SAVED)
        onIdAssigned(note.id)
    }

    fun reset() {
        job?.cancel()
        _state.value = DraftState()
        onIdAssigned(null)
    }

    fun edit(title: String? = null, body: String? = null, tags: String? = null) {
        _state.update {
            it.copy(
                title = title ?: it.title,
                body = (body ?: it.body).take(NoteRules.BODY_MAX),
                tags = tags ?: it.tags,
                localRev = it.localRev + 1,
                status = SaveStatus.DIRTY,
                error = null,
            )
        }
        job?.cancel()
        job = scope.launch { delay(debounceMs); persist() }
    }

    /** 화면 이탈·앱 백그라운드 시 즉시 저장. */
    fun flush(): Job {
        job?.cancel()
        return scope.launch { persist() }
    }

    fun retry() = flush()

    suspend fun persist() = mutex.withLock {
        val s = _state.value
        if (!s.hasUnsaved) return@withLock
        if (s.id == null && s.isEmpty) {
            _state.update { it.copy(status = SaveStatus.IDLE, savedRev = s.localRev) }
            return@withLock
        }
        _state.update { it.copy(status = SaveStatus.SAVING) }
        try {
            val saved = if (s.id == null) repo.create(s.title, s.body, s.tags) else repo.save(s.id, s.title, s.body, s.tags)
            _state.update {
                it.copy(
                    id = saved.id, savedRev = s.localRev,
                    status = if (it.localRev == s.localRev) SaveStatus.SAVED else SaveStatus.DIRTY,
                )
            }
            if (s.id == null) onIdAssigned(saved.id)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = if (e is android.database.sqlite.SQLiteFullException) "저장 공간이 부족해 저장하지 못했습니다. 초안은 화면에 남아 있습니다."
            else "저장하지 못했습니다: ${e.message ?: e.javaClass.simpleName}"
            _state.update { it.copy(status = SaveStatus.FAILED, error = msg) }
        }
    }
}
