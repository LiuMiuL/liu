package com.miu.fold.dashboard.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.data.ShortcutEntity
import com.miu.fold.dashboard.notes.NoteRules
import com.miu.fold.dashboard.recent.RecentType
import com.miu.fold.dashboard.shortcuts.InternalTarget
import com.miu.fold.dashboard.shortcuts.LaunchResult
import com.miu.fold.dashboard.shortcuts.ShortcutLauncher
import com.miu.fold.dashboard.todos.TodoBucket
import com.miu.fold.dashboard.todos.TodoRules
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Fmt
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.SaveStatusLabel
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space

@Composable
fun QuickNoteCard(vms: Vms, nav: Nav) {
    val draft by vms.notes.quick.state.collectAsStateWithLifecycle()
    LaunchedEffect(draft.notice) { draft.notice?.let { nav.showMessage(it); vms.notes.quick.consumeNotice() } }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        OutlinedTextField(
            value = draft.body,
            onValueChange = { vms.notes.quick.edit(body = it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 240.dp),
            placeholder = { Text("생각난 것을 바로 적으세요") },
            minLines = 3,
        )
        SaveStatusLabel(draft.status, draft.error, onRetry = { vms.notes.quick.retry() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            TextButton(onClick = { vms.notes.newQuick() }, enabled = !draft.isEmpty) { Text("새 메모") }
            TextButton(
                enabled = !draft.isEmpty,
                onClick = {
                    // 메모를 먼저 저장해 참조를 남기고, 원래 메모는 지우지 않는다.
                    val q = vms.notes.quick
                    q.flush().invokeOnCompletion {
                        val st = q.state.value
                        vms.todos.addFromNote(st.id, NoteRules.suggestedTitle(st.title, st.body)) { created ->
                            nav.showMessage(if (created) "할 일로 만들었습니다(원래 메모는 그대로)." else "이미 이 메모로 만든 할 일이 있습니다.")
                        }
                    }
                },
            ) { Text("할 일로 만들기") }
            TextButton(onClick = nav.openNotes) { Text("전체 메모") }
        }
    }
}

@Composable
fun TodayTodosCard(vms: Vms, nav: Nav) {
    val items by vms.todos.items.collectAsStateWithLifecycle()
    val today by vms.todos.today.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var dueToday by rememberSaveable { mutableStateOf(true) }
    val shown = items.filter { TodoRules.onHomeCard(it.bucket) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.take(300) },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("할 일 추가") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (input.isNotBlank()) { vms.todos.add(input, if (dueToday) today else null); input = "" }
                }),
            )
            IconButton(
                onClick = { vms.todos.add(input, if (dueToday) today else null); input = "" },
                enabled = input.isNotBlank(),
            ) { Icon(Icons.Filled.Add, contentDescription = "할 일 추가") }
        }
        FilterChip(selected = dueToday, onClick = { dueToday = !dueToday }, label = { Text(if (dueToday) "마감: 오늘" else "마감 없음") })
        if (shown.isEmpty()) EmptyState("오늘 할 일이 없습니다.")
        shown.take(6).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = item.todo.doneAt != null,
                    onCheckedChange = { checked ->
                        vms.todos.setDone(item.todo.id, checked)
                        if (checked) nav.showUndo("'${item.todo.title.take(20)}' 완료") { vms.todos.setDone(item.todo.id, false) }
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(item.todo.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val label = when (item.bucket) {
                        TodoBucket.OVERDUE -> "기한 지남 · ${item.due?.let { Fmt.date(it) }}"
                        TodoBucket.TODAY -> "오늘"
                        TodoBucket.NO_DATE -> "날짜 없음"
                        else -> ""
                    }
                    Text(
                        label + if (item.todo.priority >= 2) " · 우선순위 높음" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.bucket == TodoBucket.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (shown.size > 6) TextButton(onClick = nav.openTodos) { Text("${shown.size - 6}개 더 보기") }
        else TextButton(onClick = nav.openTodos) { Text("전체 할 일") }
    }
}

@Composable
fun ShortcutsCard(vms: Vms, nav: Nav, span: Int) {
    val list by vms.shortcuts.shortcuts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var failed by remember { mutableStateOf<LaunchResult.NotFound?>(null) }
    val limit = 4 * span
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (list.isEmpty()) EmptyState("등록한 바로가기가 없습니다. 자주 여는 앱이나 웹 주소를 추가하세요.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            list.take(limit).forEach { s ->
                AssistChip(
                    onClick = { handleLaunch(context, s, vms, nav) { failed = it } },
                    label = { Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = if (s.type == com.miu.fold.dashboard.shortcuts.ShortcutType.APP.name) {
                        { com.miu.fold.dashboard.shortcuts.ShortcutIcon(s) }
                    } else null,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        TextButton(onClick = nav.openShortcuts) { Text(if (list.isEmpty()) "바로가기 추가" else "바로가기 관리") }
    }
    failed?.let { f -> LaunchFailedDialog(f, vms, nav, onDismiss = { failed = null }) }
}

/** 바로가기를 연다. '열었다'만 기록하고 상대 앱 작업 완료로 보지 않는다. */
fun handleLaunch(
    context: android.content.Context,
    s: ShortcutEntity,
    vms: Vms,
    nav: Nav,
    onFail: (LaunchResult.NotFound) -> Unit,
) {
    when (val r = ShortcutLauncher.launch(context, s)) {
        LaunchResult.Opened -> vms.shortcuts.opened(s.id)
        is LaunchResult.Internal -> {
            vms.shortcuts.opened(s.id)
            when (r.target) {
                InternalTarget.NEW_NOTE -> nav.newNote("")
                InternalTarget.TODOS -> nav.openTodos()
                InternalTarget.TIMER -> nav.openTimer()
                InternalTarget.CALENDAR -> nav.openCalendar(null)
                InternalTarget.SEARCH -> nav.openSearch()
            }
        }
        is LaunchResult.NotFound -> onFail(r)
    }
}

@Composable
fun LaunchFailedDialog(f: LaunchResult.NotFound, vms: Vms, nav: Nav, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대상을 찾을 수 없습니다") },
        text = { Text("${f.shortcut.label}: ${f.reason}") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                f.shortcut.fallbackUrl?.let { url ->
                    TextButton(onClick = {
                        val r = ShortcutLauncher.openWeb(context, url, f.shortcut)
                        if (r is LaunchResult.NotFound) nav.showMessage(r.reason) else vms.shortcuts.opened(f.shortcut.id)
                        onDismiss()
                    }) { Text("웹으로 열기") }
                }
                TextButton(onClick = { onDismiss(); nav.openShortcuts() }) { Text("수정") }
                if (f.shortcut.id.isNotEmpty()) TextButton(onClick = { vms.shortcuts.delete(f.shortcut.id); onDismiss() }) { Text("삭제") }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}

@Composable
fun TodayEventsCard(vms: Vms, nav: Nav) {
    val events by vms.events.todayEvents.collectAsStateWithLifecycle()
    val today by vms.events.today.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text("${Fmt.date(today)} · 이 기기에만 저장된 일정", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (events.isEmpty()) EmptyState("오늘 등록한 일정이 없습니다.")
        events.take(5).forEach { e ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vms.events.opened(e.id); nav.openCalendar(today) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (e.allDay) "종일" else e.startInstant?.let { Fmt.time(it, vms.events.zone()) } ?: "",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = Space.m),
                )
                Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = { nav.openCalendar(today) }) { Text("일정 보기·추가") }
    }
}

@Composable
fun RecentCard(vms: Vms, nav: Nav) {
    val recent by vms.home.recent.collectAsStateWithLifecycle()
    val settings by vms.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shortcuts by vms.shortcuts.shortcuts.collectAsStateWithLifecycle()
    var failed by remember { mutableStateOf<LaunchResult.NotFound?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (recent.isEmpty()) EmptyState("이 앱에서 연 항목이 여기에 표시됩니다.")
        recent.take(6).forEach { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = r.title != null) {
                    when (r.type) {
                        RecentType.NOTE -> nav.openNote(r.id)
                        RecentType.TODO -> nav.openTodos()
                        RecentType.EVENT -> nav.openCalendar(null)
                        RecentType.SHORTCUT -> shortcuts.firstOrNull { it.id == r.id }?.let { handleLaunch(context, it, vms, nav) { f -> failed = f } }
                        null -> Unit
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.type?.label ?: "알 수 없음", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = Space.m))
                val title = when {
                    r.title == null -> "삭제된 항목"
                    settings.hideRecentContent && r.type != RecentType.SHORTCUT -> "내용 숨김"
                    else -> r.title
                }
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.title == null) IconButton(onClick = { vms.home.removeRecent(r) }) {
                    Icon(Icons.Filled.Close, contentDescription = "최근 목록에서 제거")
                }
            }
        }
    }
    failed?.let { f -> LaunchFailedDialog(f, vms, nav, onDismiss = { failed = null }) }
}
