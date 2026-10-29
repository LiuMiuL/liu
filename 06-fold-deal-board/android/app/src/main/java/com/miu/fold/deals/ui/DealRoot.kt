package com.miu.fold.deals.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 목록 패널 최소 너비와 읽기·편집 패널 최소 너비(초기 설계값). */
val LIST_PANE_MIN = 240.dp
val DETAIL_PANE_MIN = 320.dp

/**
 * 현재 창 너비로 패널 구성을 정한다. 기종명·물리 해상도는 보지 않는다.
 * 600dp 미만은 단일 패널, 그 이상은 두 패널 최소 너비를 모두 확보할 때만 2패널.
 */
fun listPaneWidth(windowWidth: Dp): Dp? {
    if (windowWidth < 600.dp) return null
    val gutter = Space.l
    if (windowWidth < LIST_PANE_MIN + DETAIL_PANE_MIN + gutter) return null
    val preferred = if (windowWidth >= 840.dp) windowWidth * 0.38f else LIST_PANE_MIN + 20.dp
    return preferred.coerceIn(LIST_PANE_MIN, minOf(420.dp, windowWidth - DETAIL_PANE_MIN - gutter))
}

@Composable
fun DealRoot(vm: AppViewModel) {
    val route by vm.route.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messageFlow.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = route != Route.Board) { vm.back() }

    // 스낵바는 화면 머리줄(뒤로·저장 버튼) 아래에 띄워 아래쪽 매물 추가·비교 버튼과 위쪽 이동 버튼을 모두 가리지 않게 한다.
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (!ui.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@BoxWithConstraints
            }
            val listWidth = listPaneWidth(maxWidth)
            if (listWidth == null) {
                RouteContent(vm, route, ui, twoPane = false)
            } else {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(listWidth).fillMaxHeight()) {
                        BoardScreen(vm, ui, selectedId = (route as? Route.Detail)?.id ?: (route as? Route.Edit)?.id)
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (route == Route.Board) EmptyDetailPane() else RouteContent(vm, route, ui, twoPane = true)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(top = 64.dp))
        }
    }
}

@Composable
private fun RouteContent(vm: AppViewModel, route: Route, ui: UiState, twoPane: Boolean) {
    when (route) {
        Route.Board -> BoardScreen(vm, ui, selectedId = null)
        is Route.Detail -> DetailScreen(vm, ui, route.id, showBack = !twoPane)
        is Route.Edit -> EditorScreen(vm, isNew = route.id == null)
        Route.Compare -> CompareScreen(vm, ui, showBack = !twoPane)
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) {
        Text("왼쪽에서 매물을 선택하거나 2~4개를 골라 비교하세요.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
