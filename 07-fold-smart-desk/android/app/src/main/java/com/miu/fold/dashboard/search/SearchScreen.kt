package com.miu.fold.dashboard.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.dashboard.LaunchFailedDialog
import com.miu.fold.dashboard.dashboard.handleLaunch
import com.miu.fold.dashboard.recent.RecentType
import com.miu.fold.dashboard.shortcuts.LaunchResult
import com.miu.fold.dashboard.ui.EmptyState
import com.miu.fold.dashboard.ui.Nav
import com.miu.fold.dashboard.ui.ReadableWidth
import com.miu.fold.dashboard.ui.Vms
import com.miu.fold.dashboard.ui.theme.Space
import java.time.LocalDate

@Composable
fun SearchScreen(vms: Vms, nav: Nav, onClose: () -> Unit) {
    val vm = vms.search
    val query by vm.query.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val ui by vm.results.collectAsStateWithLifecycle()
    val shortcuts by vms.shortcuts.shortcuts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var failed by remember { mutableStateOf<LaunchResult.NotFound?>(null) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ReadableWidth {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(Space.s), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                OutlinedTextField(
                    value = query, onValueChange = vm::setQuery, singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text("메모·할 일·일정·바로가기 검색") },
                )
            }
            FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                RecentType.entries.forEach { t -> FilterChip(selected = t in types, onClick = { vm.toggle(t) }, label = { Text(t.label) }) }
            }
            Text(
                "이 앱에 저장된 자료만 검색합니다(다른 앱·PC·웹은 검색하지 않음).",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.xs),
            )
            LazyColumn(Modifier.weight(1f)) {
                if (ui.searched && ui.hits.isEmpty()) item { EmptyState("'${ui.query}' 검색 결과가 없습니다.") }
                items(ui.hits, key = { it.type.key + it.id }) { h ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                            when (h.type) {
                                RecentType.NOTE -> nav.openNote(h.id)
                                RecentType.TODO -> nav.openTodos()
                                RecentType.EVENT -> nav.openCalendar(runCatching { LocalDate.parse(h.subtitle) }.getOrNull())
                                RecentType.SHORTCUT -> shortcuts.firstOrNull { it.id == h.id }?.let { handleLaunch(context, it, vms, nav) { f -> failed = f } }
                            }
                        }.padding(horizontal = Space.l, vertical = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h.type.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(h.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    failed?.let { f -> LaunchFailedDialog(f, vms, nav, onDismiss = { failed = null }) }
}
