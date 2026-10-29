package com.miu.fold.dashboard.todos

import androidx.room.withTransaction
import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.TodoEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TodoRepository(private val db: AppDatabase, private val clock: AppClock) {
    private val dao = db.todoDao()

    val all: Flow<List<TodoEntity>> = dao.observeAll()

    suspend fun get(id: String) = dao.get(id)

    suspend fun add(
        title: String,
        description: String = "",
        dueDate: LocalDate? = null,
        priority: Int = 0,
        sourceNoteId: String? = null,
    ): TodoEntity? {
        val t = title.trim()
        if (t.isEmpty()) return null
        val now = clock.nowMillis()
        val todo = TodoEntity(
            id = Ids.newId(), title = t.take(300), description = description.take(5000),
            dueDate = dueDate?.toString(), priority = priority.coerceIn(0, 2),
            orderKey = dao.maxOrder() + 1, sourceNoteId = sourceNoteId,
            createdAt = now, updatedAt = now,
        )
        dao.upsert(todo)
        return todo
    }

    /** 같은 메모에서 같은 제목의 미완료 할 일이 이미 있으면 만들지 않는다(버튼 연타·재실행 중복 방지). */
    suspend fun addFromNote(noteId: String, title: String): Boolean {
        val t = title.trim().take(300)
        if (t.isEmpty()) return false
        if (dao.all().any { it.sourceNoteId == noteId && it.doneAt == null && it.title == t }) return false
        return add(t, sourceNoteId = noteId) != null
    }

    suspend fun update(todo: TodoEntity) = dao.upsert(todo.copy(title = todo.title.trim().take(300), updatedAt = clock.nowMillis()))

    /** 원하는 최종 상태를 지정한다(토글 아님). 완료·되돌리기 연타에도 마지막 요청 상태로 수렴한다. */
    suspend fun setDone(id: String, done: Boolean) {
        val current = dao.get(id) ?: return
        if ((current.doneAt != null) == done) return
        val now = clock.nowMillis()
        dao.setDone(id, if (done) now else null, now)
    }

    /**
     * 화면에 보이는 목록([visible], 필터 적용 후) 기준으로 바로 옆 항목과 자리를 바꾼다.
     * 필터 때문에 안 보이는 항목과 바뀌어 '눌러도 변화 없음'이 되지 않게 한다. 숨은 항목들의 상대 순서는 유지.
     */
    suspend fun move(id: String, delta: Int, visible: List<TodoEntity>) = db.withTransaction {
        val from = visible.indexOfFirst { it.id == id }
        if (from < 0) return@withTransaction
        val to = (from + delta).coerceIn(0, visible.lastIndex)
        if (to == from) return@withTransaction
        var all = dao.all().sortedWith(compareBy<TodoEntity>({ it.orderKey }, { it.createdAt }))
        if (all.map { it.orderKey }.distinct().size != all.size) {
            all.forEachIndexed { i, t -> dao.setOrder(t.id, i.toLong()) }
            all = dao.all()
        }
        val keys = all.associate { it.id to it.orderKey }
        val a = visible[from].id
        val b = visible[to].id
        val ka = keys[a] ?: return@withTransaction
        val kb = keys[b] ?: return@withTransaction
        // 보이는 목록에서 a와 b 사이의 항목(필터로 숨은 것 포함)은 그대로 두고 두 항목만 맞바꾼다.
        dao.setOrder(a, kb)
        dao.setOrder(b, ka)
    }

    suspend fun delete(id: String) = dao.delete(id)
    suspend fun restore(todo: TodoEntity) = dao.upsert(todo)
}
