package com.miu.fold.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miu.fold.dashboard.core.SaveStatus
import com.miu.fold.dashboard.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 앱 내부 탐색 동작 모음. 화면은 이 인터페이스만 알고 실제 탭 전환은 AppRoot가 한다. */
class Nav(
    val openNote: (String) -> Unit,
    val newNote: (String) -> Unit,
    val openNotes: () -> Unit,
    val openTodos: () -> Unit,
    val openTimer: () -> Unit,
    val openCalendar: (LocalDate?) -> Unit,
    val openSearch: () -> Unit,
    val openShortcuts: () -> Unit,
    val showMessage: (String) -> Unit,
    val showUndo: (String, () -> Unit) -> Unit,
)

/** 긴 본문이 넓은 화면 전체로 퍼지지 않게 최대 너비를 둔다. */
@Composable
fun ReadableWidth(modifier: Modifier = Modifier, max: Int = 840, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max.dp).fillMaxWidth()) { content() }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth().padding(Space.l),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun SaveStatusLabel(status: SaveStatus, error: String?, onRetry: () -> Unit) {
    // 색만으로 구분하지 않도록 문구에 상태를 쓴다.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        val (text, isError) = when (status) {
            SaveStatus.IDLE -> "" to false
            SaveStatus.DIRTY -> "입력 중…" to false
            SaveStatus.SAVING -> "저장 중…" to false
            SaveStatus.SAVED -> "저장됨" to false
            SaveStatus.FAILED -> "⚠ 저장 실패" to true
        }
        if (text.isNotEmpty()) Text(
            text, style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status == SaveStatus.FAILED) TextButton(onClick = onRetry) { Text("다시 저장") }
    }
    if (status == SaveStatus.FAILED && error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** Material DatePicker는 UTC 자정 millis를 쓴다. LocalDate로만 주고받아 시간대 밀림을 막는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("선택") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    ) { DatePicker(state) }
}

object Fmt {
    private val date = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val dateTime = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

    fun date(d: LocalDate): String = date.format(d)
    fun dateTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = dateTime.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun relativeDay(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        today.minusDays(1) -> "어제"
        else -> date(d)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.padding(vertical = Space.s), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}
