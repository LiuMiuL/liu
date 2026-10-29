package com.miu.fold.deals.listings

import com.miu.fold.deals.observations.PriceKind
import com.miu.fold.deals.observations.PriceObservation
import com.miu.fold.deals.observations.PriceParser
import com.miu.fold.deals.observations.ShippingState

/**
 * 입력 화면의 편집 중 값. 모든 값은 원문 문자열이며 저장할 때 정규화한다.
 * 빈 문자열은 미확인으로 저장된다.
 */
data class ListingDraft(
    val platform: String = "",
    val url: String = "",
    val sellerTitle: String = "",
    val category: Category = Category.PHONE,
    val brand: String = "",
    val modelRaw: String = "",
    /** 사용자가 직접 고친 정규화 모델. 비어 있으면 원문에서 자동 생성한다. */
    val modelCanonical: String = "",
    val variant: String = "",
    val ram: String = "",
    val storage: String = "",
    val color: String = "",
    val region: String = "",
    val published: String = "",
    val tradeStatus: TradeStatus = TradeStatus.ON_SALE,
    val userRating: UserRating = UserRating.NONE,
    val innerScreen: String = "",
    val outerScreen: String = "",
    val hinge: String = "",
    val repairHistory: String = "",
    val battery: String = "",
    val conditionNote: String = "",
    val description: String = "",
    val memo: String = "",
    val price: String = "",
    val shippingState: ShippingState = ShippingState.UNKNOWN,
    val shipping: String = "",
) : java.io.Serializable {
    val effectiveCanonical: String? get() = modelCanonical.trim().ifEmpty { null }?.lowercase() ?: SpecNormalizer.canonicalModel(modelRaw)

    /** 저장 전 검증 오류. 없으면 빈 목록. */
    fun validate(): List<String> = buildList {
        val u = url.trim()
        if (u.isNotEmpty() && !isWebUrl(u)) add("원본 링크는 http:// 또는 https:// 주소만 저장할 수 있어요.")
        if (ram.isNotBlank() && SpecNormalizer.parseRam(ram) == null) add("RAM 값을 해석할 수 없어요. 예: 12GB, 16GB×2")
        if (storage.isNotBlank() && SpecNormalizer.parseStorageGb(storage) == null) add("저장공간 값을 해석할 수 없어요. 예: 512GB, 1TB")
        if (shippingState == ShippingState.SEPARATE && shipping.isNotBlank() && PriceParser.parse(shipping).amountKrw == null) {
            add("배송비 금액을 해석할 수 없어요.")
        }
        if (modelRaw.isBlank() && sellerTitle.isBlank() && u.isEmpty()) add("모델명, 판매글 제목, 링크 중 하나는 입력해 주세요.")
    }

    fun toListing(id: String, existing: Listing?, now: Long): Listing {
        val ramSpec = SpecNormalizer.parseRam(ram)
        val base = existing ?: Listing(id = id, savedAt = now, updatedAt = now)
        // 원문이 그대로면 처음 해석한 값을 유지한다. "3일 전"을 수정할 때마다 다시 계산하면 날짜가 밀린다.
        val date = if (existing != null && existing.publishedRaw == published.n()) {
            PublishedDateParser.Parsed(existing.publishedValue, existing.publishedPrecision)
        } else {
            PublishedDateParser.parse(published, nowMs = now)
        }
        return base.copy(
            platform = platform.n(),
            originalUrl = url.n(),
            sellerTitle = sellerTitle.n(),
            category = category,
            brand = brand.n(),
            modelRaw = modelRaw.n(),
            modelCanonical = effectiveCanonical,
            variant = variant.n(),
            ramRaw = ram.n(),
            ramGb = ramSpec?.totalGb,
            ramModuleGb = ramSpec?.moduleGb,
            ramModuleCount = ramSpec?.moduleCount,
            storageRaw = storage.n(),
            storageGb = SpecNormalizer.parseStorageGb(storage),
            color = color.n(),
            region = region.n(),
            publishedRaw = published.n(),
            publishedValue = date.value,
            publishedPrecision = date.precision,
            tradeStatus = tradeStatus,
            userRating = userRating,
            innerScreen = innerScreen.n(),
            outerScreen = outerScreen.n(),
            hinge = hinge.n(),
            repairHistory = repairHistory.n(),
            battery = battery.n(),
            conditionNote = conditionNote.n(),
            description = description.n(),
            memo = memo.n(),
            updatedAt = now,
        )
    }

    /**
     * 새 게시 가격 관측을 만든다. 기존 최신 관측과 가격·배송 원문이 같으면 null(새 기록 없음).
     * 새 매물에서 가격과 배송 정보가 모두 비어 있으면 관측을 만들지 않는다.
     */
    fun toObservationIfChanged(listingId: String, newId: String, latest: PriceObservation?, now: Long): PriceObservation? {
        val priceRaw = price.trim()
        val shipAmount = if (shippingState == ShippingState.SEPARATE) PriceParser.parse(shipping).amountKrw else null
        if (latest == null && priceRaw.isEmpty() && shippingState == ShippingState.UNKNOWN) return null
        if (latest != null && (latest.rawText ?: "") == priceRaw && latest.shippingState == shippingState && latest.shippingKrw == shipAmount) {
            return null
        }
        val parsed = PriceParser.parse(priceRaw)
        return PriceObservation(
            id = newId,
            listingId = listingId,
            priceState = parsed.state,
            amountKrw = parsed.amountKrw,
            qualifier = parsed.qualifier,
            rawText = priceRaw.ifEmpty { null },
            shippingState = shippingState,
            shippingKrw = shipAmount,
            priceKind = PriceKind.POSTED,
            observedAt = now,
        )
    }

    companion object {
        fun isWebUrl(u: String): Boolean = u.startsWith("https://", ignoreCase = true) || u.startsWith("http://", ignoreCase = true)

        fun fromListing(l: Listing, latest: PriceObservation?): ListingDraft = ListingDraft(
            platform = l.platform.orEmpty(), url = l.originalUrl.orEmpty(), sellerTitle = l.sellerTitle.orEmpty(),
            category = l.category, brand = l.brand.orEmpty(), modelRaw = l.modelRaw.orEmpty(),
            modelCanonical = if (l.modelCanonical != SpecNormalizer.canonicalModel(l.modelRaw)) l.modelCanonical.orEmpty() else "",
            variant = l.variant.orEmpty(), ram = l.ramRaw.orEmpty(), storage = l.storageRaw.orEmpty(), color = l.color.orEmpty(),
            region = l.region.orEmpty(), published = l.publishedRaw.orEmpty(), tradeStatus = l.tradeStatus, userRating = l.userRating,
            innerScreen = l.innerScreen.orEmpty(), outerScreen = l.outerScreen.orEmpty(), hinge = l.hinge.orEmpty(),
            repairHistory = l.repairHistory.orEmpty(), battery = l.battery.orEmpty(), conditionNote = l.conditionNote.orEmpty(),
            description = l.description.orEmpty(), memo = l.memo.orEmpty(),
            price = latest?.rawText.orEmpty(), shippingState = latest?.shippingState ?: ShippingState.UNKNOWN,
            shipping = latest?.shippingKrw?.toString().orEmpty(),
        )

        private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

        /** 다른 앱에서 공유한 텍스트로 초안을 만든다. 텍스트는 데이터로만 다룬다. */
        fun fromSharedText(text: String): ListingDraft {
            val url = urlRegex.find(text)?.value?.trimEnd('.', ',', ')')
            val rest = (if (url != null) text.replace(url, "") else text).trim()
            val firstLine = rest.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            return ListingDraft(
                platform = url?.let(::guessPlatform).orEmpty(),
                url = url.orEmpty(),
                sellerTitle = firstLine.take(200),
                description = rest,
            )
        }

        fun guessPlatform(url: String): String? {
            val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return null
            return when {
                host.endsWith("bunjang.co.kr") -> "번개장터"
                host.endsWith("joongna.com") -> "중고나라"
                host.endsWith("daangn.com") -> "당근"
                else -> null
            }
        }
    }
}

private fun String.n(): String? = trim().ifEmpty { null }
