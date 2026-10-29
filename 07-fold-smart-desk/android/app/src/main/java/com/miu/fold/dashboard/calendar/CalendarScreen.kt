package com.miu.fold.dashboard.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.data.LocalEventEntity
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Fmt
import com.miu.fold.dashboard.ui.LocalDatePickerDialog
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.ReadableWidth
import com.miu.fold.dashboard.ui.SectionTitle
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space
import java.time.LocalDate

@Composable
fun CalendarScreen(vms: Vms, nav: Nav, available: Dp) {
    val vm = vms.events
    val all by vm.all.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val selectedRaw by vm.selected.collectAsStateWithLifecycle()
    val selected = selectedRaw?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    var pick by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    ReadableWidth {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.select(selected.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 날") }
                Text(
                    Fmt.relativeDay(selected, today) + if (selected != today) "" else " · ${Fmt.date(today)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).clickable { pick = true }.padding(Space.s),
                )
                IconButton(onClick = { vm.select(selected.plusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 날") }
                TextButton(onClick = { vm.select(today) }) { Text("오늘") }
            }
            Text(
                "이 앱에만 저장되는 로컬 일정입니다. 시스템·Google 캘린더와 동기화하지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.l),
            )
            val dayEvents = vm.eventsOn(all, selected)
            val upcoming = (1..30L).map { selected.plusDays(it) }.map { d -> d to vm.eventsOn(all, d) }.filter { it.second.isNotEmpty() }
            LazyColumn(Modifier.weight(1f).padding(horizontal = Space.l)) {
                item {
                    // 월 달력: 화면이 넓어도 너무 커지지 않게 최대 너비를 둔다.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.layout.Box(Modifier.widthIn(max = 480.dp)) {
                            MonthGrid(
                                month = java.time.YearMonth.from(selected),
                                selected = selected, today = today,
                                hasEvents = { d -> vm.eventsOn(all, d).isNotEmpty() },
                                onSelect = vm::select,
                                onMonth = { m -> vm.select(m.atDay(minOf(selected.dayOfMonth, m.lengthOfMonth()))) },
                            )
                        }
                    }
                }
                item { SectionTitle(Fmt.date(selected)) }
                if (dayEvents.isEmpty()) item { EmptyState("이 날 일정이 없습니다.") }
                items(dayEvents, key = { "d-" + it.id }) { e -> EventRow(e, vm) { editId = e.id; vm.opened(e.id) } }
                item { HorizontalDivider(); SectionTitle("이후 30일") }
                if (upcoming.isEmpty()) item { EmptyState("예정된 일정이 없습니다.") }
                upcoming.forEach { (d, list) ->
                    item(key = "h-$d") { Text(Fmt.relativeDay(d, today), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Space.s)) }
                    items(list, key = { "u-$d-" + it.id }) { e -> EventRow(e, vm) { editId = e.id; vm.opened(e.id) } }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { creating = true }, modifier = Modifier.align(Alignment.End).padding(Space.l),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("일정 추가") },
            )
        }
    }
    if (pick) LocalDatePickerDialog(selected, onPick = vm::select, onDismiss = { pick = false })
    if (creating) EventDialog(null, selected, vms, onDismiss = { creating = false })
    editId?.let { id ->
        val e = all.firstOrNull { it.id == id }
        if (e == null) editId = null else EventDialog(e, selected, vms, onDismiss = { editId = null })
    }
}

@Composable
private fun EventRow(e: LocalEventEntity, vm: EventsViewModel, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        val time = if (e.allDay) {
            if (e.localDateStart == e.localDateEnd) "종일" else "종일 ~${e.localDateEnd.substring(5)}"
        } else listOfNotNull(e.startInstant?.let { Fmt.time(it, vm.zone()) }, e.endInstant?.let { Fmt.time(it, vm.zone()) }).joinToString("–")
        Text(time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = Space.m))
        Column(Modifier.weight(1f)) {
            Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.memo.isNotBlank()) Text(e.memo, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EventDialog(e: LocalEventEntity?, defaultDate: LocalDate, vms: Vms, onDismiss: () -> Unit) {
    val vm = vms.events
    val zone = vm.zone()
    var title by rememberSaveable { mutableStateOf(e?.title ?: "") }
    var allDay by rememberSaveable { mutableStateOf(e?.allDay ?: false) }
    var start by rememberSaveable { mutableStateOf(e?.localDateStart ?: defaultDate.toString()) }
    var end by rememberSaveable { mutableStateOf(e?.localDateEnd ?: defaultDate.toString()) }
    var startTime by rememberSaveable { mutableStateOf(e?.startInstant?.let { Fmt.time(it, zone) } ?: "09:00") }
    var endTime by rememberSaveable { mutableStateOf(e?.endInstant?.let { Fmt.time(it, zone) } ?: "10:00") }
    var memo by rememberSaveable { mutableStateOf(e?.memo ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pickStart by rememberSaveable { mutableStateOf(false) }
    var pickEnd by rememberSaveable { mutableStateOf(false) }
    // 시간 지정 일정을 다른 시간대에서 편집하면 원래 시간대를 알려준다.
    val zoneNote = e?.zoneId?.takeIf { !e.allDay && it != zone.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e == null) "일정 추가" else "일정 편집") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(title, { title = it.take(300) }, label = { Text("제목") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("종일", Modifier.weight(1f)); Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(selected = true, onClick = { pickStart = true }, label = { Text("시작 ${Fmt.date(LocalDate.parse(start))}") })
                    FilterChip(selected = true, onClick = { pickEnd = true }, label = { Text("종료 ${Fmt.date(LocalDate.parse(end))}") })
                }
                if (!allDay) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    OutlinedTextField(startTime, { startTime = it.take(5) }, label = { Text("시작 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(endTime, { endTime = it.take(5) }, label = { Text("종료 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                zoneNote?.let { Text("원래 시간대: $it (현재 기기 시간대로 표시 중)", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(memo, { memo = it.take(5000) }, label = { Text("메모") }, minLines = 2)
                error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
                if (e != null) TextButton(onClick = { vm.delete(e.id); onDismiss() }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = EventRules.build(allDay, LocalDate.parse(start), LocalDate.parse(end), startTime, endTime, zone)) {
                    is EventInputResult.Error -> error = r.message
                    is EventInputResult.Ok -> { vm.save(e?.id, title, r.span, r.zoneId, memo); onDismiss() }
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
    if (pickStart) LocalDatePickerDialog(LocalDate.parse(start), onPick = {
        start = it.toString(); if (LocalDate.parse(end).isBefore(it)) end = it.toString()
    }, onDismiss = { pickStart = false })
    if (pickEnd) LocalDatePickerDialog(LocalDate.parse(end), onPick = { end = it.toString() }, onDismiss = { pickEnd = false })
}
