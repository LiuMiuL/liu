package com.miu.lastcontact.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.lastcontact.IntakeViewModel
import com.miu.lastcontact.Shared
import com.miu.lastcontact.importer.ImportScreen
import com.miu.lastcontact.importer.ImportViewModel
import com.miu.lastcontact.people.Messages
import com.miu.lastcontact.people.PeopleScreen
import com.miu.lastcontact.people.PeopleViewModel
import com.miu.lastcontact.settings.SettingsScreen
import com.miu.lastcontact.settings.SettingsViewModel
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    PEOPLE("사람", Icons.Filled.Person),
    IMPORT("가져오기", Icons.Filled.Refresh),
    SETTINGS("설정", Icons.Filled.Settings),
}

@Composable
fun AppRoot(intake: IntakeViewModel) {
    val people = appViewModel { c, s -> PeopleViewModel(c, s) }
    val importer = appViewModel { c, s -> ImportViewModel(c, s) }
    val settings = appViewModel { c, _ -> SettingsViewModel(c) }
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.PEOPLE) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val msg = remember {
        Messages(
            show = { m -> scope.launch { snackbar.showSnackbar(m, withDismissAction = true) } },
            undo = { m, undo ->
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    if (snackbar.showSnackbar(m, actionLabel = "되돌리기", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
                }
            },
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        people.refreshToday()
        importer.onAppResume(context.applicationContext)
    }

    // 카톡 '대화 내보내기' 공유로 들어온 경우 가져오기 화면으로 보낸다.
    val shared by intake.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        when (val s = shared) {
            is Shared.File -> { tab = Tab.IMPORT; importer.readExport { context.contentResolver.openInputStream(s.uri) }; intake.consume() }
            is Shared.Text -> { tab = Tab.IMPORT; importer.readExportText(s.text); intake.consume() }
            null -> {}
        }
    }

    val selected by people.selectedId.collectAsStateWithLifecycle()
    val exportState by importer.export.collectAsStateWithLifecycle()
    BackHandler(enabled = tab != Tab.PEOPLE || selected != null || exportState is com.miu.lastcontact.importer.ExportState.Review) {
        when {
            tab == Tab.IMPORT && exportState is com.miu.lastcontact.importer.ExportState.Review -> importer.cancelExport()
            tab != Tab.PEOPLE -> tab = Tab.PEOPLE
            else -> people.select(null)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (rail) NavigationRail(windowInsets = WindowInsets.safeDrawing) {
                Tab.entries.forEach { t ->
                    NavigationRailItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!rail) NavigationBar {
                        Tab.entries.forEach { t -> NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(t.label) }) }
                    }
                },
            ) { padding ->
                BoxWithConstraints(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
                    when (tab) {
                        Tab.PEOPLE -> PeopleScreen(people, msg, maxWidth)
                        Tab.IMPORT -> ImportScreen(importer, msg)
                        Tab.SETTINGS -> SettingsScreen(settings, msg)
                    }
                }
            }
        }
    }
}
