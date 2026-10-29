package com.miu.fold.dashboard.notes

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.data.NoteEntity
import com.miu.fold.dashboard.ui.ConfirmDialog
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Fmt
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.ReadableWidth
import com.miu.fold.dashboard.ui.SaveStatusLabel
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space

private const val LIST_MIN = 240
private const val DETAIL_MIN = 320

/** 목록 240dp + 편집 320dp + 여백을 확보할 수 있을 때만 2패널. 그렇지 않으면 목록→편집 전환. */
@Composable
fun NotesScreen(vms: Vms, nav: Nav, available: Dp, editorOpen: Boolean, setEditorOpen: (Boolean) -> Unit) {
    val twoPane = available.value >= LIST_MIN + DETAIL_MIN + 48 && available.value >= 600
    val open = { id: String -> vms.notes.open(id); setEditorOpen(true) }
    val new = { vms.notes.newNote(); setEditorOpen(true) }
    val close = { vms.notes.closeEditor(); setEditorOpen(false) }
    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            NoteList(vms, Modifier.width((available.value * 0.38f).coerceAtLeast(LIST_MIN.toFloat()).dp).fillMaxHeight(), open, new)
            VerticalDivider()
            if (editorOpen) NoteEditor(vms, nav, Modifier.weight(1f), onClose = close, showBack = false)
            else EmptyState("왼쪽에서 메모를 고르거나 새 메모를 만드세요.", Modifier.weight(1f))
        }
    } else {
        if (editorOpen) NoteEditor(vms, nav, Modifier.fillMaxSize(), onClose = close, showBack = true)
        else NoteList(vms, Modifier.fillMaxSize(), open, new)
    }
}

@Composable
private fun NoteList(vms: Vms, modifier: Modifier, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val notes by vms.notes.notes.collectAsStateWithLifecycle()
    val trash by vms.notes.trash.collectAsStateWithLifecycle()
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var confirmPurge by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !showTrash, onClick = { showTrash = false }, label = { Text("메모 ${notes.size}") })
            Text(" ")
            FilterChip(selected = showTrash, onClick = { showTrash = true }, label = { Text("휴지통 ${trash.size}") })
        }
        val list = if (showTrash) trash else notes
        LazyColumn(Modifier.weight(1f)) {
            if (list.isEmpty()) item { EmptyState(if (showTrash) "휴지통이 비어 있습니다." else "메모가 없습니다. 새 메모를 만들어 보세요.") }
            if (showTrash && trash.isNotEmpty()) item {
                TextButton(onClick = { confirmEmpty = true }, modifier = Modifier.padding(horizontal = Space.s)) { Text("휴지통 비우기") }
            }
            items(list, key = { it.id }) { n ->
                NoteRow(n, showTrash, onOpen, onRestore = { vms.notes.restore(n.id) }, onPurge = { confirmPurge = n.id })
                HorizontalDivider()
            }
        }
        if (!showTrash) ExtendedFloatingActionButton(
            onClick = onNew, modifier = Modifier.align(Alignment.End).padding(Space.l),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("새 메모") },
        )
    }
    confirmPurge?.let { id ->
        ConfirmDialog("영구 삭제", "이 메모를 완전히 삭제합니다. 되돌릴 수 없습니다.", "영구 삭제",
            onConfirm = { vms.notes.purge(id) }, onDismiss = { confirmPurge = null })
    }
    if (confirmEmpty) ConfirmDialog("휴지통 비우기", "휴지통의 메모 ${trash.size}개를 완전히 삭제합니다. 되돌릴 수 없습니다.", "모두 삭제",
        onConfirm = { vms.notes.emptyTrash() }, onDismiss = { confirmEmpty = false })
}

@Composable
private fun NoteRow(n: NoteEntity, inTrash: Boolean, onOpen: (String) -> Unit, onRestore: () -> Unit, onPurge: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = !inTrash) { onOpen(n.id) }
            .padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (n.pinned) Icon(Icons.Filled.Star, contentDescription = "고정됨", Modifier.padding(end = Space.xs))
                Text(NoteRules.suggestedTitle(n.title, n.body), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(n.body.replace('\n', ' ').take(120), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Fmt.dateTime(n.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (inTrash) {
            TextButton(onClick = onRestore) { Text("복원") }
            TextButton(onClick = onPurge) { Text("영구 삭제") }
        }
    }
}

@Composable
private fun NoteEditor(vms: Vms, nav: Nav, modifier: Modifier, onClose: () -> Unit, showBack: Boolean) {
    val d by vms.notes.editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(d.notice) { d.notice?.let { nav.showMessage(it); vms.notes.editor.consumeNotice() } }
    val notes by vms.notes.notes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val current = notes.firstOrNull { it.id == d.id }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "목록으로") }
            Column(Modifier.weight(1f).padding(horizontal = Space.s)) { SaveStatusLabel(d.status, d.error, onRetry = { vms.notes.editor.retry() }) }
            if (current != null) {
                IconButton(onClick = { vms.notes.togglePin(current) }) {
                    Icon(Icons.Filled.Star, contentDescription = if (current.pinned) "고정 해제" else "고정")
                }
            }
            IconButton(enabled = !d.isEmpty, onClick = {
                val text = NoteRules.toMarkdown(d.title, d.body)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(Intent.EXTRA_SUBJECT, NoteRules.suggestedTitle(d.title, d.body))
                runCatching { context.startActivity(Intent.createChooser(send, "메모 공유")) }
                    .onFailure { nav.showMessage("공유할 앱이 없습니다.") }
            }) { Icon(Icons.Filled.Share, contentDescription = "공유") }
            if (d.id != null) IconButton(onClick = {
                val id = d.id!!
                vms.notes.trash(id)
                onClose()
                nav.showUndo("메모를 휴지통으로 옮겼습니다") { vms.notes.restore(id) }
            }) { Icon(Icons.Filled.Delete, contentDescription = "휴지통으로") }
        }
        ReadableWidth(Modifier.weight(1f), max = 760) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(
                    value = d.title, onValueChange = { vms.notes.editor.edit(title = it.take(200)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("제목") },
                    placeholder = { Text(NoteRules.suggestedTitle("", d.body)) },
                )
                OutlinedTextField(
                    value = d.body, onValueChange = { vms.notes.editor.edit(body = it) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                    label = { Text("내용") },
                )
                OutlinedTextField(
                    value = d.tags, onValueChange = { vms.notes.editor.edit(tags = it.take(200)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("태그(쉼표로 구분)") },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TextButton(enabled = !d.isEmpty, onClick = {
                        vms.notes.editor.flush().invokeOnCompletion {
                            val st = vms.notes.editor.state.value
                            vms.todos.add(NoteRules.suggestedTitle(st.title, st.body), null, sourceNoteId = st.id)
                            nav.showMessage("할 일로 만들었습니다(원래 메모는 그대로).")
                        }
                    }) { Text("할 일로 만들기") }
                }
                Text("${d.body.length}자", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
