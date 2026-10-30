package com.miu.videograb.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.videograb.data.DownloadJob
import com.miu.videograb.data.JobStatus
import com.miu.videograb.engine.QualityPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(vm: MainViewModel, onFirstDownload: () -> Unit) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DownloadJob?>(null) }
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messageFlow.collect { snackbar.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("VideoGrab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("다른 앱에서 공유 → VideoGrab 을 누르면 바로 저장돼요.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showSettings = true; vm.loadEngineVersion() }) { Text("설정") }
                    }
                }
                item(key = "input") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            OutlinedTextField(
                                value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
                                label = { Text("영상 링크 (여러 개면 줄마다 하나)") }, minLines = 1, maxLines = 4,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                OutlinedButton(onClick = { clipboardText(context)?.let { input = it } }) { Text("붙여넣기") }
                                Button(
                                    onClick = { onFirstDownload(); vm.enqueue(input) { input = "" } },
                                    enabled = input.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                ) { Text("다운로드") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                QualityPreset.entries.forEach { p ->
                                    FilterChip(selected = settings.preset == p, onClick = { vm.setPreset(p) },
                                        label = { Text((if (settings.preset == p) "✓ " else "") + p.label) })
                                }
                            }
                        }
                    }
                }
                val list = jobs
                when {
                    list == null -> Unit
                    list.isEmpty() -> item(key = "empty") {
                        Text("아직 받은 영상이 없어요. 링크를 붙여넣거나 다른 앱에서 공유해 보세요.",
                            Modifier.padding(vertical = Space.xl), style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> {
                        item(key = "listHeader") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("받은 목록 ${list.size}개", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = vm::clearFinished) { Text("완료 항목 지우기") }
                            }
                        }
                        items(list, key = { it.id }) { job ->
                            JobCard(job, vm, onOpen = { openFile(context, job, vm) }, onDetail = { detail = job })
                        }
                    }
                }
                item(key = "notice") {
                    Text("본인이 권리를 가졌거나 다운로드가 허용된 영상만 저장하세요. 각 사이트의 이용약관과 저작권을 지켜야 합니다. " +
                        "저작권 보호(DRM)가 걸린 영상은 받지 않습니다.",
                        Modifier.padding(top = Space.l), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showSettings) SettingsDialog(vm, settings.wifiOnly, settings.autoUpdateEngine) { showSettings = false }
    detail?.let { job ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(job.errorMessage ?: job.status.label) },
            text = { Text(listOfNotNull(job.url, job.errorDetail?.let { "오류 원문:\n$it" }).joinToString("\n\n")) },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("닫기") } },
        )
    }
}

@Composable
private fun JobCard(job: DownloadJob, vm: MainViewModel, onOpen: () -> Unit, onDetail: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(job.title ?: job.url, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val preset = QualityPreset.fromName(job.preset).label
            val statusText = when (job.status) {
                JobStatus.RUNNING -> listOfNotNull(
                    job.progress?.let { "%.0f%%".format(it) } ?: "준비 중",
                    job.etaSeconds?.takeIf { it > 0 }?.let { "남은 시간 ${formatEta(it)}" },
                ).joinToString(" · ")
                JobStatus.DONE -> "완료" + (job.totalBytes?.let { " · ${formatBytes(it)}" } ?: "") +
                    if (job.outputCount > 1) " · 파일 ${job.outputCount}개" else ""
                JobStatus.FAILED -> "실패 · ${job.errorMessage ?: ""}"
                else -> job.status.label + if (job.restarts > 0 && job.status == JobStatus.QUEUED) " (중단돼 다시 받는 중)" else ""
            }
            Text("$statusText · $preset", style = MaterialTheme.typography.bodyMedium,
                color = if (job.status == JobStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (job.status == JobStatus.RUNNING || job.status == JobStatus.SAVING) {
                val p = job.progress
                if (p == null || job.status == JobStatus.SAVING) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                when (job.status) {
                    JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.SAVING ->
                        TextButton(onClick = { vm.cancel(job) }) { Text("취소") }
                    JobStatus.DONE -> {
                        TextButton(onClick = onOpen) { Text("열기") }
                        TextButton(onClick = { vm.remove(job) }) { Text("목록에서 지우기") }
                    }
                    JobStatus.FAILED, JobStatus.CANCELLED -> {
                        TextButton(onClick = { vm.retry(job) }) { Text("다시 시도") }
                        if (job.status == JobStatus.FAILED) TextButton(onClick = onDetail) { Text("자세히") }
                        TextButton(onClick = { vm.remove(job) }) { Text("지우기") }
                    }
                }
            }
            if (job.status == JobStatus.FAILED && job.suggestEngineUpdate) {
                Text("설정 → 다운로드 엔진 업데이트 후 다시 시도해 보세요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun SettingsDialog(vm: MainViewModel, wifiOnly: Boolean, autoUpdate: Boolean, onDismiss: () -> Unit) {
    val engineStatus by vm.engineStatus.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wi-Fi에서만 받기")
                        Text("모바일 데이터에서는 Wi-Fi 연결까지 기다려요. 다음에 추가하는 링크부터 적용.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = wifiOnly, onCheckedChange = vm::setWifiOnly)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("엔진 자동 업데이트")
                        Text("하루 한 번, 그리고 사이트가 바뀌어 실패했을 때 yt-dlp 안정판을 받아 그 링크를 한 번 더 시도해요.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoUpdate, onCheckedChange = vm::setAutoUpdateEngine)
                }
                Text("다운로드 엔진: ${engineStatus ?: "확인 중…"}")
                Text("유튜브 등 사이트가 바뀌면 받기가 실패할 수 있어요. 그럴 때 엔진을 업데이트하세요(인터넷으로 yt-dlp 최신 안정판을 받음).",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::updateEngine, enabled = !updating) { Text(if (updating) "업데이트 중…" else "다운로드 엔진 업데이트") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

private fun clipboardText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }

private fun openFile(context: Context, job: DownloadJob, vm: MainViewModel) {
    val uri = job.firstOutputUri ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 열 앱이 없거나 파일이 지워진 경우: 목록 항목만 남아 있다.
    } catch (_: SecurityException) {
    }
}

fun formatEta(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1fGB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1fMB".format(b / (1L shl 20).toDouble())
    else -> "%dKB".format(b / 1024)
}
