package com.miu.fold.dashboard.notes

import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.NoteEntity
import kotlinx.coroutines.flow.Flow

sealed interface NoteSave {
    data class Saved(val note: NoteEntity) : NoteSave
    /** 읽은 뒤 다른 화면에서 바뀌었거나(current≠null) 휴지통·삭제됨(current=null 또는 deletedAt≠null). */
    data class Conflict(val current: NoteEntity?) : NoteSave
}

/** 초안 저장에 필요한 최소 계약. 저장 실패 테스트에서 가짜 구현으로 바꿔 끼운다. */
interface NoteWriter {
    suspend fun create(title: String, body: String, tags: String = ""): NoteEntity
    suspend fun saveIfUnchanged(id: String, title: String, body: String, tags: String, expectedRevision: Int): NoteSave
    fun observe(id: String): Flow<NoteEntity?>
}

class NoteRepository(db: AppDatabase, private val clock: AppClock) : NoteWriter {
    private val dao = db.noteDao()

    val active: Flow<List<NoteEntity>> = dao.observeActive()
    val trash: Flow<List<NoteEntity>> = dao.observeTrash()

    override fun observe(id: String): Flow<NoteEntity?> = dao.observe(id)
    suspend fun get(id: String): NoteEntity? = dao.get(id)

    override suspend fun create(title: String, body: String, tags: String): NoteEntity {
        val now = clock.nowMillis()
        val note = NoteEntity(
            id = Ids.newId(), title = title.take(200), body = body.take(NoteRules.BODY_MAX),
            tags = tags, createdAt = now, updatedAt = now,
        )
        dao.upsert(note)
        return note
    }

    /**
     * 같은 메모를 여러 화면(홈 빠른 메모·메모 편집기)이 동시에 잡고 있어도 서로 덮어쓰지 않도록,
     * 읽은 revision과 DB revision이 같을 때만 저장한다. 예외(저장 공간 부족 등)는 호출부로 전달한다.
     */
    override suspend fun saveIfUnchanged(id: String, title: String, body: String, tags: String, expectedRevision: Int): NoteSave {
        val n = dao.updateIfRevision(id, title.take(200), body.take(NoteRules.BODY_MAX), tags, clock.nowMillis(), expectedRevision)
        val current = dao.get(id)
        return if (n == 1 && current != null) NoteSave.Saved(current) else NoteSave.Conflict(current)
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned, clock.nowMillis())
    suspend fun moveToTrash(id: String) = dao.setDeleted(id, clock.nowMillis())
    suspend fun restore(id: String) = dao.setDeleted(id, null)
    suspend fun purge(id: String) = dao.purge(id)
    suspend fun emptyTrash() = dao.purgeAllTrash()
}
