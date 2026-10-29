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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.deals.comparison.CompareTable
import com.miu.fold.deals.comparison.ComparisonCalculator
import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.listings.Category
import com.miu.fold.deals.listings.FilterFacets
import com.miu.fold.deals.listings.SpecFilter
import com.miu.fold.deals.listings.SpecNormalizer
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.listings.UserRating

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun BoardScreen(vm: AppViewModel, ui: UiState, selectedId: String?) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // 머리말·필터까지 한 목록에 넣어 좁은 화면·큰 글꼴에서도 함께 스크롤된다.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.s, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            item(key = "header") {
                Column {
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
                        val n = filter.spec.activeCount
                        FilterChip(selected = showFilters || n > 0, onClick = { showFilters = !showFilters },
                            label = { Text((if (showFilters) "▲ " else "▼ ") + "조건" + if (n > 0) " ($n)" else "") })
                        SortOrder.entries.forEach { s ->
                            FilterChip(selected = filter.sort == s, onClick = { vm.setSort(s) }, label = { Text(s.label) })
                        }
                        FilterChip(selected = filter.onSaleOnly, onClick = { vm.setOnSaleOnly(!filter.onSaleOnly) },
                            label = { Text("판매 중만") })
                        FilterChip(selected = filter.hideExcluded, onClick = { vm.setHideExcluded(!filter.hideExcluded) },
                            label = { Text("'제외' 숨김") })
                    }
                }
            }
            // 결과 수를 조건 패널 위에 두어 칩을 누르면서 바로 확인할 수 있게 한다.
            if (filter.spec.activeCount > 0 && ui.active.isNotEmpty()) item(key = "summary") {
                FilterSummary(ui, filter.spec, vm)
            }
            if (showFilters) item(key = "filters") { SpecFilterPanel(filter.spec, ui.facets, vm) }
            when {
                ui.active.isEmpty() -> item(key = "empty") { EmptyBoard(onAdd = { vm.startNew() }) }
                ui.visible.isEmpty() -> item(key = "none") {
                    Text("검색·조건에 맞는 매물이 없어요.", Modifier.padding(vertical = Space.xl), style = MaterialTheme.typography.bodyLarge)
                }
                else -> items(ui.visible, key = { it.listing.id }) { item ->
                    ListingCard(
                        item = item,
                        selected = item.listing.id == selectedId,
                        inCompare = item.listing.id in ui.compareIds,
                        onOpen = { vm.navigate(Route.Detail(item.listing.id)) },
                        onToggleCompare = { vm.toggleCompare(item.listing.id) },
                        needsCheck = ui.unknownFields[item.listing.id],
                        modifier = Modifier.animateItem(),
                    )
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
    needsCheck: List<String>? = null,
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
                if (!needsCheck.isNullOrEmpty()) {
                    Text("? 확인 필요: ${needsCheck.joinToString(", ")} 미확인", style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.tertiary)
                }
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

private val PUBLISHED_DAYS = listOf(1, 3, 7, 30)
private val CHECKED_DAYS = listOf(1, 7, 30)

/** 분류·기종·RAM·저장공간·등록일·가격 확인일 조건. 선택지는 저장한 매물에 있는 값만 보여준다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecFilterPanel(spec: SpecFilter, facets: FilterFacets, vm: AppViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            ChipLine("분류") {
                Chip("전체", spec.category == null) { vm.updateSpec { it.copy(category = null) } }
                Category.entries.forEach { c ->
                    Chip(c.label, spec.category == c) { vm.updateSpec { it.copy(category = if (it.category == c) null else c) } }
                }
            }
            ChipLine("등록일") {
                Chip("전체", spec.publishedWithinDays == null) { vm.updateSpec { it.copy(publishedWithinDays = null) } }
                PUBLISHED_DAYS.forEach { d ->
                    Chip(daysLabel(d), spec.publishedWithinDays == d) {
                        vm.updateSpec { it.copy(publishedWithinDays = if (it.publishedWithinDays == d) null else d) }
                    }
                }
            }
            ChipLine("가격 확인일") {
                Chip("전체", spec.checkedWithinDays == null) { vm.updateSpec { it.copy(checkedWithinDays = null) } }
                CHECKED_DAYS.forEach { d ->
                    Chip(daysLabel(d), spec.checkedWithinDays == d) {
                        vm.updateSpec { it.copy(checkedWithinDays = if (it.checkedWithinDays == d) null else d) }
                    }
                }
            }
            ChipLine("기종 (여러 개 선택 가능)") {
                if (facets.models.isEmpty()) Text("저장한 모델 없음", style = MaterialTheme.typography.bodySmall)
                facets.models.forEach { m -> Chip(m, m in spec.models) { vm.updateSpec { it.copy(models = it.models.toggle(m)) } } }
            }
            ChipLine("RAM") {
                if (facets.ramGb.isEmpty()) Text("저장한 RAM 값 없음", style = MaterialTheme.typography.bodySmall)
                facets.ramGb.forEach { r -> Chip("${r}GB", r in spec.ramGb) { vm.updateSpec { it.copy(ramGb = it.ramGb.toggle(r)) } } }
            }
            ChipLine("저장공간") {
                if (facets.storageGb.isEmpty()) Text("저장한 용량 값 없음", style = MaterialTheme.typography.bodySmall)
                facets.storageGb.forEach { g ->
                    Chip(SpecNormalizer.formatStorage(g), g in spec.storageGb) {
                        vm.updateSpec { it.copy(storageGb = it.storageGb.toggle(g)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("미확인 값도 보기", spec.includeUnknown) { vm.updateSpec { it.copy(includeUnknown = !it.includeUnknown) } }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::clearSpec, enabled = spec.activeCount > 0 || spec.includeUnknown) { Text("조건 초기화") }
            }
            Text("등록일은 입력한 날짜 기준이며, '3일 전' 같은 표현은 처음 저장한 시각으로 계산합니다. 날짜를 모르는 매물은 기간 조건을 통과시키지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterSummary(ui: UiState, spec: SpecFilter, vm: AppViewModel) {
    val passed = ui.visible.size - if (spec.includeUnknown) ui.needsCheck.size else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "조건 충족 ${passed}개" + if (ui.needsCheck.isNotEmpty()) " · 확인 필요 ${ui.needsCheck.size}개" +
                (if (spec.includeUnknown) "(아래 표시)" else "(숨김)") else "",
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
        )
        if (ui.needsCheck.isNotEmpty()) TextButton(onClick = { vm.updateSpec { it.copy(includeUnknown = !it.includeUnknown) } }) {
            Text(if (spec.includeUnknown) "숨기기" else "보기")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipLine(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) { content() }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text((if (selected) "✓ " else "") + text) })
}

private fun daysLabel(d: Int) = if (d == 1) "24시간" else if (d == 7) "일주일" else "${d}일"

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v
