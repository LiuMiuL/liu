package com.miu.fold.deals.data

import com.miu.fold.deals.comparison.Comparison
import com.miu.fold.deals.comparison.ListingWithObservations
import com.miu.fold.deals.listings.Listing
import com.miu.fold.deals.observations.PriceObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/** 매물·관측·비교 세션 저장소. 로컬 DB만 사용한다. */
class ListingRepository(private val dao: DealDao, private val clock: () -> Long = System::currentTimeMillis) {

    /** 삭제된 매물까지 포함한 전체. 비교 기록 표시에 필요하다. */
    val allItems: Flow<List<ListingWithObservations>> =
        combine(dao.observeAllListings(), dao.observeAllObservations()) { listings, observations ->
            val byListing = observations.groupBy { it.listingId }
            listings.map { ListingWithObservations(it, byListing[it.id].orEmpty()) }
        }

    val comparisons: Flow<List<Comparison>> = dao.observeComparisons()

    fun now(): Long = clock()

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun get(id: String): Listing? = dao.getListing(id)

    suspend fun observations(id: String): List<PriceObservation> = dao.observationsFor(id)

    suspend fun create(listing: Listing, initial: PriceObservation?) = dao.insertListingWithObservation(listing, initial)

    suspend fun update(listing: Listing, newObservation: PriceObservation?) =
        dao.updateListingWithObservation(listing.copy(updatedAt = clock()), newObservation)

    suspend fun addObservation(observation: PriceObservation) = dao.insertObservation(observation)

    suspend fun softDelete(id: String) = dao.softDelete(id, clock())

    suspend fun restore(id: String) = dao.restore(id, clock())

    suspend fun saveComparison(comparison: Comparison) = dao.insertComparison(comparison)

    suspend fun deleteComparison(id: String) = dao.deleteComparison(id)
}
