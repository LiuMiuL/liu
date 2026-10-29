package com.miu.fold.dashboard.todos

import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.TodoEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TodoRepository(db: AppDatabase, private val clock: AppClock) {
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

    suspend fun update(todo: TodoEntity) = dao.upsert(todo.copy(title = todo.title.trim().take(300), updatedAt = clock.nowMillis()))

    /** 원하는 최종 상태를 지정한다(토글 아님). 완료·되돌리기 연타에도 마지막 요청 상태로 수렴한다. */
    suspend fun setDone(id: String, done: Boolean) {
        val current = dao.get(id) ?: return
        if ((current.doneAt != null) == done) return
        val now = clock.nowMillis()
        dao.setDone(id, if (done) now else null, now)
    }

    suspend fun move(id: String, delta: Int, ordered: List<TodoEntity>) {
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (to == from) return
        val list = ordered.toMutableList().also { val x = it.removeAt(from); it.add(to, x) }
        list.forEachIndexed { i, t -> if (t.orderKey != i.toLong()) dao.setOrder(t.id, i.toLong()) }
    }

    suspend fun delete(id: String) = dao.delete(id)
    suspend fun restore(todo: TodoEntity) = dao.upsert(todo)
}
