package com.miu.lastcontact.importer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.lastcontact.data.PersonEntity
import com.miu.lastcontact.data.UnmatchedSummary
import com.miu.lastcontact.domain.MappingKind
import com.miu.lastcontact.domain.SenderMapping
import com.miu.lastcontact.domain.SenderStats
import com.miu.lastcontact.people.Messages
import com.miu.lastcontact.ui.EmptyState
import com.miu.lastcontact.ui.Fmt
import com.miu.lastcontact.ui.ReadableWidth
import com.miu.lastcontact.ui.Space

@Composable
fun ImportScreen(vm: ImportViewModel, msg: Messages) {
    val export by vm.export.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(message) { message?.let { msg.show(it); vm.consumeMessage() } }
    when (val st = export) {
        is ExportState.Review -> MappingScreen(vm, st)
        else -> ImportHome(vm, st)
    }
}

@Composable
private fun ImportHome(vm: ImportViewModel, st: ExportState) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unmatched by vm.unmatched.collectAsStateWithLifecycle()
    val ignored by vm.ignored.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    val listenerOn = remember(refresh) { vm.listenerEnabled(context) }
    val callOk = remember(refresh) { granted(context, Manifest.permission.READ_CALL_LOG) }
    val smsOk = remember(refresh) { granted(context, Manifest.permission.READ_SMS) }
    var linking by rememberSaveable { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readExport { context.contentResolver.openInputStream(uri) }
    }
    val askPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        refresh++
        if (res.values.any { it }) { vm.setCallSms(true); vm.syncCallSms() }
    }

    ReadableWidth {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            item { Text("가져오기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.s)) }

            item {
                Section("카톡 대화 내보내기 가져오기", "가장 정확한 방법이에요(보낸 것·받은 것 모두). 대화 내용은 저장하지 않고 날짜만 남깁니다.") {
                    Text(
                        "1. 카카오톡 채팅방 → 오른쪽 위 ≡ → ⚙ 설정 → '대화 내보내기'\n2. '텍스트만 보내기' → 공유 목록에서 '연락 기록' 선택\n   (또는 파일로 저장한 뒤 아래 '파일 선택')",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { pickFile.launch(arrayOf("text/plain", "application/zip", "application/octet-stream")) }, enabled = st !is ExportState.Reading) { Text("파일 선택") }
                        if (st is ExportState.Reading) { CircularProgressIndicator(); Text("읽는 중…") }
                    }
                    if (st is ExportState.Error) Text("⚠ ${st.message}", color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Section("카톡 알림 자동 기록", "카톡 알림이 올 때 '누가 언제' 보냈는지만 기록해요. 받은 메시지만 잡히고, 알림을 끈 방·놓친 알림은 기록되지 않습니다.") {
                    Text(if (listenerOn) "✓ 알림 접근 허용됨" else "알림 접근이 꺼져 있습니다", style = MaterialTheme.typography.titleSmall)
                    if (listenerOn) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("기록하기", Modifier.weight(1f))
                        Switch(checked = settings.kakaoCaptureOn, onCheckedChange = vm::setKakaoCapture)
                    }
                    OutlinedButton(onClick = { openListenerSettings(context) }) { Text(if (listenerOn) "알림 접근 설정 열기" else "알림 접근 허용하러 가기") }
                    if (!listenerOn) Text(
                        "목록에서 '연락 기록'을 켜세요. 회색으로 막혀 있으면(파일로 직접 설치한 경우): 휴대폰 설정 → 애플리케이션 → 연락 기록 → 오른쪽 위 ⋮ → '제한된 설정 허용' 후 다시 시도하세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Section("통화·문자 기록", "등록한 사람의 번호와 통화(1초 이상)·문자를 주고받은 날짜를 가져와요. 문자 내용은 읽지 않습니다.") {
                    Text(
                        when { callOk && smsOk -> "✓ 통화·문자 권한 허용됨"; callOk -> "통화만 허용됨"; smsOk -> "문자만 허용됨"; else -> "권한이 없습니다" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (callOk || smsOk) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("매일 자동으로 가져오기", Modifier.weight(1f))
                        Switch(checked = settings.callSmsOn, onCheckedChange = vm::setCallSms)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        if (!callOk || !smsOk) Button(onClick = { askPerms.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)) }) { Text("권한 허용") }
                        if (callOk || smsOk) OutlinedButton(onClick = { vm.syncCallSms() }, enabled = !syncing) { Text(if (syncing) "가져오는 중…" else "지금 가져오기") }
                        if (callOk || smsOk) TextButton(onClick = { vm.syncCallSms(full = true) }, enabled = !syncing) { Text("최근 1년 다시 훑기") }
                    }
                    Text("번호는 사람 편집에서 등록하거나 '연락처에서 가져오기'로 넣으세요. 등록하지 않은 번호는 저장하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Text("미확인 카톡 이름 ${unmatched.size}개", style = MaterialTheme.typography.titleMedium)
                Text("알림으로 들어왔지만 등록된 사람과 이름이 안 맞은 경우예요. 사람에 연결하면 쌓인 날짜가 반영됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unmatched.isEmpty()) EmptyState("없음")
            }
            items(unmatched, key = { it.nameKey }) { u -> UnmatchedRow(u, onLink = { linking = u.nameKey }, onNew = { vm.linkUnmatchedAsNew(u.nameKey, u.displayName) }, onIgnore = { vm.ignore(u.nameKey, u.displayName) }) }
            if (ignored.isNotEmpty()) item {
                HorizontalDivider()
                Text("무시하는 이름: " + ignored.joinToString(", ") { it.displayName }, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    ignored.take(10).forEach { n -> TextButton(onClick = { vm.unignore(n.nameKey) }) { Text("'${n.displayName}' 다시 기록") } }
                }
            }
            item { Text(" ") }
        }
    }
    linking?.let { key ->
        val people by vm.people.collectAsStateWithLifecycle()
        val u = unmatched.firstOrNull { it.nameKey == key }
        if (u == null) linking = null
        else PersonPicker("'${u.displayName}'을(를) 누구와 연결할까요?", people.map { it.person }, onPick = { vm.linkUnmatched(key, it); linking = null }, onDismiss = { linking = null })
    }
}

@Composable
private fun UnmatchedRow(u: UnmatchedSummary, onLink: () -> Unit, onNew: () -> Unit, onIgnore: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(u.displayName, style = MaterialTheme.typography.titleSmall)
        Text(
            "${u.days}일 · 마지막 ${Fmt.dateTime(u.lastAt)}" +
                (u.roomName?.let { " · $it 방" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            TextButton(onClick = onLink) { Text("사람에 연결") }
            TextButton(onClick = onNew) { Text("새 사람으로") }
            TextButton(onClick = onIgnore) { Text("무시") }
        }
        HorizontalDivider()
    }
}

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun MappingScreen(vm: ImportViewModel, st: ExportState.Review) {
    val s = st.summary
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val others = st.mappings.count { it.kind != MappingKind.ME }
    val oneToOne = s.senders.size - st.mappings.count { it.kind == MappingKind.ME } == 1
    ReadableWidth {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                item {
                    Text("가져오기 확인", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Space.s))
                    Text(
                        (s.chatTitle?.let { "'$it' 대화 · " } ?: "") + "${Fmt.date(s.firstDay)} ~ ${Fmt.date(s.lastDay)} · 메시지 ${s.messageCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (oneToOne) "1:1 대화로 보고, 대화가 있었던 모든 날을 상대와의 연락으로 기록합니다."
                        else "단체 대화로 보고, 각 사람이 직접 말한 날만 그 사람과의 연락으로 기록합니다.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("보낸 사람마다 누구인지 골라 주세요. 대화 내용은 저장하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (others == 0) Text("⚠ 모두 '나'로 되어 있어 저장할 날짜가 없습니다.", color = MaterialTheme.colorScheme.error)
                }
                items(s.senders, key = { it.name }) { stat ->
                    val m = st.mappings.first { it.sender == stat.name }
                    SenderCard(stat, m, st.people, onKind = { k -> if (k == MappingKind.PERSON) pickingFor = stat.name else vm.setMapping(stat.name, k) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End)) {
                OutlinedButton(onClick = vm::cancelExport) { Text("취소") }
                Button(onClick = vm::applyExport) { Text("저장") }
            }
        }
    }
    pickingFor?.let { sender ->
        PersonPicker("'$sender'은(는) 누구인가요?", st.people, onPick = { vm.setMapping(sender, MappingKind.PERSON, it); pickingFor = null }, onDismiss = { pickingFor = null })
    }
}

@Composable
private fun SenderCard(s: SenderStats, m: SenderMapping, people: List<PersonEntity>, onKind: (MappingKind) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.m)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("메시지 ${s.count}개 · ${s.days.size}일 · 마지막 ${Fmt.date(s.last.toLocalDate())}", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FilterChip(selected = m.kind == MappingKind.ME, onClick = { onKind(MappingKind.ME) }, label = { Text("나") })
                FilterChip(
                    selected = m.kind == MappingKind.PERSON, onClick = { onKind(MappingKind.PERSON) },
                    label = { Text(if (m.kind == MappingKind.PERSON) (people.firstOrNull { it.id == m.personId }?.name ?: "사람") + " ▾" else "등록된 사람") },
                    enabled = people.isNotEmpty(),
                )
                FilterChip(selected = m.kind == MappingKind.NEW_PERSON, onClick = { onKind(MappingKind.NEW_PERSON) }, label = { Text("새 사람 '${s.name.take(10)}'") })
                FilterChip(selected = m.kind == MappingKind.IGNORE, onClick = { onKind(MappingKind.IGNORE) }, label = { Text("무시") })
            }
        }
    }
}

@Composable
private fun PersonPicker(title: String, people: List<PersonEntity>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(q, { q = it.take(30) }, singleLine = true, placeholder = { Text("이름 찾기") })
                val list = people.filter { q.isBlank() || it.name.contains(q, true) || it.aliases.contains(q, true) }.take(50)
                if (list.isEmpty()) EmptyState("등록된 사람이 없습니다.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = Space.s)) {
                    items(list, key = { it.id }) { p ->
                        TextButton(onClick = { onPick(p.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(p.name + if (p.groupName.isNotBlank()) " · ${p.groupName}" else "", Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun openListenerSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}
