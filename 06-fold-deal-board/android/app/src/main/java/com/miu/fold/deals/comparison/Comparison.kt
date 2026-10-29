package com.miu.fold.deals.comparison

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 저장한 비교 세션. 사용한 가격 관측 ID를 고정해 두므로 원본 가격이 바뀌어도 당시 비교가 유지된다.
 * listingIds, observationIds는 같은 순서의 줄바꿈 구분 문자열이다. 관측이 없던 칸은 빈 문자열이다.
 */
@Entity(tableName = "comparisons")
data class Comparison(
    @PrimaryKey val id: String,
    val title: String,
    val listingIds: String,
    val observationIds: String,
    val budgetKrw: Long?,
    val notes: String?,
    val createdAt: Long,
) {
    val listingIdList: List<String> get() = listingIds.split('\n')
    val observationIdList: List<String?> get() = observationIds.split('\n').map { it.ifEmpty { null } }
}
