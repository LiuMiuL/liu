package com.miu.fold.dashboard.notes

import com.miu.fold.dashboard.core.SaveStatus
import com.miu.fold.dashboard.data.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
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
    /** 이 초안이 마지막으로 읽거나 저장한 DB revision. 저장 충돌 검사 기준. */
    val baseRevision: Int = 0,
    /** 충돌·외부 변경 안내(한 번 보여주고 지움). */
    val notice: String? = null,
) {
    val hasUnsaved: Boolean get() = localRev != savedRev
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()
}

/**
 * 메모 초안 자동 저장(디바운스 500ms). 실패하면 FAILED로 표시하고 초안은 메모리에 그대로 둔다(조용히 버리지 않음).
 * 빈 초안은 새 메모로 만들지 않는다.
 *
 * 같은 메모를 다른 화면이 고친 경우:
 * - 이 초안에 저장 안 된 변경이 없으면 → DB 내용을 다시 불러온다.
 * - 저장 안 된 변경이 있으면 → 덮어쓰지 않고 내 변경을 '충돌 사본' 새 메모로 저장한다(어느 쪽도 잃지 않음).
 */
@OptIn(ExperimentalCoroutinesApi::class)
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
    private val watchedId = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            watchedId.flatMapLatest { id -> if (id == null) emptyFlow() else repo.observe(id) }
                .collect { remote -> onRemote(remote) }
        }
    }

    private fun onRemote(remote: NoteEntity?) {
        val s = _state.value
        if (s.id == null || s.hasUnsaved || s.status == SaveStatus.SAVING) return
        when {
            remote == null || remote.deletedAt != null -> {
                // 다른 화면에서 휴지통으로 보냈다. 고친 게 없으니 비운다.
                reset()
                _state.update { it.copy(notice = "이 메모가 다른 화면에서 삭제되어 닫았습니다.") }
            }
            remote.id == s.id && remote.revision > s.baseRevision -> {
                _state.update { it.copy(title = remote.title, body = remote.body, tags = remote.tags, baseRevision = remote.revision) }
            }
        }
    }

    fun load(note: NoteEntity) {
        job?.cancel()
        _state.value = DraftState(note.id, note.title, note.body, note.tags, SaveStatus.SAVED, baseRevision = note.revision)
        watchedId.value = note.id
        onIdAssigned(note.id)
    }

    fun reset() {
        job?.cancel()
        _state.value = DraftState()
        watchedId.value = null
        onIdAssigned(null)
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

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
            var notice: String? = null
            val saved: NoteEntity = if (s.id == null) {
                repo.create(s.title, s.body, s.tags)
            } else when (val r = repo.saveIfUnchanged(s.id, s.title, s.body, s.tags, s.baseRevision)) {
                is NoteSave.Saved -> r.note
                is NoteSave.Conflict -> {
                    val deleted = r.current == null || r.current.deletedAt != null
                    notice = if (deleted) "원래 메모가 다른 화면에서 삭제되어, 고친 내용을 새 메모로 저장했습니다."
                    else "다른 화면에서 이 메모가 바뀌어, 내가 고친 내용은 '충돌 사본' 새 메모로 저장했습니다."
                    val title = if (deleted) s.title else NoteRules.suggestedTitle(s.title, s.body).take(180) + " (충돌 사본)"
                    repo.create(title, s.body, s.tags)
                }
            }
            _state.update {
                it.copy(
                    id = saved.id, savedRev = s.localRev, baseRevision = saved.revision,
                    title = if (saved.id != s.id && s.id != null) saved.title else it.title,
                    status = if (it.localRev == s.localRev) SaveStatus.SAVED else SaveStatus.DIRTY,
                    notice = notice ?: it.notice,
                )
            }
            if (saved.id != s.id) { watchedId.value = saved.id; onIdAssigned(saved.id) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = if (e is android.database.sqlite.SQLiteFullException) "저장 공간이 부족해 저장하지 못했습니다. 초안은 화면에 남아 있습니다."
            else "저장하지 못했습니다: ${e.message ?: e.javaClass.simpleName}"
            _state.update { it.copy(status = SaveStatus.FAILED, error = msg) }
        }
    }
}
