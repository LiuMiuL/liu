package com.miu.fold.dashboard.todos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.data.TodoEntity
import com.miu.fold.dashboard.recent.RecentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class TodoItem(val todo: TodoEntity, val bucket: TodoBucket, val due: LocalDate?)

class TodosViewModel(private val c: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    /** 백그라운드에서 자정이 지나도 복귀 시 [refreshToday]로 다시 계산한다. */
    val today = MutableStateFlow(c.clock.today())
    val filter: StateFlow<TodoFilter> = saved.getStateFlow(KEY_FILTER, TodoFilter.TODAY)
    private val doneMutex = Mutex()

    val items: StateFlow<List<TodoItem>> = combine(c.todos.all, today) { list, d ->
        list.map { t ->
            val due = TodoRules.parseDate(t.dueDate)
            TodoItem(t, TodoRules.bucket(due, t.doneAt != null, d), due)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshToday() { today.value = c.clock.today() }
    fun setFilter(f: TodoFilter) { saved[KEY_FILTER] = f }

    fun add(title: String, due: LocalDate?, priority: Int = 0, description: String = "", sourceNoteId: String? = null) =
        viewModelScope.launch { c.todos.add(title, description, due, priority, sourceNoteId) }

    /** 메모에서 할 일 만들기. 결과(새로 만들었는지)를 알려준다. 원래 메모는 지우지 않는다. */
    fun addFromNote(noteId: String?, title: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val created = if (noteId == null) c.todos.add(title) != null else c.todos.addFromNote(noteId, title)
        onResult(created)
    }

    fun update(t: TodoEntity) = viewModelScope.launch { c.todos.update(t) }

    /** 직렬화해서 연타해도 마지막 요청 상태가 최종 상태가 된다. */
    fun setDone(id: String, done: Boolean) = viewModelScope.launch { doneMutex.withLock { c.todos.setDone(id, done) } }

    fun delete(t: TodoEntity) = viewModelScope.launch { c.todos.delete(t.id); c.recent.remove(RecentType.TODO.key, t.id) }
    fun restore(t: TodoEntity) = viewModelScope.launch { c.todos.restore(t) }
    fun opened(id: String) = viewModelScope.launch { c.recent.record(RecentType.TODO, id) }

    /** 현재 필터로 보이는 미완료 목록 기준으로 이동한다. */
    fun move(id: String, delta: Int) = viewModelScope.launch {
        val visible = items.value.filter { it.todo.doneAt == null && TodoRules.matches(filter.value, it.bucket) }.map { it.todo }
        c.todos.move(id, delta, visible)
    }

    companion object { private const val KEY_FILTER = "todoFilter" }
}
