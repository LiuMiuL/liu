package com.miu.fold.deals.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.deals.comparison.CompareTable
import com.miu.fold.deals.comparison.ComparisonCalculator
import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.listings.UserRating

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun BoardScreen(vm: AppViewModel, ui: UiState, selectedId: String?) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
                Text("Fold Deal Board", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("수동 기록 모드 · 자동 가격 추적 없음 · 직접 기록한 값만 비교합니다",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = filter.query, onValueChange = vm::setQuery, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                    placeholder = { Text("모델·제목·지역·메모 검색") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "검색어 지우기")
                        }
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    SortOrder.entries.forEach { s ->
                        FilterChip(selected = filter.sort == s, onClick = { vm.setSort(s) }, label = { Text(s.label) })
                    }
                    FilterChip(selected = filter.onSaleOnly, onClick = { vm.setOnSaleOnly(!filter.onSaleOnly) },
                        label = { Text("판매 중만") })
                    FilterChip(selected = filter.hideExcluded, onClick = { vm.setHideExcluded(!filter.hideExcluded) },
                        label = { Text("'제외' 숨김") })
                }
            }
            when {
                ui.active.isEmpty() -> EmptyBoard(onAdd = { vm.startNew() })
                ui.visible.isEmpty() -> Box(Modifier.fillMaxWidth().padding(Space.xl)) {
                    Text("검색·필터 조건에 맞는 매물이 없어요.", style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Space.l, end = Space.l, bottom = 160.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    items(ui.visible, key = { it.listing.id }) { item ->
                        ListingCard(
                            item = item,
                            selected = item.listing.id == selectedId,
                            inCompare = item.listing.id in ui.compareIds,
                            onOpen = { vm.navigate(Route.Detail(item.listing.id)) },
                            onToggleCompare = { vm.toggleCompare(item.listing.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(Space.l), horizontalAlignment = Alignment.End) {
            if (ui.compareIds.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 3.dp, shape = MaterialTheme.shapes.large) {
                    Row(Modifier.padding(horizontal = Space.m, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        Text("비교 ${ui.compareItems.size}/${AppViewModel.MAX_COMPARE}")
                        TextButton(onClick = vm::clearCompare) { Text("해제") }
                        Button(onClick = { vm.navigate(Route.Compare) }, enabled = ui.compareItems.size >= 2) { Text("비교하기") }
                    }
                }
                Spacer(Modifier.size(Space.s))
            }
            ExtendedFloatingActionButton(
                onClick = { vm.startNew() },
                modifier = Modifier.testTag("add-listing"),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("매물 추가") },
            )
        }
    }
}

@Composable
private fun EmptyBoard(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text("아직 저장한 매물이 없어요.", style = MaterialTheme.typography.titleMedium)
        Text("중고거래 앱에서 매물을 '공유 → Fold Deal Board'로 보내거나, 직접 추가하세요. 가격을 몰라도 저장할 수 있어요.",
            style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAdd) { Text("첫 매물 추가") }
    }
}

@Composable
fun ListingCard(
    item: ListingWithObservations,
    selected: Boolean,
    inCompare: Boolean,
    onOpen: () -> Unit,
    onToggleCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val l = item.listing
    val obs = item.latestPosted
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(start = Space.m, top = Space.s, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (selected) "▶ " else "") + l.displayName,
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val price = obs?.let { CompareTable.priceText(it) } ?: "가격 미확인"
                val total = obs?.totalCostKrw?.let { "총 ${ComparisonCalculator.formatKrw(it)}" }
                    ?: obs?.totalCostIssue?.let { "총비용 미확인($it)" } ?: "총비용 미확인"
                Text("$price · $total", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val badges = buildList {
                    add(l.tradeStatus.label)
                    if (l.userRating != UserRating.NONE) add(l.userRating.label)
                    l.platform?.let(::add)
                    add(obs?.let { "확인 ${formatTime(it.observedAt)}" } ?: "가격 확인일 없음")
                }
                Text(badges.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = if (l.tradeStatus == TradeStatus.SOLD) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(
                    checked = inCompare, onCheckedChange = { onToggleCompare() },
                    modifier = Modifier.semantics { contentDescription = "${l.displayName} 비교에 ${if (inCompare) "포함됨" else "추가"}" },
                )
                Text("비교", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
