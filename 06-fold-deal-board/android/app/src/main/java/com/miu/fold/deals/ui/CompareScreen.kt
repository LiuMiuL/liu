package com.miu.fold.deals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.deals.comparison.CompareRow
import com.miu.fold.deals.comparison.CompareTable
import com.miu.fold.deals.comparison.Comparison
import com.miu.fold.deals.comparison.ComparisonCalculator
import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.comparison.SameSpecCriteria
import com.miu.fold.deals.listings.SpecNormalizer
import com.miu.fold.deals.listings.TradeStatus

private enum class Basis(val label: String, val row: String) { PRICE("가격 기준", "판매가"), TOTAL("총비용 기준", "총비용") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareScreen(vm: AppViewModel, ui: UiState, showBack: Boolean) {
    val budget by vm.budgetText.collectAsStateWithLifecycle()
    val diffOnly by vm.diffOnly.collectAsStateWithLifecycle()
    val includeUnknown by vm.includeUnknownRows.collectAsStateWithLifecycle()
    val forceTable by vm.forceTable.collectAsStateWithLifecycle()
    var basis by rememberSaveable { mutableStateOf(Basis.TOTAL) }
    val items = ui.compareItems

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = { vm.navigate(Route.Board) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("비교 (${items.size}개)", Modifier.weight(1f).padding(start = Space.s), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.l).padding(bottom = Space.xxl)) {
            if (items.size < 2) {
                Text("비교하려면 보드에서 2~4개 매물을 선택하세요.", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { vm.navigate(Route.Board) }) { Text("보드로") }
            } else {
                val match = ComparisonCalculator.specMatchState(items.map { it.listing })
                Text(
                    (if (match == ComparisonCalculator.SpecMatch.SAME) "✓ " else "! ") + match.label,
                    fontWeight = FontWeight.SemiBold,
                    color = if (match == ComparisonCalculator.SpecMatch.SAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                OutlinedTextField(
                    value = budget, onValueChange = vm::setBudget, singleLine = true,
                    label = { Text("예산 (예: 65만)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FilterChip(diffOnly, { vm.setDiffOnly(!diffOnly) }, label = { Text("다른 항목만") })
                    FilterChip(includeUnknown, { vm.setIncludeUnknownRows(!includeUnknown) }, label = { Text("전부 미확인인 행 포함") })
                    Basis.entries.forEach { b -> FilterChip(basis == b, { basis = b }, label = { Text(b.label) }) }
                    FilterChip(forceTable, { vm.setForceTable(!forceTable) }, label = { Text("항상 표로 보기") })
                }

                val rows = CompareTable.build(items, vm.budgetKrw())
                    .filter { !diffOnly || it.differs }
                    .filter { includeUnknown || !it.allUnknown }
                val lowest = lowestIndexes(rows.firstOrNull { it.label == basis.row }, items)
                val names = items.map { it.listing.displayName }

                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Space.m)) {
                    val fontScale = LocalDensity.current.fontScale
                    val labelWidth = (104.dp * fontScale).coerceAtMost(200.dp)
                    val colWidth = (140.dp * fontScale).coerceAtMost(280.dp)
                    val cardMode = !forceTable && maxWidth - labelWidth < colWidth * 1.2f
                    if (cardMode) DiffCards(names, rows, lowest, basis.row) else Table(names, rows, lowest, basis.row, labelWidth, colWidth)
                }
                Text("≠ 표시는 값이 다른 행, '? 미확인'은 확인되지 않은 값입니다. 미확인을 좋은 상태로 보지 않습니다.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Space.s))

                FlowRow(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Button(onClick = vm::saveComparison) { Text("비교 저장") }
                    OutlinedButton(onClick = vm::clearCompare) { Text("선택 해제") }
                }

                AggregateSection(ui, items.first())
            }
            SavedComparisons(ui, vm)
        }
    }
}

/** 기준 행에서 최저 금액 칸. 미확인 칸은 후보에서 빠진다. */
private fun lowestIndexes(row: CompareRow?, items: List<ListingWithObservations>): Set<Int> {
    if (row == null) return emptySet()
    val values = items.map { if (row.label == "총비용") it.latestPosted?.totalCostKrw else it.latestPosted?.let { o ->
        if (o.qualifier == com.miu.fold.deals.observations.PriceQualifier.NONE) o.amountKrw else null } }
    val min = values.filterNotNull().minOrNull() ?: return emptySet()
    return values.indices.filter { values[it] == min }.toSet()
}

@Composable
private fun rowHeight(lines: Int): Dp {
    val density = LocalDensity.current
    val lh = MaterialTheme.typography.bodyMedium.lineHeight
    return with(density) { (lh * lines).toDp() } + Space.l
}

@Composable
private fun Table(names: List<String>, rows: List<CompareRow>, lowest: Set<Int>, basisRow: String, labelWidth: Dp, colWidth: Dp) {
    val h = rowHeight(2)
    val hScroll = rememberScrollState()
    Row(Modifier.fillMaxWidth()) {
        // 고정 항목 열
        Column(Modifier.width(labelWidth)) {
            Cell("항목", h, header = true)
            rows.forEach { r -> Cell((if (r.differs) "≠ " else "") + r.label, h, header = true, emphasize = r.differs) }
        }
        Column(Modifier.horizontalScroll(hScroll)) {
            Row { names.forEach { Cell(it, h, header = true, width = colWidth) } }
            rows.forEach { r ->
                Row {
                    r.cells.forEachIndexed { i, c ->
                        val mark = if (r.label == basisRow && i in lowest) " ▼최저" else ""
                        Cell(c.value?.plus(mark), h, width = colWidth, emphasize = r.differs)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String?, height: Dp, header: Boolean = false, emphasize: Boolean = false, width: Dp? = null) {
    val bg = if (emphasize) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
    Box(
        (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).height(height).background(bg)
            .padding(horizontal = Space.s, vertical = Space.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text == null) {
            Text("? 미확인", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (header || emphasize) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
    HorizontalDivider(if (width != null) Modifier.width(width) else Modifier)
}

/** 좁은 폭·큰 글꼴에서 쓰는 매물별 카드. */
@Composable
private fun DiffCards(names: List<String>, rows: List<CompareRow>, lowest: Set<Int>, basisRow: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        names.forEachIndexed { i, name ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Space.m)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    rows.forEach { r ->
                        val mark = if (r.label == basisRow && i in lowest) " ▼최저" else ""
                        FieldRow((if (r.differs) "≠ " else "") + r.label, r.cells[i].value?.plus(mark))
                    }
                }
            }
        }
    }
}

@Composable
private fun AggregateSection(ui: UiState, base: ListingWithObservations) {
    val l = base.listing
    val criteria = SameSpecCriteria(l.modelCanonical, l.ramGb, l.storageGb)
    SectionTitle("같은 조건 저장 매물 집계")
    if (l.modelCanonical == null || l.ramGb == null || l.storageGb == null) {
        Text("첫 번째 매물의 모델·RAM·저장공간 중 미확인 값이 있어 같은 조건 집계를 할 수 없어요. (확인 필요)",
            style = MaterialTheme.typography.bodyMedium)
        return
    }
    val agg = ComparisonCalculator.aggregate(ui.all, criteria, System.currentTimeMillis())
    Text("기준: ${l.modelCanonical} · RAM ${l.ramGb}GB · ${SpecNormalizer.formatStorage(l.storageGb)} · ${TradeStatus.SOLD.label} 제외 · 총비용",
        style = MaterialTheme.typography.bodySmall)
    FieldRow("표본", "저장한 매물 ${agg.sampleCount}개 기준" + if (agg.sampleInsufficient) " · 표본 부족" else "")
    FieldRow("최저", agg.minKrw?.let(ComparisonCalculator::formatKrw))
    FieldRow("중앙값", agg.medianKrw?.let(ComparisonCalculator::formatKrw))
    FieldRow("범위", if (agg.minKrw != null) "${ComparisonCalculator.formatKrw(agg.minKrw)} ~ ${ComparisonCalculator.formatKrw(agg.maxKrw)}" else null)
    Text("전체 시장 시세나 실거래가가 아닙니다. 매물당 최신 게시 가격 1개만 사용합니다.", style = MaterialTheme.typography.bodySmall)
    if (agg.excluded.isNotEmpty()) {
        Text("집계 제외 ${agg.excluded.size}개", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.s))
        agg.excluded.take(10).forEach { Text("· ${it.listing.displayName}: ${it.reason}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SavedComparisons(ui: UiState, vm: AppViewModel) {
    if (ui.comparisons.isEmpty()) return
    SectionTitle("저장한 비교 (${ui.comparisons.size})")
    val obsById = ui.all.flatMap { it.observations }.associateBy { it.id }
    ui.comparisons.forEach { c -> SavedComparisonCard(c, ui, obsById, vm) }
}

@Composable
private fun SavedComparisonCard(
    c: Comparison, ui: UiState, obsById: Map<String, com.miu.fold.deals.observations.PriceObservation>, vm: AppViewModel,
) {
    val ids = c.listingIdList
    val usedObs = c.observationIdList
    val notes = buildList {
        ids.forEachIndexed { i, id ->
            val item = ui.find(id)
            when {
                item == null -> add("기록에 없는 매물")
                item.listing.deletedAt != null -> add("삭제된 매물 포함: ${item.listing.displayName}")
                item.listing.tradeStatus == TradeStatus.SOLD -> add("판매 완료: ${item.listing.displayName}")
            }
            if (item != null && item.latestPosted?.id != usedObs.getOrNull(i)) add("새 관측값 있음: ${item.listing.displayName}")
        }
    }
    Card(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Column(Modifier.padding(Space.m)) {
            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("저장 ${formatTime(c.createdAt)}" + (c.budgetKrw?.let { " · 예산 ${ComparisonCalculator.formatKrw(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall)
            ids.forEachIndexed { i, id ->
                val name = ui.find(id)?.listing?.displayName ?: "알 수 없는 매물"
                val o = usedObs.getOrNull(i)?.let(obsById::get)
                val price = o?.let { it.totalCostKrw?.let { v -> "총 ${ComparisonCalculator.formatKrw(v)}" } ?: CompareTable.priceText(it) } ?: "가격 미확인"
                Text("· $name — 당시 $price" + (o?.let { " (${formatTime(it.observedAt)})" } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
            notes.forEach { Text("! $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row {
                TextButton(onClick = { vm.openComparison(c) }) { Text("현재 값으로 다시 비교") }
                TextButton(onClick = { vm.deleteComparison(c.id) }) { Text("기록 삭제") }
            }
        }
    }
}
