package com.miu.fold.deals.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.miu.fold.deals.comparison.Comparison
import com.miu.fold.deals.listings.Listing
import com.miu.fold.deals.observations.PriceObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface DealDao {
    @Query("SELECT * FROM listings WHERE deletedAt IS NULL ORDER BY savedAt DESC")
    fun observeActiveListings(): Flow<List<Listing>>

    @Query("SELECT * FROM listings")
    fun observeAllListings(): Flow<List<Listing>>

    @Query("SELECT * FROM price_observations ORDER BY observedAt ASC")
    fun observeAllObservations(): Flow<List<PriceObservation>>

    @Query("SELECT * FROM listings WHERE id = :id")
    suspend fun getListing(id: String): Listing?

    @Query("SELECT * FROM price_observations WHERE listingId = :listingId ORDER BY observedAt ASC")
    suspend fun observationsFor(listingId: String): List<PriceObservation>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertListing(listing: Listing)

    @Update
    suspend fun updateListing(listing: Listing)

    /** 관측은 추가만 한다. 같은 ID 재삽입은 실패시켜 덮어쓰기를 막는다. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservation(observation: PriceObservation)

    @Transaction
    suspend fun insertListingWithObservation(listing: Listing, observation: PriceObservation?) {
        insertListing(listing)
        observation?.let { insertObservation(it) }
    }

    @Transaction
    suspend fun updateListingWithObservation(listing: Listing, observation: PriceObservation?) {
        updateListing(listing)
        observation?.let { insertObservation(it) }
    }

    @Query("UPDATE listings SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE listings SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: String, at: Long)

    @Query("SELECT * FROM comparisons ORDER BY createdAt DESC")
    fun observeComparisons(): Flow<List<Comparison>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComparison(comparison: Comparison)

    @Query("DELETE FROM comparisons WHERE id = :id")
    suspend fun deleteComparison(id: String)
}
