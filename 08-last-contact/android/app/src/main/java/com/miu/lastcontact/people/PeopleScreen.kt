package com.miu.lastcontact.people

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.lastcontact.core.Names
import com.miu.lastcontact.core.Phones
import com.miu.lastcontact.data.PersonEntity
import com.miu.lastcontact.data.PersonSummary
import com.miu.lastcontact.data.SavePersonResult
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.ContactRules
import com.miu.lastcontact.domain.Direction
import com.miu.lastcontact.domain.SortMode
import com.miu.lastcontact.ui.ConfirmDialog
import com.miu.lastcontact.ui.EmptyState
import com.miu.lastcontact.ui.Fmt
import com.miu.lastcontact.ui.PastDatePickerDialog
import com.miu.lastcontact.ui.ReadableWidth
import com.miu.lastcontact.ui.Space
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 알림·되돌리기 안내를 화면 밖(스낵바)으로 넘긴다. */
class Messages(val show: (String) -> Unit, val undo: (String, () -> Unit) -> Unit)

@Composable
fun PeopleScreen(vm: PeopleViewModel, msg: Messages, available: Dp) {
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val twoPane = available >= 600.dp
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // "new" 또는 사람 ID
    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            PeopleList(vm, msg, Modifier.width((available * 0.42f).coerceAtLeast(300.dp)).fillMaxHeight(), onAdd = { editing = "new" })
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (selected == null) EmptyState("왼쪽에서 사람을 고르세요.", Modifier.align(Alignment.Center))
                else PersonDetail(vm, msg, showBack = false, onEdit = { editing = it })
            }
        }
    } else {
        if (selected == null) PeopleList(vm, msg, Modifier.fillMaxSize(), onAdd = { editing = "new" })
        else PersonDetail(vm, msg, showBack = true, onEdit = { editing = it })
    }
    editing?.let { id ->
        PersonEditDialog(vm, if (id == "new") null else id, onDone = { saved -> editing = null; saved?.let { vm.select(it) } })
    }
}

@Composable
private fun PeopleList(vm: PeopleViewModel, msg: Messages, modifier: Modifier, onAdd: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs, top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("연락 기록", style = MaterialTheme.typography.titleLarge)
                if (ui.total > 0) Text(
                    if (ui.overdueCount > 0) "${ui.total}명 중 ${ui.overdueCount}명이 연락할 때가 지났어요" else "${ui.total}명 · 모두 목표 안에 있어요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { searching = !searching; if (!searching) vm.setQuery("") }) { Icon(Icons.Filled.Search, contentDescription = "검색") }
        }
        if (searching) OutlinedTextField(
            q, vm::setQuery, Modifier.fillMaxWidth().padding(horizontal = Space.l), singleLine = true,
            placeholder = { Text("이름·카톡 이름으로 찾기") },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.l, vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SortMode.entries.forEach { m -> FilterChip(selected = sort == m, onClick = { vm.setSort(m) }, label = { Text(m.label) }) }
        }
        if (ui.groups.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FilterChip(selected = group == null, onClick = { vm.setGroup(null) }, label = { Text("전체") })
            ui.groups.forEach { g -> FilterChip(selected = group == g, onClick = { vm.setGroup(if (group == g) null else g) }, label = { Text(g) }) }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (ui.loaded && ui.total == 0) item {
                EmptyState("아직 등록한 사람이 없습니다.\n'사람 추가'로 가족·친구를 등록하거나, 가져오기 탭에서 카톡 대화를 가져오세요.")
            } else if (ui.loaded && ui.items.isEmpty()) item { EmptyState("조건에 맞는 사람이 없습니다.") }
            items(ui.items, key = { it.person.id }) { ps ->
                PersonRow(ps, today, onOpen = { vm.select(ps.person.id) }, onToday = {
                    vm.contactedToday(ps.person.id) { undo ->
                        if (undo != null) msg.undo("${ps.person.name}: 오늘 연락함으로 기록했습니다", undo)
                        else msg.show("${ps.person.name}: 오늘은 이미 기록돼 있어요")
                    }
                })
                HorizontalDivider()
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd, modifier = Modifier.align(Alignment.End).padding(Space.l),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("사람 추가") },
        )
    }
}

@Composable
private fun PersonRow(ps: PersonSummary, today: LocalDate, onOpen: () -> Unit, onToday: () -> Unit) {
    val p = ps.person
    val last = ps.last
    val over = ContactRules.overdueBy(last?.day, p.goalDays, today)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = "자세히", onClick = onOpen).padding(start = Space.l, end = Space.xs, top = Space.s, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.groupName.isNotBlank()) Text("  ${p.groupName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                buildString {
                    append(ContactRules.relative(last?.day, today))
                    if (last != null) append(" · ${Fmt.short(last.day, today)} · ${last.channel.label}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            // 색만으로 구분하지 않도록 문구에 상태를 쓴다.
            when {
                over == null -> {}
                over == Long.MAX_VALUE -> Text("⚠ 목표 ${p.goalDays}일 · 아직 기록 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                over > 0 -> Text("⚠ 목표 ${p.goalDays}일 · ${over}일 지남", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> Text("목표 ${p.goalDays}일 · ${-over}일 남음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onToday) { Icon(Icons.Filled.Check, contentDescription = "${p.name} 오늘 연락함") }
    }
}

@Composable
private fun PersonDetail(vm: PeopleViewModel, msg: Messages, showBack: Boolean, onEdit: (String) -> Unit) {
    val d by vm.detail.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val p = d.person ?: run { EmptyState("불러오는 중…"); return }
    var pickDay by rememberSaveable { mutableStateOf(false) }
    var noteFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteEvent by rememberSaveable { mutableStateOf<String?>(null) }
    val last = d.byChannel.firstOrNull()?.day

    ReadableWidth {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.l)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBack) IconButton(onClick = { vm.select(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "목록으로") }
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Filled.Edit, contentDescription = "편집") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "사람 삭제") }
                }
                Text(
                    "마지막 연락: " + ContactRules.relative(last, today) + (last?.let { " (${Fmt.date(it)})" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                ContactRules.overdueBy(last, p.goalDays, today)?.let { over ->
                    Text(
                        when { over == Long.MAX_VALUE -> "목표 ${p.goalDays}일마다 · 아직 기록 없음"; over > 0 -> "목표 ${p.goalDays}일마다 · ${over}일 지남"; else -> "목표 ${p.goalDays}일마다 · ${-over}일 남음" },
                        color = if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val info = listOfNotNull(
                    p.groupName.takeIf { it.isNotBlank() }?.let { "그룹: $it" },
                    Names.splitAliases(p.aliases).takeIf { it.isNotEmpty() }?.let { "카톡 이름: ${it.joinToString(", ")}" },
                    p.phones.split(',').filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { "번호: ${it.joinToString(", ") { n -> Phones.display(n) }}" },
                    p.memo.takeIf { it.isNotBlank() },
                )
                info.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FlowRow(Modifier.padding(vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Button(onClick = {
                        vm.contactedToday(p.id) { undo ->
                            if (undo != null) msg.undo("오늘 연락함으로 기록했습니다", undo) else msg.show("오늘은 이미 기록돼 있어요")
                        }
                    }) { Text("오늘 연락함") }
                    OutlinedButton(onClick = { pickDay = true }) { Text("날짜 골라 기록") }
                }
                if (d.byChannel.isNotEmpty()) {
                    Text("경로별 마지막 연락", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    d.byChannel.forEach { Text("· ${it.channel.label}: ${Fmt.short(it.day, today)} (${ContactRules.relative(it.day, today)})") }
                }
                HorizontalDivider(Modifier.padding(vertical = Space.m))
                Text("연락 기록 ${d.eventCount}건" + if (d.eventCount > d.events.size) " (최근 ${d.events.size}건 표시)" else "", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                if (d.events.isEmpty()) EmptyState("아직 기록이 없습니다.")
            }
            items(d.events, key = { it.id }) { e ->
                val day = LocalDate.parse(e.day)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.date(day) + (e.atMillis?.let { " " + Fmt.time(it) } ?: ""), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Fmt.channel(Channel.of(e.channel), Direction.of(e.direction)) + if (e.note.isNotBlank()) " · ${e.note}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { deleteEvent = e.id }) { Icon(Icons.Filled.Delete, contentDescription = "이 기록 삭제") }
                }
            }
        }
    }
    if (pickDay) PastDatePickerDialog(today, today, onPick = { day -> noteFor = day.toString() }, onDismiss = { pickDay = false })
    noteFor?.let { dayStr ->
        var note by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("${Fmt.date(LocalDate.parse(dayStr))} 연락") },
            text = { OutlinedTextField(note, { note = it.take(200) }, label = { Text("메모(선택) 예: 전화, 만남") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.recordOn(p.id, LocalDate.parse(dayStr), note) { msg.show(it) }; noteFor = null }) { Text("기록") } },
            dismissButton = { TextButton(onClick = { noteFor = null }) { Text("취소") } },
        )
    }
    if (confirmDelete) ConfirmDialog(
        "${p.name} 삭제", "이 사람과 연락 기록 ${d.eventCount}건이 모두 지워집니다. 되돌릴 수 없으니 필요하면 먼저 설정에서 백업하세요.", "삭제",
        onConfirm = { vm.deletePerson(p.id) }, onDismiss = { confirmDelete = false },
    )
    deleteEvent?.let { id ->
        ConfirmDialog("기록 삭제", "이 날의 연락 기록 1건을 지웁니다.", "삭제", onConfirm = { vm.deleteEvent(id) }, onDismiss = { deleteEvent = null })
    }
}

/** 시스템 연락처 선택기로 '전화번호 하나'를 고른다. 고른 항목에만 일시적 읽기 권한이 주어지므로 READ_CONTACTS가 필요 없다. */
private class PickPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

private fun readPicked(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
    )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else null }
}.getOrNull()

private val goalPresets = listOf(null to "없음", 7 to "1주", 14 to "2주", 30 to "1달", 90 to "3달")

@Composable
internal fun PersonEditDialog(vm: PeopleViewModel, id: String?, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val existing: PersonEntity? = id?.let { pid -> ui.items.firstOrNull { it.person.id == pid }?.person ?: vm.detail.value.person?.takeIf { it.id == pid } }
    var name by rememberSaveable(id) { mutableStateOf(existing?.name ?: "") }
    var aliases by rememberSaveable(id) { mutableStateOf(existing?.aliases ?: "") }
    var phones by rememberSaveable(id) { mutableStateOf(existing?.phones?.split(',')?.filter { it.isNotBlank() }?.joinToString(", ") { Phones.display(it) } ?: "") }
    var group by rememberSaveable(id) { mutableStateOf(existing?.groupName ?: "") }
    var goal by rememberSaveable(id) { mutableStateOf(existing?.goalDays?.toString() ?: "") }
    var memo by rememberSaveable(id) { mutableStateOf(existing?.memo ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(PickPhone()) { uri ->
        uri?.let { readPicked(context, it) }?.let { (n, num) ->
            if (name.isBlank()) name = n
            val list = Phones.split(phones) + listOfNotNull(Phones.normalize(num))
            phones = list.distinct().joinToString(", ") { Phones.display(it) }
        }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (existing == null) "사람 추가" else "사람 편집") },
        text = {
            // 내용이 화면보다 길 때 대화상자 높이가 정해지지 않고 계속 다시 재는 문제를 막기 위해 최대 높이를 둔다.
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                FilledTonalButton(onClick = { runCatching { pick.launch(Unit) } }) { Text("연락처에서 이름·번호 가져오기") }
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("이름") }, singleLine = true)
                OutlinedTextField(aliases, { aliases = it.take(300) }, label = { Text("카톡 이름(다르면, 줄마다 하나)") }, minLines = 2)
                OutlinedTextField(phones, { phones = it.take(200) }, label = { Text("전화번호(쉼표로 여러 개)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(group, { group = it.take(20) }, label = { Text("그룹 예: 가족, 친구") }, singleLine = true)
                if (ui.groups.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    ui.groups.forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) }) }
                }
                Text("연락 목표 주기", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    goalPresets.forEach { (v, l) -> FilterChip(selected = goal == (v?.toString() ?: ""), onClick = { goal = v?.toString() ?: "" }, label = { Text(l) }) }
                }
                OutlinedTextField(goal, { v -> goal = v.filter { it.isDigit() }.take(4) }, label = { Text("직접(일)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(memo, { memo = it.take(2000) }, label = { Text("메모") }, minLines = 2)
                error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (val r = vm.savePerson(existing?.id, name, aliases, phones, group, goal.toIntOrNull(), memo)) {
                        is SavePersonResult.Invalid -> error = r.reason
                        is SavePersonResult.Saved -> onDone(r.person.id)
                    }
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("취소") } },
    )
}
