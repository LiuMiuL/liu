package com.miu.lastcontact.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miu.lastcontact.AppContainer
import com.miu.lastcontact.LastContactApp
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.Direction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 단일 Activity 범위에서 같은 타입 ViewModel은 화면 간 공유된다. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val app = LocalContext.current.applicationContext as LastContactApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container, createSavedStateHandle()) } })
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth().padding(Space.l), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ReadableWidth(modifier: Modifier = Modifier, max: Int = 760, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max.dp).fillMaxWidth()) { content() }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 날짜만 주고받는 날짜 선택기. 미래 날짜는 고를 수 없다(연락 기록은 지난 일). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastDatePickerDialog(initial: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
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
    private val date = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
    private val short = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
    private val time = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    fun date(d: LocalDate): String = date.format(d)
    fun short(d: LocalDate, today: LocalDate): String = if (d.year == today.year) short.format(d) else date.format(d)
    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun dateTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(ms).atZone(zone).let { "${short.format(it)} ${time.format(it)}" }

    fun channel(c: Channel?, d: Direction? = null): String {
        val base = c?.label ?: "알 수 없음"
        val dir = d?.label?.takeIf { it.isNotEmpty() }
        return if (dir != null) "$base · $dir" else base
    }
}
