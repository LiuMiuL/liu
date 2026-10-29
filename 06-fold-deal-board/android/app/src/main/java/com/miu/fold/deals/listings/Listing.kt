package com.miu.fold.deals.listings

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 제품 분류. */
enum class Category(val label: String) {
    PHONE("휴대폰"), PC_PART("PC 부품"), LAPTOP("노트북"), TABLET("태블릿"), OTHER("기타")
}

/** 거래 상태. UNVERIFIABLE은 "확인 불가"이며 판매 중으로 취급하지 않는다. */
enum class TradeStatus(val label: String) {
    ON_SALE("판매 중"), RESERVED("예약"), SOLD("판매 완료"), UNVERIFIABLE("확인 불가")
}

/** 사용자 평가. */
enum class UserRating(val label: String) {
    NONE("평가 없음"), INTERESTED("관심"), ON_HOLD("보류"), EXCLUDED("제외"), PURCHASED("구매 완료")
}

/** 값의 출처. 이번 단계에서 실제로 만드는 출처는 MANUAL, FROM_DESCRIPTION 뿐이다. */
enum class SourceKind(val label: String) {
    MANUAL("직접 입력"), FROM_DESCRIPTION("설명에서 가져옴"), ALLOWED_INTEGRATION("허용된 연동"), AI_SUGGESTION("AI 제안")
}

/** 날짜 정밀도. 날짜만 확인된 정보에 임의 시각을 붙이지 않는다. */
enum class DatePrecision { UNKNOWN, YEAR_MONTH, DAY }

/**
 * 저장한 매물 1개.
 * 모든 nullable 필드에서 null은 "미확인"이다. 0이나 정상 상태로 바꾸지 않는다.
 * 가격은 이 엔터티에 두지 않고 [com.miu.fold.deals.observations.PriceObservation]에 누적한다.
 */
@Entity(tableName = "listings")
data class Listing(
    @PrimaryKey val id: String,
    val platform: String? = null,
    val originalUrl: String? = null,
    val platformListingId: String? = null,
    val sellerTitle: String? = null,
    val category: Category = Category.PHONE,
    val brand: String? = null,
    /** 판매자 원문 모델명. */
    val modelRaw: String? = null,
    /** 정확 비교용 정규화 모델 키. 사용자가 수정할 수 있다. */
    val modelCanonical: String? = null,
    val variant: String? = null,
    val ramRaw: String? = null,
    /** 총 RAM(GB). */
    val ramGb: Int? = null,
    val ramModuleGb: Int? = null,
    val ramModuleCount: Int? = null,
    val storageRaw: String? = null,
    val storageGb: Int? = null,
    val color: String? = null,
    val region: String? = null,
    val publishedRaw: String? = null,
    /** ISO 형식 yyyy-MM-dd 또는 yyyy-MM. 정밀도는 [publishedPrecision]. */
    val publishedValue: String? = null,
    val publishedPrecision: DatePrecision = DatePrecision.UNKNOWN,
    val tradeStatus: TradeStatus = TradeStatus.ON_SALE,
    val userRating: UserRating = UserRating.NONE,
    val innerScreen: String? = null,
    val outerScreen: String? = null,
    val hinge: String? = null,
    val repairHistory: String? = null,
    val battery: String? = null,
    val conditionNote: String? = null,
    val description: String? = null,
    val memo: String? = null,
    val specSource: SourceKind = SourceKind.MANUAL,
    /** 앱에 저장한 시각(UTC epoch ms). 등록일과 다르다. */
    val savedAt: Long,
    val updatedAt: Long,
    /** 삭제 시각. 비교 기록이 참조할 수 있도록 실제 행은 남긴다. */
    val deletedAt: Long? = null,
) {
    val displayName: String
        get() = listOfNotNull(
            modelRaw?.takeIf { it.isNotBlank() } ?: sellerTitle?.takeIf { it.isNotBlank() },
            ramGb?.let { "${it}GB" },
            storageGb?.let { SpecNormalizer.formatStorage(it) },
        ).joinToString(" · ").ifBlank { "이름 없는 매물" }
}
