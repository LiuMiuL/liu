package com.miu.fold.dashboard.shortcuts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.dashboard.LaunchFailedDialog
import com.miu.fold.dashboard.dashboard.handleLaunch
import com.miu.fold.dashboard.data.ShortcutEntity
import com.miu.fold.dashboard.ui.ConfirmDialog
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.ReadableWidth
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(vms: Vms, nav: Nav, onClose: () -> Unit) {
    val list by vms.shortcuts.shortcuts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<LaunchResult.NotFound?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("바로가기") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
        )
        ReadableWidth(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "등록한 앱·웹·앱 안 화면을 엽니다. 다른 앱을 열기만 하며, 그 앱 안의 작업이나 답변을 읽지 않습니다.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Space.l),
                    )
                }
                if (list.isEmpty()) item { EmptyState("바로가기가 없습니다.") }
                items(list, key = { it.id }) { s ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        ShortcutIcon(s)
                        Column(Modifier.weight(1f)) {
                            Text(s.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(typeLabel(s) + " · " + s.target, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { handleLaunch(context, s, vms, nav) { failed = it } }) { Text("열기") }
                        TextButton(onClick = { editing = s.id }) { Text("수정") }
                        TextButton(onClick = { deleting = s.id }) { Text("삭제") }
                    }
                    HorizontalDivider()
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true }, modifier = Modifier.align(Alignment.End).padding(Space.l),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("바로가기 추가") },
        )
    }
    if (creating) ShortcutDialog(null, vms, nav, onDismiss = { creating = false })
    editing?.let { id -> list.firstOrNull { it.id == id }?.let { ShortcutDialog(it, vms, nav, onDismiss = { editing = null }) } ?: run { editing = null } }
    deleting?.let { id ->
        ConfirmDialog("바로가기 삭제", "이 바로가기만 삭제합니다. 대상 앱이나 웹사이트에는 영향이 없습니다.", "삭제",
            onConfirm = { vms.shortcuts.delete(id) }, onDismiss = { deleting = null })
    }
    failed?.let { f -> LaunchFailedDialog(f, vms, nav, onDismiss = { failed = null }) }
}

private fun typeLabel(s: ShortcutEntity) = when (runCatching { ShortcutType.valueOf(s.type) }.getOrNull()) {
    ShortcutType.WEB -> "웹"
    ShortcutType.APP -> "앱"
    ShortcutType.INTERNAL -> "앱 안"
    null -> "알 수 없음"
}

@Composable
private fun ShortcutDialog(existing: ShortcutEntity?, vms: Vms, nav: Nav, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(existing?.type?.let { runCatching { ShortcutType.valueOf(it) }.getOrNull() } ?: ShortcutType.WEB) }
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var url by rememberSaveable { mutableStateOf(if (existing?.type == ShortcutType.WEB.name) existing.target else "") }
    var pkg by rememberSaveable { mutableStateOf(if (existing?.type == ShortcutType.APP.name) existing.target else "") }
    var internal by rememberSaveable { mutableStateOf(if (existing?.type == ShortcutType.INTERNAL.name) existing.target else InternalTarget.NEW_NOTE.key) }
    var fallback by rememberSaveable { mutableStateOf(existing?.fallbackUrl ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var appFilter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(type) {
        if (type == ShortcutType.APP && apps == null) apps = withContext(Dispatchers.IO) { ShortcutLauncher.launchableApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "바로가기 추가" else "바로가기 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(selected = type == ShortcutType.WEB, onClick = { type = ShortcutType.WEB }, label = { Text("웹") })
                    FilterChip(selected = type == ShortcutType.APP, onClick = { type = ShortcutType.APP }, label = { Text("앱") })
                    FilterChip(selected = type == ShortcutType.INTERNAL, onClick = { type = ShortcutType.INTERNAL }, label = { Text("앱 안") })
                }
                OutlinedTextField(label, { label = it.take(60) }, label = { Text("이름(선택)") }, singleLine = true)
                when (type) {
                    ShortcutType.WEB -> {
                        OutlinedTextField(url, { url = it.take(UrlValidator.MAX_LENGTH) }, label = { Text("주소 (https://…)") }, singleLine = true)
                        Text("로그인 토큰·API 키가 들어간 주소는 저장하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    ShortcutType.APP -> {
                        if (pkg.isNotEmpty()) Text("선택됨: ${apps?.firstOrNull { it.packageName == pkg }?.label ?: pkg}", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(appFilter, { appFilter = it.take(40) }, label = { Text("앱 찾기") }, singleLine = true)
                        val loaded = apps
                        if (loaded == null) Text("앱 목록을 불러오는 중…")
                        else {
                            val filtered = loaded.filter { appFilter.isBlank() || it.label.contains(appFilter, true) }.take(30)
                            if (filtered.isEmpty()) Text("표시할 앱이 없습니다.")
                            filtered.forEach { a ->
                                Text(
                                    (if (a.packageName == pkg) "✓ " else "") + a.label,
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { pkg = a.packageName; if (label.isBlank()) label = a.label }
                                        .padding(vertical = Space.m),
                                )
                            }
                        }
                        OutlinedTextField(fallback, { fallback = it.take(UrlValidator.MAX_LENGTH) }, label = { Text("앱이 없을 때 열 웹 주소(선택)") }, singleLine = true)
                    }
                    ShortcutType.INTERNAL -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        InternalTarget.entries.forEach { t ->
                            FilterChip(selected = internal == t.key, onClick = { internal = t.key }, label = { Text(t.label) })
                        }
                    }
                }
                error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val target = when (type) { ShortcutType.WEB -> url; ShortcutType.APP -> pkg; ShortcutType.INTERNAL -> internal }
                    when (val r = vms.shortcuts.save(existing?.id, type, label, target, if (type == ShortcutType.APP) fallback else null)) {
                        is ShortcutSaveResult.Invalid -> error = r.reason
                        is ShortcutSaveResult.Saved -> { r.warning?.let(nav.showMessage); onDismiss() }
                    }
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
