package com.miu.fold.deals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.deals.listings.Category
import com.miu.fold.deals.listings.ListingDraft
import com.miu.fold.deals.listings.PublishedDateParser
import com.miu.fold.deals.listings.SpecNormalizer
import com.miu.fold.deals.listings.TradeStatus
import com.miu.fold.deals.listings.UserRating
import com.miu.fold.deals.observations.PriceParser
import com.miu.fold.deals.observations.PriceQualifier
import com.miu.fold.deals.observations.PriceState
import com.miu.fold.deals.observations.ShippingState

@Composable
fun EditorScreen(vm: AppViewModel, isNew: Boolean) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val errors by vm.draftErrors.collectAsStateWithLifecycle()
    val d = draft
    if (d == null) {
        // 초안이 없으면(예: 저장 직후) 보드로 돌아간다.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { vm.navigate(Route.Board) }) { Text("보드로 돌아가기") }
        }
        return
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeEditor) { Text("취소") }
            Text(if (isNew) "매물 추가" else "매물 수정", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Button(onClick = vm::saveDraft) { Text("저장") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.l).padding(bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (errors.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(Space.m)) {
                            Text("저장할 수 없어요", fontWeight = FontWeight.Bold)
                            errors.forEach { Text("! $it") }
                        }
                    }
                }
                EditorFields(d) { t -> vm.updateDraft(t) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorFields(d: ListingDraft, update: ((ListingDraft) -> ListingDraft) -> Unit) {
    SectionTitle("원본")
    Field("원본 링크", d.url, keyboard = KeyboardType.Uri) { v ->
        update { it.copy(url = v, platform = it.platform.ifEmpty { ListingDraft.guessPlatform(v).orEmpty() }) }
    }
    Field("플랫폼 (번개장터·중고나라·당근 등)", d.platform) { v -> update { it.copy(platform = v) } }
    Field("판매글 제목", d.sellerTitle) { v -> update { it.copy(sellerTitle = v) } }

    SectionTitle("가격 (게시 가격)")
    Field("판매가 원문 (예: 70만, 700,000원, 가격 협의)", d.price) { v -> update { it.copy(price = v) } }
    Hint(priceHint(d.price))
    ChipGroup("배송비", ShippingState.entries, d.shippingState, { it.label }) { v -> update { it.copy(shippingState = v) } }
    if (d.shippingState == ShippingState.SEPARATE) {
        Field("배송비 금액 (모르면 비워두기)", d.shipping, keyboard = KeyboardType.Number) { v -> update { it.copy(shipping = v) } }
    }
    Hint("가격·배송을 바꿔 저장하면 이전 값은 덮어쓰지 않고 새 관측으로 기록됩니다.")

    SectionTitle("제품")
    ChipGroup("분류", Category.entries, d.category, { it.label }) { v -> update { it.copy(category = v) } }
    Field("제조사", d.brand) { v -> update { it.copy(brand = v) } }
    Field("모델 원문 (예: 갤럭시 Z 폴드6)", d.modelRaw) { v -> update { it.copy(modelRaw = v) } }
    Field("정규화 모델 (비우면 자동)", d.modelCanonical) { v -> update { it.copy(modelCanonical = v) } }
    Hint("비교 키: " + (d.effectiveCanonical ?: "미확인") + " · 비슷한 이름(Fold SE / Fold6)은 자동으로 합치지 않습니다.")
    Field("세부 변형 (내수/해외, 통신사 등)", d.variant) { v -> update { it.copy(variant = v) } }
    Field("RAM (예: 12GB, 16GB×2)", d.ram) { v -> update { it.copy(ram = v) } }
    Hint(ramHint(d.ram))
    Field("저장공간 (예: 512GB, 1TB)", d.storage) { v -> update { it.copy(storage = v) } }
    Hint(storageHint(d.storage))
    Field("색상", d.color) { v -> update { it.copy(color = v) } }

    SectionTitle("거래")
    ChipGroup("거래 상태", TradeStatus.entries, d.tradeStatus, { it.label }) { v -> update { it.copy(tradeStatus = v) } }
    ChipGroup("내 평가", UserRating.entries, d.userRating, { it.label }) { v -> update { it.copy(userRating = v) } }
    Field("거래 지역", d.region) { v -> update { it.copy(region = v) } }
    Field("등록일 (예: 2026-10-28, 모르면 비우기)", d.published) { v -> update { it.copy(published = v) } }
    Hint(PublishedDateParser.parse(d.published).let { p ->
        if (d.published.isBlank()) "등록일 미확인으로 저장" else p.value?.let { "해석: $it" } ?: "해석 불가 → 원문만 보존, 등록일 미확인"
    })

    SectionTitle("상태 (판매자 설명 기준)")
    Field("내부 화면", d.innerScreen) { v -> update { it.copy(innerScreen = v) } }
    Field("외부 화면", d.outerScreen) { v -> update { it.copy(outerScreen = v) } }
    Field("힌지", d.hinge) { v -> update { it.copy(hinge = v) } }
    Field("수리 이력", d.repairHistory) { v -> update { it.copy(repairHistory = v) } }
    Field("배터리", d.battery) { v -> update { it.copy(battery = v) } }
    Field("기타 상태 설명", d.conditionNote, singleLine = false) { v -> update { it.copy(conditionNote = v) } }

    SectionTitle("메모")
    Field("판매글 설명 붙여넣기", d.description, singleLine = false) { v -> update { it.copy(description = v) } }
    Field("내 메모 (질문·협상 내용)", d.memo, singleLine = false) { v -> update { it.copy(memo = v) } }
    Hint("판매자 연락처·계좌 같은 개인정보는 필요할 때만 직접 입력하세요.")
}

@Composable
private fun Field(
    label: String, value: String, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        minLines = if (singleLine) 1 else 3, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            options.forEach { o ->
                FilterChip(selected = o == selected, onClick = { onSelect(o) },
                    label = { Text((if (o == selected) "✓ " else "") + text(o)) })
            }
        }
    }
}

private fun priceHint(raw: String): String {
    if (raw.isBlank()) return "가격 미확인으로 저장 (0원과 다릅니다)"
    val p = PriceParser.parse(raw)
    return when (p.state) {
        PriceState.KNOWN -> "해석: ${PriceParser.format(p.amountKrw!!)}" +
            (if (p.qualifier != PriceQualifier.NONE) " · ${p.qualifier.label} 가격이라 총비용 집계에서 제외" else "")
        PriceState.NEGOTIABLE -> "해석: 가격 협의 (금액 없음)"
        PriceState.UNKNOWN -> "해석 불가 → 원문만 보존, 가격 미확인"
    }
}

private fun ramHint(raw: String): String {
    if (raw.isBlank()) return "RAM 미확인으로 저장"
    val r = SpecNormalizer.parseRam(raw) ?: return "해석 불가 — 예: 12GB, 16GB×2"
    return if (r.moduleCount != null) "해석: 총 ${r.totalGb}GB (모듈 ${r.moduleGb}GB × ${r.moduleCount}개)" else "해석: ${r.totalGb}GB"
}

private fun storageHint(raw: String): String {
    if (raw.isBlank()) return "저장공간 미확인으로 저장"
    return SpecNormalizer.parseStorageGb(raw)?.let { "해석: ${SpecNormalizer.formatStorage(it)}" } ?: "해석 불가 — 예: 512GB, 1TB"
}
