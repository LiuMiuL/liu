package com.miu.fold.dashboard.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Fmt
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.ReadableWidth
import com.miu.fold.dashboard.ui.SectionTitle
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space

/** 홈 카드용 축약 타이머. */
@Composable
fun TimerCard(vms: Vms, nav: Nav) {
    val ui by vms.timer.ui.collectAsStateWithLifecycle()
    val settings by vms.settings.settings.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        TimerDisplay(ui)
        TimerControls(ui, settings.focusMinutes, settings.breakMinutes, vms, label = "집중")
        TimerAlertStatus(compact = true)
        androidx.compose.material3.TextButton(onClick = nav.openTimer) { Text("타이머 상세·기록") }
    }
}

@Composable
private fun TimerDisplay(ui: TimerUi) {
    val s = ui.snapshot
    val stateText = when (s?.state) {
        null, TimerState.IDLE -> "대기"
        TimerState.RUNNING -> "진행 중"
        TimerState.PAUSED -> "일시정지"
        TimerState.COMPLETED -> "완료"
        TimerState.CANCELLED -> "종료됨"
        TimerState.INTERRUPTED -> "재부팅으로 타이머가 중단됨"
    }
    val active = s?.state?.isActive == true
    Text(
        if (active) TimerFormat.mmss(ui.remainingMs) else "--:--",
        style = MaterialTheme.typography.displayMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(
        buildString {
            append(stateText)
            if (s != null && (active || s.state == TimerState.COMPLETED)) append(" · ${s.label}")
        },
        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TimerControls(ui: TimerUi, focusMin: Int, breakMin: Int, vms: Vms, label: String, todoId: String? = null) {
    val s = ui.snapshot
    val startAsk = rememberStartWithNotificationAsk(vms)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        when (s?.state) {
            TimerState.RUNNING -> {
                Button(onClick = vms.timer::pause) { Text("일시정지") }
                OutlinedButton(onClick = vms.timer::cancel) { Text("종료") }
                OutlinedButton(onClick = vms.timer::restart) { Text("다시 시작") }
            }
            TimerState.PAUSED -> {
                Button(onClick = vms.timer::resume) { Text("재개") }
                OutlinedButton(onClick = vms.timer::cancel) { Text("종료") }
                OutlinedButton(onClick = vms.timer::restart) { Text("다시 시작") }
            }
            TimerState.INTERRUPTED -> {
                Button(onClick = vms.timer::resume) { Text("남은 ${TimerFormat.mmss(s.remainingMs)} 재개") }
                OutlinedButton(onClick = vms.timer::cancel) { Text("종료") }
            }
            else -> {
                Button(onClick = { startAsk { vms.timer.start(focusMin, label.ifBlank { "집중" }, todoId) } }) { Text("집중 ${focusMin}분") }
                OutlinedButton(onClick = { startAsk { vms.timer.start(breakMin, "휴식", null) } }) { Text("휴식 ${breakMin}분") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(vms: Vms, onClose: () -> Unit) {
    val ui by vms.timer.ui.collectAsStateWithLifecycle()
    val records by vms.timer.records.collectAsStateWithLifecycle()
    val settings by vms.settings.settings.collectAsStateWithLifecycle()
    val todos by vms.todos.items.collectAsStateWithLifecycle()
    var label by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf("") }
    var todoId by rememberSaveable { mutableStateOf<String?>(null) }
    val idle = ui.snapshot?.state?.isActive != true
    val startAsk = rememberStartWithNotificationAsk(vms)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("집중 타이머") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") } },
        )
        ReadableWidth {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                item { TimerDisplay(ui) }
                item {
                    TimerControls(ui, settings.focusMinutes, settings.breakMinutes, vms, label, todoId)
                }
                if (idle) {
                    item {
                        OutlinedTextField(
                            value = label, onValueChange = { label = it.take(60) },
                            label = { Text("세션 목적(선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val open = todos.filter { it.todo.doneAt == null }.take(8)
                    if (open.isNotEmpty()) item {
                        Column {
                            Text("연결할 할 일(선택)", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                open.forEach { t ->
                                    FilterChip(
                                        selected = todoId == t.todo.id,
                                        onClick = {
                                            todoId = if (todoId == t.todo.id) null else t.todo.id
                                            if (label.isBlank() && todoId != null) label = t.todo.title.take(60)
                                        },
                                        label = { Text(t.todo.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            OutlinedTextField(
                                value = custom, onValueChange = { v -> custom = v.filter { it.isDigit() }.take(3) },
                                label = { Text("직접 지정(분)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { custom.toIntOrNull()?.let { m -> startAsk { vms.timer.start(m, label.ifBlank { "집중" }, todoId) } } },
                                enabled = (custom.toIntOrNull() ?: 0) in 1..600,
                            ) { Text("시작") }
                        }
                    }
                }
                item { TimerAlertStatus(compact = false) }
                item {
                    Text(
                        "· 다음 세션 자동 시작: 꺼짐\n· 재부팅되면 완료로 처리하지 않고 '중단됨'으로 표시해 남은 시간 재개를 선택할 수 있습니다(재부팅 중에는 알림 없음).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { HorizontalDivider(); SectionTitle("집중 기록") }
                if (records.isEmpty()) item { EmptyState("아직 기록이 없습니다.") }
                items(records, key = { it.sessionId }) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                        Text(Fmt.dateTime(r.endedAt), Modifier.weight(1f))
                        Text(if (r.outcome == FocusOutcome.COMPLETED.name) "완료" else "취소", Modifier.padding(end = Space.m))
                        Text("${r.elapsedMs / 60_000}분 집중")
                    }
                }
            }
        }
    }
}
