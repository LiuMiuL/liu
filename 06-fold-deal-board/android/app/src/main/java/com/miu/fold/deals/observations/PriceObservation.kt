package com.miu.fold.deals.observations

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.miu.fold.deals.listings.Listing
import com.miu.fold.deals.listings.SourceKind

/** 가격 값의 상태. 미확인·협의·0원(KNOWN, 0)을 서로 다른 상태로 다룬다. */
enum class PriceState(val label: String) { KNOWN("확인됨"), UNKNOWN("가격 미확인"), NEGOTIABLE("가격 협의") }

/** 가격 표현의 단위. NONE이 아니면 단순 판매가로 집계하지 않는다. */
enum class PriceQualifier(val label: String) { NONE(""), BUNDLE("일괄"), PER_UNIT("개당"), DEPOSIT("보증금") }

/** 가격 종류. 게시 가격·협상 제안·확인한 거래가를 섞지 않는다. */
enum class PriceKind(val label: String) { POSTED("게시 가격"), NEGOTIATION("협상 제안"), CONFIRMED_TRADE("확인한 거래가") }

/** 배송비 상태. UNKNOWN을 무료로 바꾸지 않는다. */
enum class ShippingState(val label: String) {
    UNKNOWN("배송비 미확인"), INCLUDED("배송비 포함"), SEPARATE("배송비 별도"), DIRECT_DEAL("직거래(배송 없음)")
}

/** 가격 관측 1건. 추가만 하며 이전 값을 덮어쓰지 않는다. */
@Entity(
    tableName = "price_observations",
    foreignKeys = [ForeignKey(entity = Listing::class, parentColumns = ["id"], childColumns = ["listingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listingId")],
)
data class PriceObservation(
    @PrimaryKey val id: String,
    val listingId: String,
    val priceState: PriceState,
    /** 원화 정수. priceState가 KNOWN일 때만 값이 있다. */
    val amountKrw: Long?,
    val qualifier: PriceQualifier = PriceQualifier.NONE,
    val rawText: String?,
    val shippingState: ShippingState = ShippingState.UNKNOWN,
    /** SEPARATE일 때 알려진 배송비. 모르면 null. */
    val shippingKrw: Long? = null,
    val priceKind: PriceKind = PriceKind.POSTED,
    val currency: String = "KRW",
    /** 관측(확인) 시각 UTC epoch ms. */
    val observedAt: Long,
    val sourceKind: SourceKind = SourceKind.MANUAL,
    val note: String? = null,
) {
    /**
     * 총비용(판매가+배송비). 계산할 수 없으면 null이며 이유는 [totalCostIssue]에 있다.
     */
    val totalCostKrw: Long?
        get() = if (totalCostIssue == null) amountKrw!! + (if (shippingState == ShippingState.SEPARATE) shippingKrw!! else 0L) else null

    val totalCostIssue: String?
        get() = when {
            currency != "KRW" -> "통화가 원화가 아님"
            priceState == PriceState.UNKNOWN -> "가격 미확인"
            priceState == PriceState.NEGOTIABLE -> "가격 협의"
            amountKrw == null -> "가격 미확인"
            qualifier != PriceQualifier.NONE -> "${qualifier.label} 가격(단순 판매가 아님)"
            shippingState == ShippingState.UNKNOWN -> "배송비 미확인"
            shippingState == ShippingState.SEPARATE && shippingKrw == null -> "별도 배송비 금액 미확인"
            else -> null
        }
}
