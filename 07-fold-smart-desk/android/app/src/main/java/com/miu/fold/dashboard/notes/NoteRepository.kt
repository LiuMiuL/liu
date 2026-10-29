package com.miu.fold.dashboard.notes

import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.NoteEntity
import kotlinx.coroutines.flow.Flow

/** 초안 저장에 필요한 최소 쓰기 계약. 저장 실패 테스트에서 가짜 구현으로 바꿔 끼운다. */
interface NoteWriter {
    suspend fun create(title: String, body: String, tags: String = ""): NoteEntity
    suspend fun save(id: String, title: String, body: String, tags: String): NoteEntity
}

class NoteRepository(db: AppDatabase, private val clock: AppClock) : NoteWriter {
    private val dao = db.noteDao()

    val active: Flow<List<NoteEntity>> = dao.observeActive()
    val trash: Flow<List<NoteEntity>> = dao.observeTrash()

    fun observe(id: String): Flow<NoteEntity?> = dao.observe(id)
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

    /** 내용을 저장하고 revision을 올린다. 예외(저장 공간 부족 등)는 호출부로 전달해 실패를 표시하게 한다. */
    override suspend fun save(id: String, title: String, body: String, tags: String): NoteEntity {
        val now = clock.nowMillis()
        val old = dao.get(id)
        val note = old?.copy(title = title.take(200), body = body.take(NoteRules.BODY_MAX), tags = tags, updatedAt = now, revision = old.revision + 1)
            ?: NoteEntity(id, title.take(200), body.take(NoteRules.BODY_MAX), tags = tags, createdAt = now, updatedAt = now)
        dao.upsert(note)
        return note
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned, clock.nowMillis())
    suspend fun moveToTrash(id: String) = dao.setDeleted(id, clock.nowMillis())
    suspend fun restore(id: String) = dao.setDeleted(id, null)
    suspend fun purge(id: String) = dao.purge(id)
    suspend fun emptyTrash() = dao.purgeAllTrash()
}
