package com.miu.fold.deals.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miu.fold.deals.comparison.CompareTable
import com.miu.fold.deals.comparison.ComparisonCalculator
import com.miu.fold.deals.listings.DatePrecision
import com.miu.fold.deals.listings.ListingDraft
import com.miu.fold.deals.listings.SpecNormalizer
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.listings.UserRating
import com.miu.fold.deals.observations.PriceKind
import com.miu.fold.deals.observations.PriceObservation
import com.miu.fold.deals.observations.ShippingState

/** 가격 확인 후 이 기간이 지나면 최신성 경고를 표시한다(초기 설계값). */
private const val STALE_DAYS = 7L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(vm: AppViewModel, ui: UiState, id: String, showBack: Boolean) {
    val item = ui.find(id)
    if (item == null) {
        Box(Modifier.fillMaxSize().padding(Space.xl)) {
            Column {
                Text("매물을 찾을 수 없어요.")
                TextButton(onClick = { vm.navigate(Route.Board) }) { Text("보드로") }
            }
        }
        return
    }
    val l = item.listing
    val latest = item.latestPosted
    val context = LocalContext.current
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = { vm.navigate(Route.Board) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(l.displayName, Modifier.weight(1f).padding(start = Space.s), style = MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.l).padding(bottom = Space.xxl),
            ) {
                if (l.deletedAt != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                            Text("삭제된 매물입니다 (${formatTime(l.deletedAt)}).", Modifier.weight(1f))
                            TextButton(onClick = { vm.restore(l.id) }) { Text("복원") }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Button(onClick = { vm.startEdit(l.id) }) { Text("수정") }
                    OutlinedButton(onClick = { showAdd = true }) { Text("새 가격 기록") }
                    OutlinedButton(onClick = { vm.toggleCompare(l.id) }) {
                        Text(if (l.id in ui.compareIds) "✓ 비교에서 빼기" else "비교에 추가")
                    }
                    val url = l.originalUrl
                    if (url != null) OutlinedButton(onClick = {
                        // http/https 외의 스킴은 실행하지 않는다(저장 시에도 검증).
                        if (ListingDraft.isWebUrl(url)) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                            } catch (_: ActivityNotFoundException) {
                                // 열 수 있는 앱이 없으면 아무 것도 실행하지 않는다.
                            }
                        }
                    }) { Text("원본 열기") }
                    if (l.deletedAt == null) TextButton(onClick = { confirmDelete = true }) { Text("삭제") }
                }

                SectionTitle("가격")
                if (latest == null) {
                    Text("게시 가격 기록이 없어요. '새 가격 기록'으로 추가하세요.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    FieldRow("판매가", CompareTable.priceText(latest) ?: latest.rawText?.let { "원문: $it (해석 불가)" })
                    FieldRow("배송비", CompareTable.shippingText(latest))
                    FieldRow("총비용", latest.totalCostKrw?.let(ComparisonCalculator::formatKrw)
                        ?: latest.totalCostIssue?.let { "미확인 — $it" })
                    FieldRow("가격 확인일", formatTime(latest.observedAt))
                    val ageDays = (System.currentTimeMillis() - latest.observedAt) / 86_400_000L
                    if (ageDays >= STALE_DAYS) {
                        Text("! 가격을 확인한 지 ${ageDays}일 지났어요. 현재 가격은 다를 수 있습니다.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("직접 기록한 가격 기준 · 자동 갱신 없음", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                SectionTitle("상태 설정")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    TradeStatus.entries.forEach { s ->
                        FilterChip(selected = l.tradeStatus == s, onClick = { vm.setTradeStatus(l.id, s) },
                            label = { Text((if (l.tradeStatus == s) "✓ " else "") + s.label) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    UserRating.entries.forEach { r ->
                        FilterChip(selected = l.userRating == r, onClick = { vm.setRating(l.id, r) },
                            label = { Text((if (l.userRating == r) "✓ " else "") + r.label) })
                    }
                }

                SectionTitle("제품·사양")
                FieldRow("분류", l.category.label)
                FieldRow("제조사", l.brand)
                FieldRow("모델 원문", l.modelRaw)
                FieldRow("비교 키", l.modelCanonical)
                FieldRow("세부 변형", l.variant)
                FieldRow("RAM", l.ramGb?.let { ram ->
                    val modules = if (l.ramModuleCount != null) " (${l.ramModuleGb}GB×${l.ramModuleCount})" else ""
                    "${ram}GB$modules · 원문 ${l.ramRaw}"
                })
                FieldRow("저장공간", l.storageGb?.let { "${SpecNormalizer.formatStorage(it)} · 원문 ${l.storageRaw}" })
                FieldRow("색상", l.color)

                SectionTitle("상태 (판매자 설명 기준, 직접 확인 전)")
                FieldRow("내부 화면", l.innerScreen)
                FieldRow("외부 화면", l.outerScreen)
                FieldRow("힌지", l.hinge)
                FieldRow("수리 이력", l.repairHistory)
                FieldRow("배터리", l.battery)
                FieldRow("기타", l.conditionNote)

                SectionTitle("거래 정보")
                FieldRow("플랫폼", l.platform)
                FieldRow("원본 링크", l.originalUrl)
                FieldRow("판매글 제목", l.sellerTitle)
                FieldRow("거래 지역", l.region)
                FieldRow("등록일", when (l.publishedPrecision) {
                    DatePrecision.UNKNOWN -> l.publishedRaw?.let { "원문 '$it' (날짜 미확인)" }
                    else -> l.publishedValue
                })
                FieldRow("앱에 저장", formatTime(l.savedAt))
                FieldRow("입력 출처", l.specSource.label)

                SectionTitle("가격 이력 (${item.observations.size}건)")
                if (item.observations.size == 1) Text("기록이 1개뿐이라 추세를 판단할 수 없어요.", style = MaterialTheme.typography.bodySmall)
                item.observations.sortedByDescending { it.observedAt }.forEach { o -> ObservationRow(o, isCurrent = o.id == latest?.id) }

                if (!l.description.isNullOrBlank() || !l.memo.isNullOrBlank()) {
                    SectionTitle("메모")
                    FieldRow("판매글 설명", l.description)
                    FieldRow("내 메모", l.memo)
                }
            }
        }
    }

    if (showAdd) AddObservationDialog(
        initialShipping = latest?.shippingState ?: ShippingState.UNKNOWN,
        onDismiss = { showAdd = false },
        onSave = { price, kind, ship, shipAmt, note ->
            vm.addObservation(l.id, price, kind, ship, shipAmt, note)
            showAdd = false
        },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("매물을 삭제할까요?") },
        text = { Text("보드와 현재 비교에서 빠집니다. 가격 이력과 이전 비교 기록은 남으며 상세 화면에서 복원할 수 있어요.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(l.id) }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
    )
}

@Composable
private fun ObservationRow(o: PriceObservation, isCurrent: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Text(
            "${formatTime(o.observedAt)} · ${o.priceKind.label}" + if (isCurrent) " · 현재 기준" else "",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            listOfNotNull(
                CompareTable.priceText(o) ?: "가격 미확인",
                CompareTable.shippingText(o) ?: "배송비 미확인",
                o.rawText?.let { "원문 '$it'" },
                o.sourceKind.label,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        o.note?.let { Text("메모: $it", style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(top = Space.xs))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddObservationDialog(
    initialShipping: ShippingState,
    onDismiss: () -> Unit,
    onSave: (String, PriceKind, ShippingState, String, String) -> Unit,
) {
    var price by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(PriceKind.POSTED) }
    var ship by rememberSaveable { mutableStateOf(initialShipping) }
    var shipAmt by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 가격 기록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("이전 기록은 그대로 두고 지금 시각의 관측을 추가합니다.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PriceKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text((if (kind == k) "✓ " else "") + k.label) })
                    }
                }
                OutlinedTextField(price, { price = it }, label = { Text("가격 원문 (예: 65만)") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    ShippingState.entries.forEach { s ->
                        FilterChip(selected = ship == s, onClick = { ship = s }, label = { Text((if (ship == s) "✓ " else "") + s.label) })
                    }
                }
                if (ship == ShippingState.SEPARATE) {
                    OutlinedTextField(shipAmt, { shipAmt = it }, label = { Text("배송비 금액") }, singleLine = true)
                }
                OutlinedTextField(note, { note = it }, label = { Text("메모 (선택)") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(price, kind, ship, shipAmt, note) }) { Text("기록") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
