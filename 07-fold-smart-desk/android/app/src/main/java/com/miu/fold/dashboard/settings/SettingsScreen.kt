package com.miu.fold.dashboard.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.miu.fold.dashboard.BuildConfig
import com.miu.fold.dashboard.backup.BackupCodec
import com.miu.fold.dashboard.backup.ImportMode
import com.miu.fold.dashboard.dashboard.LayoutProfile
import com.miu.fold.dashboard.ui.ConfirmDialog
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.ReadableWidth
import com.miu.fold.dashboard.ui.SectionTitle
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space

@Composable
fun SettingsScreen(vms: Vms, nav: Nav) {
    val vm = vms.settings
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var resetProfile by rememberSaveable { mutableStateOf<LayoutProfile?>(null) }

    LaunchedEffect(message) { message?.let { nav.showMessage(it); vm.consumeMessage() } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export { context.contentResolver.openOutputStream(uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readImport { context.contentResolver.openInputStream(uri) }
    }

    ReadableWidth {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            SectionTitle("테마")
            ThemeMode.entries.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = s.theme == m, onClick = { vm.setTheme(m) })
                    Text(m.label)
                }
            }
            HorizontalDivider()
            SectionTitle("집중 타이머")
            Stepper("집중 시간", s.focusMinutes, 1..180) { vm.setFocus(it) }
            Stepper("휴식 시간", s.breakMinutes, 1..60) { vm.setBreak(it) }
            Text("다음 세션 자동 시작은 꺼져 있습니다. 앱 밖 종료 알림은 아직 없습니다.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            SectionTitle("최근 항목")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최근 목록에서 메모·할 일 제목 숨기기", Modifier.weight(1f))
                Switch(checked = s.hideRecentContent, onCheckedChange = vm::setHideRecent)
            }
            TextButton(onClick = vm::clearRecent) { Text("최근 항목 기록 지우기") }
            HorizontalDivider()
            SectionTitle("홈 배치")
            Text("배치 초기화는 선택한 화면의 카드 순서·크기·숨김만 기본값으로 되돌립니다. 메모·할 일·바로가기 데이터는 지워지지 않습니다.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                LayoutProfile.entries.forEach { p -> OutlinedButton(onClick = { resetProfile = p }) { Text("${p.label} 배치 초기화") } }
            }
            HorizontalDivider()
            SectionTitle("백업·복원")
            Text(
                "JSON 파일로 메모·할 일·일정·바로가기·집중 기록·홈 배치·설정을 내보냅니다. 평문 파일이므로 개인 내용이 그대로 들어 있습니다. " +
                    "이 앱은 로그인 토큰·API 키를 저장하지 않으므로 백업에도 포함되지 않습니다. 진행 중인 타이머는 포함되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(onClick = { exportLauncher.launch(
                    "fold-smart-desk-backup-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".json",
                ) }) { Text("백업 내보내기") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("백업 가져오기") }
            }
            HorizontalDivider()
            SectionTitle("정보")
            Text(
                "Fold Smart Desk ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "다른 앱·서버·계정 없이 동작하는 독립 앱입니다. 네트워크·위치·연락처 권한을 요청하지 않습니다. " +
                    "데이터는 이 기기 앱 내부 저장소에만 저장되며 별도의 앱 수준 암호화는 하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    resetProfile?.let { p ->
        ConfirmDialog(
            "${p.label} 배치 초기화", "${p.label} 배치만 기본값으로 되돌립니다. 데이터는 지워지지 않습니다.", "초기화",
            onConfirm = { vms.home.resetLayout(p); nav.showMessage("${p.label} 배치를 초기화했습니다.") },
            onDismiss = { resetProfile = null },
        )
    }

    pending?.let { p ->
        var mode by rememberSaveable { mutableStateOf(ImportMode.ADD_AS_NEW) }
        var confirmReplace by rememberSaveable { mutableStateOf(false) }
        val counts = BackupCodec.counts(p.file)
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("백업 가져오기 확인") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("앱 버전 ${p.file.header.appVersion.take(20)} · 스키마 ${p.file.header.schemaVersion}")
                    Text("메모 ${counts["notes"]} · 할 일 ${counts["todos"]} · 일정 ${counts["events"]} · 바로가기 ${counts["shortcuts"]} · 집중 기록 ${counts["focusRecords"]} · 카드 ${counts["cards"]}")
                    p.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == ImportMode.ADD_AS_NEW, onClick = { mode = ImportMode.ADD_AS_NEW })
                        Text("새 데이터로 추가 (기존 자료·배치 유지)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == ImportMode.REPLACE_ALL, onClick = { mode = ImportMode.REPLACE_ALL })
                        Text("전체 교체 (현재 상태를 먼저 내부 백업)")
                    }
                    if (mode == ImportMode.REPLACE_ALL) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmReplace, onCheckedChange = { confirmReplace = it })
                        Text("현재 메모·할 일·일정·배치가 백업 내용으로 바뀌는 것을 이해했습니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = mode == ImportMode.ADD_AS_NEW || confirmReplace,
                    onClick = { vm.confirmImport(mode) },
                ) { Text("가져오기") }
            },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("취소") } },
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${value}분", Modifier.weight(1f))
        FilterChip(selected = false, onClick = { onChange((value - 5).coerceIn(range)) }, label = { Text("−5") })
        TextButton(onClick = { onChange((value - 1).coerceIn(range)) }) { Text("−1") }
        TextButton(onClick = { onChange((value + 1).coerceIn(range)) }) { Text("+1") }
        FilterChip(selected = false, onClick = { onChange((value + 5).coerceIn(range)) }, label = { Text("+5") })
    }
}
