package com.miu.fold.dashboard.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.data.DashboardCardEntity
import com.miu.fold.dashboard.ui.ConfirmDialog
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vms: Vms, nav: Nav, available: Dp, window: Dp) {
    val vm = vms.home
    val state by vm.state.collectAsStateWithLifecycle()
    val edit by vm.edit.collectAsStateWithLifecycle()
    val columns = GridPolicy.columnsFor(available.value.toInt(), window.value.toInt())
    val currentProfile = GridPolicy.profileFor(columns)
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Fold Smart Desk", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                IconButton(onClick = nav.openSearch) { Icon(Icons.Filled.Search, contentDescription = "검색") }
                if (edit == null) IconButton(onClick = { vm.startEdit(currentProfile) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "배치 편집")
                }
            },
        )
        val s = state ?: run {
            EmptyState("불러오는 중…")
            return@Column
        }
        val e = edit
        if (e != null) {
            EditBar(
                e, currentProfile,
                onSave = vm::saveEdit, onCancel = vm::cancelEdit,
                onAdd = { showAdd = true }, onReset = { confirmReset = true },
            )
        }
        val placements = e?.draft ?: s.layouts[currentProfile].orEmpty()
        val cardsById = s.cards.associateBy { it.id }
        val shown = if (e != null) LayoutEditor.sorted(placements).map { it.copy(span = it.span.coerceIn(1, columns)) }
        else LayoutEditor.renderable(placements, columns)
        val withCards = shown.mapNotNull { p -> cardsById[p.cardId]?.let { p to it } }

        if (withCards.isEmpty()) {
            EmptyState(
                if (e == null) "표시할 카드가 없습니다. 오른쪽 위 편집 버튼으로 카드를 다시 표시하거나 추가하세요."
                else "카드가 없습니다. '카드 추가'를 누르세요.",
            )
            if (e == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = { vm.startEdit(currentProfile) }) { Text("배치 편집") }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            items(withCards, key = { it.first.cardId }, span = { GridItemSpan(it.first.span) }) { (p, card) ->
                CardFrame(
                    card = card, placement = p, editing = e != null, columns = columns,
                    profile = e?.profile ?: currentProfile,
                    onLongPress = { vm.startEdit(currentProfile) },
                    onMove = { d -> vm.move(card.id, d) },
                    onSpan = { sp -> vm.setSpan(card.id, sp, card.type) },
                    onVisible = { v -> vm.setVisible(card.id, v) },
                    onDelete = { vm.deleteCard(card) },
                    onOpen = { openCard(card, nav) },
                ) { CardContent(card, p.span, vms, nav) }
            }
        }
    }

    if (showAdd) {
        val s = state
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("카드 추가") },
            text = {
                Column {
                    val addable = CardType.entries.filter { t -> (s?.cards?.count { it.type == t.typeId } ?: 0) < t.maxInstances }
                    if (addable.isEmpty()) Text("모든 종류의 카드가 이미 있습니다. 숨긴 카드는 편집 모드에서 '표시'를 누르세요.")
                    addable.forEach { t ->
                        TextButton(onClick = { vm.addCard(t); showAdd = false }) { Text(t.defaultTitle) }
                    }
                    Spacer(Modifier.padding(Space.xs))
                    Text(
                        "PC 상태·AI 답변 등 다른 앱 연동 카드는 해당 앱과 공유 계약이 생긴 뒤 추가됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("닫기") } },
        )
    }
    if (confirmReset) {
        val profile = edit?.profile ?: currentProfile
        ConfirmDialog(
            title = "${profile.label} 배치 초기화",
            text = "${profile.label} 배치(순서·크기·숨김)만 기본값으로 되돌립니다. 다른 화면 배치와 메모·할 일·바로가기 데이터는 지워지지 않습니다.",
            confirm = "초기화",
            onConfirm = { vm.resetLayout(profile) },
            onDismiss = { confirmReset = false },
        )
    }
}

private fun openCard(card: DashboardCardEntity, nav: Nav) {
    when (CardType.fromTypeId(card.type)) {
        CardType.QUICK_NOTE -> nav.openNotes()
        CardType.TODAY_TODOS -> nav.openTodos()
        CardType.FOCUS_TIMER -> nav.openTimer()
        CardType.SHORTCUTS -> nav.openShortcuts()
        CardType.TODAY_EVENTS -> nav.openCalendar(null)
        CardType.RECENT -> nav.openSearch()
        null -> Unit
    }
}

@Composable
private fun EditBar(
    e: LayoutEdit,
    current: LayoutProfile,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
            Text("배치 편집 중: ${e.profile.label} 배치", style = MaterialTheme.typography.titleSmall)
            if (e.profile != current) {
                Text(
                    "현재 창은 ${current.label}입니다. 편집 중인 ${e.profile.label} 배치에만 저장되며, 저장하거나 취소하면 현재 배치로 돌아갑니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Button(onClick = onSave) { Text("저장") }
                OutlinedButton(onClick = onCancel) { Text("취소") }
                TextButton(onClick = onAdd) { Text("카드 추가") }
                TextButton(onClick = onReset) { Text("이 배치 초기화") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardFrame(
    card: DashboardCardEntity,
    placement: Placement,
    editing: Boolean,
    columns: Int,
    profile: LayoutProfile,
    onLongPress: () -> Unit,
    onMove: (Int) -> Unit,
    onSpan: (Int) -> Unit,
    onVisible: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    val type = CardType.fromTypeId(card.type)
    ElevatedCard(Modifier.fillMaxWidth().alpha(if (editing && !placement.visible) 0.55f else 1f)) {
        Column(Modifier.padding(Space.l)) {
            // 머리글만 '열기/길게 눌러 편집'을 받는다. 카드 안 체크박스·버튼과 충돌하지 않게 한다.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .combinedClickable(
                        enabled = !editing,
                        onClickLabel = "열기",
                        onLongClickLabel = "배치 편집",
                        onClick = onOpen,
                        onLongClick = onLongPress,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    card.title.ifBlank { type?.defaultTitle ?: "카드" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (editing && !placement.visible) Text("숨김", style = MaterialTheme.typography.labelMedium)
            }
            if (editing) {
                EditControls(type, placement, columns, profile, onMove, onSpan, onVisible, onDelete)
            } else if (type == null) {
                Text("지원하지 않는 카드입니다(${card.type.take(40)}). 앱을 업데이트하거나 삭제하세요.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDelete) { Text("카드 삭제") }
            } else {
                content()
            }
        }
    }
}

@Composable
private fun EditControls(
    type: CardType?,
    p: Placement,
    columns: Int,
    profile: LayoutProfile,
    onMove: (Int) -> Unit,
    onSpan: (Int) -> Unit,
    onVisible: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "앞으로") }
            IconButton(onClick = { onMove(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "뒤로") }
            if (columns > 1) {
                IconButton(onClick = { onMove(-columns) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "위로") }
                IconButton(onClick = { onMove(columns) }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "아래로") }
            }
        }
        val spans = (type?.supportedSpans ?: setOf(1)).filter { it <= GridPolicy.maxSpan(profile) }.sorted()
        if (spans.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                spans.forEach { sp ->
                    FilterChip(
                        selected = p.span == sp, onClick = { onSpan(sp) },
                        label = { Text("${sp}열") },
                        modifier = Modifier.semantics { contentDescription = "너비 ${sp}열" },
                    )
                }
            }
            if (p.span > columns) Text("현재 창에서는 ${columns}열로 줄여 표시됩니다.", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedButton(onClick = { onVisible(!p.visible) }) { Text(if (p.visible) "숨기기" else "표시") }
            TextButton(onClick = { confirmDelete = true }) { Text("카드 삭제") }
        }
    }
    if (confirmDelete) ConfirmDialog(
        title = "카드 삭제",
        text = "홈에서 이 카드를 모든 배치에서 제거합니다. 메모·할 일 등 실제 데이터는 지워지지 않으며 '카드 추가'로 다시 만들 수 있습니다. 숨기기만 하려면 '숨기기'를 쓰세요.",
        confirm = "삭제",
        onConfirm = onDelete,
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun CardContent(card: DashboardCardEntity, span: Int, vms: Vms, nav: Nav) {
    when (CardType.fromTypeId(card.type)) {
        CardType.QUICK_NOTE -> QuickNoteCard(vms, nav)
        CardType.TODAY_TODOS -> TodayTodosCard(vms, nav)
        CardType.FOCUS_TIMER -> com.miu.fold.dashboard.timer.TimerCard(vms, nav)
        CardType.SHORTCUTS -> ShortcutsCard(vms, nav, span)
        CardType.TODAY_EVENTS -> TodayEventsCard(vms, nav)
        CardType.RECENT -> RecentCard(vms, nav)
        null -> Unit
    }
}
