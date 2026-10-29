package com.miu.fold.dashboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.miu.fold.dashboard.notes.NotesScreen
import com.miu.fold.dashboard.todos.TodosScreen

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    vms: Vms,
    nav: Nav,
    available: Dp,
    tab: RecordsTab,
    onTab: (RecordsTab) -> Unit,
    editorOpen: Boolean,
    setEditorOpen: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            Tab(selected = tab == RecordsTab.NOTES, onClick = { onTab(RecordsTab.NOTES) }, text = { Text("메모") })
            Tab(selected = tab == RecordsTab.TODOS, onClick = { onTab(RecordsTab.TODOS) }, text = { Text("할 일") })
        }
        when (tab) {
            RecordsTab.NOTES -> NotesScreen(vms, nav, available, editorOpen, setEditorOpen)
            RecordsTab.TODOS -> TodosScreen(vms, nav)
        }
    }
}
