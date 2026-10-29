package com.miu.fold.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.IntakeViewModel
import com.miu.fold.dashboard.calendar.CalendarScreen
import com.miu.fold.dashboard.calendar.EventsViewModel
import com.miu.fold.dashboard.dashboard.HomeScreen
import com.miu.fold.dashboard.dashboard.HomeViewModel
import com.miu.fold.dashboard.notes.NotesViewModel
import com.miu.fold.dashboard.search.SearchScreen
import com.miu.fold.dashboard.search.SearchViewModel
import com.miu.fold.dashboard.settings.SettingsScreen
import com.miu.fold.dashboard.settings.SettingsViewModel
import com.miu.fold.dashboard.shortcuts.ShortcutsScreen
import com.miu.fold.dashboard.shortcuts.ShortcutsViewModel
import com.miu.fold.dashboard.timer.TimerScreen
import com.miu.fold.dashboard.timer.TimerViewModel
import com.miu.fold.dashboard.todos.TodosViewModel
import com.miu.fold.dashboard.ui.theme.Space
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    RECORDS("기록", Icons.AutoMirrored.Filled.List),
    CALENDAR("일정", Icons.Filled.DateRange),
    SETTINGS("설정", Icons.Filled.Settings),
}

enum class Overlay { NONE, SEARCH, TIMER, SHORTCUTS }
enum class RecordsTab { NOTES, TODOS }

/** 공유 ViewModel 묶음. 단일 Activity 범위라 화면 간에 같은 인스턴스다. */
class Vms(
    val home: HomeViewModel,
    val notes: NotesViewModel,
    val todos: TodosViewModel,
    val timer: TimerViewModel,
    val shortcuts: ShortcutsViewModel,
    val events: EventsViewModel,
    val search: SearchViewModel,
    val settings: SettingsViewModel,
)

@Composable
fun AppRoot(intake: IntakeViewModel) {
    val vms = Vms(
        home = appViewModel { c, _ -> HomeViewModel(c) },
        notes = appViewModel { c, s -> NotesViewModel(c, s) },
        todos = appViewModel { c, s -> TodosViewModel(c, s) },
        timer = appViewModel { c, _ -> TimerViewModel(c) },
        shortcuts = appViewModel { c, _ -> ShortcutsViewModel(c) },
        events = appViewModel { c, s -> EventsViewModel(c, s) },
        search = appViewModel { c, s -> SearchViewModel(c, s) },
        settings = appViewModel { c, _ -> SettingsViewModel(c) },
    )
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var recordsTab by rememberSaveable { mutableStateOf(RecordsTab.NOTES) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var noteEditorOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vms.timer.onAppResume()
        vms.todos.refreshToday()
        vms.events.refreshToday()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // 화면 이탈 시 미저장 초안을 즉시 저장하고 타이머 기준값을 갱신한다.
        vms.notes.editor.flush()
        vms.notes.quick.flush()
        vms.timer.onAppStop()
    }

    val nav = Nav(
        openNote = { id -> vms.notes.open(id); noteEditorOpen = true; recordsTab = RecordsTab.NOTES; tab = Tab.RECORDS; overlay = Overlay.NONE },
        newNote = { body -> vms.notes.newNote(body); noteEditorOpen = true; recordsTab = RecordsTab.NOTES; tab = Tab.RECORDS; overlay = Overlay.NONE },
        openNotes = { recordsTab = RecordsTab.NOTES; tab = Tab.RECORDS; overlay = Overlay.NONE },
        openTodos = { recordsTab = RecordsTab.TODOS; tab = Tab.RECORDS; overlay = Overlay.NONE },
        openTimer = { overlay = Overlay.TIMER },
        openCalendar = { d -> d?.let(vms.events::select); tab = Tab.CALENDAR; overlay = Overlay.NONE },
        openSearch = { overlay = Overlay.SEARCH },
        openShortcuts = { overlay = Overlay.SHORTCUTS },
        showMessage = { msg -> scope.launch { snackbar.showSnackbar(msg, withDismissAction = true) } },
        showUndo = { msg, undo ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(msg, actionLabel = "되돌리기", duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) undo()
            }
        },
    )

    BackHandler(enabled = overlay != Overlay.NONE || tab != Tab.HOME || noteEditorOpen) {
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            tab == Tab.RECORDS && noteEditorOpen -> { vms.notes.closeEditor(); noteEditorOpen = false }
            else -> tab = Tab.HOME
        }
    }

    val shared by intake.pending.collectAsStateWithLifecycle()
    shared?.let { s ->
        AlertDialog(
            onDismissRequest = { intake.consume() },
            title = { Text("공유받은 내용을 메모로 저장할까요?") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    s.subject?.let { Text("제목: $it", style = MaterialTheme.typography.labelLarge) }
                    if (s.truncated) Text("⚠ 너무 길어 앞부분 ${com.miu.fold.dashboard.notes.NoteRules.SHARED_TEXT_MAX}자만 저장됩니다.", color = MaterialTheme.colorScheme.error)
                    Text(s.text.take(2000) + if (s.text.length > 2000) "…" else "")
                }
            },
            confirmButton = { TextButton(onClick = { vms.notes.saveShared(s.text, s.subject); intake.consume(); nav.showMessage("새 메모로 저장했습니다.") }) { Text("메모로 저장") } },
            dismissButton = { TextButton(onClick = { intake.consume() }) { Text("저장 안 함") } },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val useRail = windowWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(windowInsets = WindowInsets.safeDrawing) {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t && overlay == Overlay.NONE,
                            onClick = { tab = t; overlay = Overlay.NONE },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!useRail) NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t && overlay == Overlay.NONE,
                                onClick = { tab = t; overlay = Overlay.NONE },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                    val available = maxWidth
                    Box(Modifier.fillMaxSize()) {
                        when (overlay) {
                            Overlay.SEARCH -> SearchScreen(vms, nav, onClose = { overlay = Overlay.NONE })
                            Overlay.TIMER -> TimerScreen(vms, onClose = { overlay = Overlay.NONE })
                            Overlay.SHORTCUTS -> ShortcutsScreen(vms, nav, onClose = { overlay = Overlay.NONE })
                            Overlay.NONE -> when (tab) {
                                Tab.HOME -> HomeScreen(vms, nav, available, windowWidth)
                                Tab.RECORDS -> RecordsScreen(
                                    vms, nav, available, recordsTab, { recordsTab = it },
                                    noteEditorOpen, { noteEditorOpen = it },
                                )
                                Tab.CALENDAR -> CalendarScreen(vms, nav, available)
                                Tab.SETTINGS -> SettingsScreen(vms, nav)
                            }
                        }
                    }
                }
            }
        }
    }
}
