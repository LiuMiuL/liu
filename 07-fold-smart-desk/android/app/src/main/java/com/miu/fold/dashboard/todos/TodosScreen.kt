package com.miu.fold.dashboard.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.data.TodoEntity
import com.miu.fold.dashboard.ui.Fmt
import com.miu.fold.dashboard.ui.LocalDatePickerDialog
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.ReadableWidth
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space
import java.time.LocalDate

private val priorityLabels = listOf("보통", "중요", "높음")

@Composable
fun TodosScreen(vms: Vms, nav: Nav) {
    val vm = vms.todos
    val items by vm.items.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    var pickDue by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    ReadableWidth {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                TodoFilter.entries.forEach { f ->
                    val n = items.count { TodoRules.matches(f, it.bucket) }
                    FilterChip(selected = filter == f, onClick = { vm.setFilter(f) }, label = { Text("${f.label} $n") })
                }
            }
            Row(Modifier.padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(300) }, modifier = Modifier.weight(1f),
                    singleLine = true, placeholder = { Text("새 할 일") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (title.isNotBlank()) { vm.add(title, TodoRules.parseDate(due)); title = "" }
                    }),
                )
                IconButton(enabled = title.isNotBlank(), onClick = { vm.add(title, TodoRules.parseDate(due)); title = "" }) {
                    Icon(Icons.Filled.Add, contentDescription = "추가")
                }
            }
            FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                val d = TodoRules.parseDate(due)
                FilterChip(selected = d == today, onClick = { due = today.toString() }, label = { Text("오늘") })
                FilterChip(selected = d == today.plusDays(1), onClick = { due = today.plusDays(1).toString() }, label = { Text("내일") })
                FilterChip(selected = d != null && d != today && d != today.plusDays(1), onClick = { pickDue = true },
                    label = { Text(if (d != null && d != today && d != today.plusDays(1)) Fmt.date(d) else "날짜 선택") })
                FilterChip(selected = d == null, onClick = { due = null }, label = { Text("날짜 없음") })
            }
            val shown = items.filter { TodoRules.matches(filter, it.bucket) }
            LazyColumn(Modifier.weight(1f)) {
                if (shown.isEmpty()) item { EmptyState(if (filter == TodoFilter.DONE) "완료한 할 일이 없습니다." else "할 일이 없습니다.") }
                items(shown, key = { it.todo.id }) { item ->
                    TodoRow(
                        item, today,
                        onCheck = { checked ->
                            vm.setDone(item.todo.id, checked)
                            if (checked) nav.showUndo("완료했습니다") { vm.setDone(item.todo.id, false) }
                        },
                        onMove = { delta -> vm.move(item.todo.id, delta) },
                        onOpen = { editing = item.todo.id; vm.opened(item.todo.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (pickDue) LocalDatePickerDialog(TodoRules.parseDate(due) ?: today, onPick = { due = it.toString() }, onDismiss = { pickDue = false })
    editing?.let { id ->
        items.firstOrNull { it.todo.id == id }?.let { item ->
            TodoEditDialog(item.todo, nav, onSave = { vm.update(it) }, onDelete = {
                val copy = item.todo
                vm.delete(copy)
                nav.showUndo("할 일을 삭제했습니다") { vm.restore(copy) }
            }, onDismiss = { editing = null })
        } ?: run { editing = null }
    }
}

@Composable
private fun TodoRow(item: TodoItem, today: LocalDate, onCheck: (Boolean) -> Unit, onMove: (Int) -> Unit, onOpen: () -> Unit) {
    val t = item.todo
    val done = t.doneAt != null
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = done, onCheckedChange = onCheck)
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = Space.s)) {
            Text(
                t.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val parts = buildList {
                add(item.bucket.label)
                item.due?.let { add(Fmt.relativeDay(it, today)) }
                if (t.priority > 0) add("우선순위 ${priorityLabels[t.priority.coerceIn(0, 2)]}")
                if (t.sourceNoteId != null) add("메모에서 만듦")
            }
            Text(
                parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = if (item.bucket == TodoBucket.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!done) {
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "위로") }
            IconButton(onClick = { onMove(1) }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "아래로") }
        }
    }
}

@Composable
private fun TodoEditDialog(t: TodoEntity, nav: Nav, onSave: (TodoEntity) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable(t.id) { mutableStateOf(t.title) }
    var desc by rememberSaveable(t.id) { mutableStateOf(t.description) }
    var due by rememberSaveable(t.id) { mutableStateOf(t.dueDate) }
    var priority by rememberSaveable(t.id) { mutableStateOf(t.priority) }
    var pick by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("할 일 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(title, { title = it.take(300) }, label = { Text("제목") }, singleLine = true)
                OutlinedTextField(desc, { desc = it.take(5000) }, label = { Text("설명") }, minLines = 2)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(selected = due != null, onClick = { pick = true }, label = { Text(TodoRules.parseDate(due)?.let { "마감 ${Fmt.date(it)}" } ?: "마감 날짜 선택") })
                    if (due != null) TextButton(onClick = { due = null }) { Text("날짜 지우기") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    priorityLabels.forEachIndexed { i, l -> FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(l) }) }
                }
                if (t.sourceNoteId != null) TextButton(onClick = { onDismiss(); nav.openNote(t.sourceNoteId) }) { Text("원본 메모 열기") }
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(t.copy(title = title, description = desc, dueDate = due, priority = priority)); onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
    if (pick) LocalDatePickerDialog(TodoRules.parseDate(due), onPick = { due = it.toString() }, onDismiss = { pick = false })
}
