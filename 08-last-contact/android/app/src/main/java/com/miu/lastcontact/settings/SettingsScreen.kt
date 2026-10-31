package com.miu.lastcontact.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.lastcontact.BuildConfig
import com.miu.lastcontact.backup.BackupCodec
import com.miu.lastcontact.backup.ImportMode
import com.miu.lastcontact.capture.Reminders
import com.miu.lastcontact.people.Messages
import com.miu.lastcontact.ui.ConfirmDialog
import com.miu.lastcontact.ui.ReadableWidth
import com.miu.lastcontact.ui.Space
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: SettingsViewModel, msg: Messages) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { msg.show(it); vm.consumeMessage() } }
    var names by rememberSaveable(s.myKakaoNames) { mutableStateOf(s.myKakaoNames) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.setReminder(context, true, s.reminderHour)
        if (!ok) msg.show("알림 권한이 없어 매일 알림은 표시되지 않아요. 휴대폰 설정에서 켤 수 있습니다.")
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export { context.contentResolver.openOutputStream(uri) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readImport { context.contentResolver.openInputStream(uri) }
    }

    ReadableWidth {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text("설정", style = MaterialTheme.typography.titleLarge)

            Text("내 카톡 이름", style = MaterialTheme.typography.titleMedium)
            Text("대화 내보내기에서 '나'를 자동으로 고르는 데 써요. 여러 개면 줄마다 하나.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(names, { names = it.take(300) }, minLines = 2)
            TextButton(onClick = { vm.setMyNames(names) }, enabled = names != s.myKakaoNames) { Text("저장") }
            HorizontalDivider()

            Text("매일 연락 알림", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("목표 주기를 넘긴 사람이 있으면 알려주기", Modifier.weight(1f))
                Switch(checked = s.reminderOn, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.setReminder(context, on, s.reminderHour)
                })
            }
            if (s.reminderOn) {
                Text("알림 시각(대략): ${s.reminderHour}시 — 휴대폰 절전 정책에 따라 조금 늦을 수 있어요.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    listOf(8, 12, 18, 20, 22).forEach { h ->
                        TextButton(onClick = { vm.setReminder(context, true, h) }) { Text((if (h == s.reminderHour) "✓ " else "") + "${h}시") }
                    }
                }
            }
            HorizontalDivider()

            Text("백업·복원", style = MaterialTheme.typography.titleMedium)
            Text("사람·연락 날짜·설정을 JSON 파일로 저장해요. 대화·문자 내용은 원래 저장하지 않아 들어가지 않습니다. 일반 텍스트라 이름이 그대로 보이니 보관에 주의하세요.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(onClick = {
                    export.launch("last-contact-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json")
                }) { Text("백업 내보내기") }
                OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("백업 가져오기") }
            }
            HorizontalDivider()

            Text("자료 삭제", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { confirmDeleteAll = true }) { Text("모든 사람과 연락 기록 삭제", color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()

            Text(
                "연락 기록 ${BuildConfig.VERSION_NAME}\n인터넷을 쓰지 않으며 모든 자료는 이 폰 안에만 저장됩니다(클라우드 자동 백업 꺼짐). " +
                    "카톡·문자 내용은 저장하지 않고 '누구와 언제'만 남깁니다. 카카오 공식 API에는 대화 기록을 읽는 기능이 없어, 내보내기 파일과 알림을 씁니다.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (confirmDeleteAll) ConfirmDialog(
        "전부 삭제", "등록한 사람, 모든 연락 기록, 미확인 이름이 지워집니다. 되돌릴 수 없습니다. 먼저 백업하는 것을 권장해요.", "삭제",
        onConfirm = vm::deleteAll, onDismiss = { confirmDeleteAll = false },
    )
    pending?.let { f ->
        var mode by rememberSaveable { mutableStateOf(ImportMode.ADD) }
        var sure by rememberSaveable { mutableStateOf(false) }
        val c = BackupCodec.counts(f)
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("백업 가져오기") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("사람 ${c["people"]}명 · 연락 기록 ${c["events"]}건 · 미확인 이름 ${c["unmatched"]}건")
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == ImportMode.ADD, { mode = ImportMode.ADD }); Text("새로 추가(지금 자료 유지)") }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == ImportMode.REPLACE, { mode = ImportMode.REPLACE }); Text("전체 교체(지금 상태를 먼저 내부 백업)") }
                    if (mode == ImportMode.REPLACE) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(sure, { sure = it }); Text("지금 자료가 백업 내용으로 바뀌는 것을 이해했습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (mode == ImportMode.ADD) Text("같은 사람이 이미 있으면 두 번 생깁니다. 다른 폰으로 옮길 때는 '전체 교체'가 맞아요.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = mode == ImportMode.ADD || sure, onClick = { vm.confirmImport(mode) }) { Text("가져오기") } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("취소") } },
        )
    }
}
