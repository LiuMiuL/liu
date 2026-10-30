package com.miu.lastcontact.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY name")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person")
    suspend fun all(): List<PersonEntity>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun get(id: String): PersonEntity?

    @Query("SELECT * FROM person WHERE id = :id")
    fun observe(id: String): Flow<PersonEntity?>

    @Upsert
    suspend fun upsert(p: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<PersonEntity>)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun delete(id: String)
}

/** 사람별 가장 최근 날짜의 기록(같은 날 여러 경로면 여러 행). */
data class LatestRow(val personId: String, val day: String, val atMillis: Long?, val channel: String)

/** 사람·경로별 마지막 날짜 */
data class ChannelLatest(val channel: String, val day: String)

@Dao
interface EventDao {
    @Query(
        """SELECT e.personId AS personId, e.day AS day, e.atMillis AS atMillis, e.channel AS channel
           FROM contact_event e JOIN (SELECT personId, MAX(day) AS d FROM contact_event GROUP BY personId) m
           ON e.personId = m.personId AND e.day = m.d""",
    )
    fun observeLatest(): Flow<List<LatestRow>>

    @Query("SELECT channel, MAX(day) AS day FROM contact_event WHERE personId = :personId GROUP BY channel")
    fun observeChannelLatest(personId: String): Flow<List<ChannelLatest>>

    @Query("SELECT * FROM contact_event WHERE personId = :personId ORDER BY day DESC, atMillis DESC LIMIT :limit")
    fun observeForPerson(personId: String, limit: Int = 300): Flow<List<ContactEventEntity>>

    @Query("SELECT COUNT(*) FROM contact_event WHERE personId = :personId")
    fun observeCount(personId: String): Flow<Int>

    @Query("SELECT * FROM contact_event WHERE dedupeKey = :key")
    suspend fun byKey(key: String): ContactEventEntity?

    @Query("SELECT * FROM contact_event")
    suspend fun all(): List<ContactEventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: ContactEventEntity)

    @Upsert
    suspend fun upsert(e: ContactEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<ContactEventEntity>)

    @Query("DELETE FROM contact_event WHERE id = :id")
    suspend fun delete(id: String)
}

/** 미확인 이름별 요약 */
data class UnmatchedSummary(val nameKey: String, val displayName: String, val days: Int, val lastAt: Long, val roomName: String?)

@Dao
interface UnmatchedDao {
    @Query(
        """SELECT nameKey, MAX(displayName) AS displayName, COUNT(*) AS days, MAX(lastAt) AS lastAt, MAX(roomName) AS roomName
           FROM unmatched_sighting GROUP BY nameKey ORDER BY lastAt DESC""",
    )
    fun observeSummary(): Flow<List<UnmatchedSummary>>

    @Query("SELECT * FROM unmatched_sighting WHERE nameKey = :key AND day = :day AND channel = :channel")
    suspend fun get(key: String, day: String, channel: String): UnmatchedSightingEntity?

    @Query("SELECT * FROM unmatched_sighting WHERE nameKey = :key")
    suspend fun forName(key: String): List<UnmatchedSightingEntity>

    @Query("SELECT * FROM unmatched_sighting")
    suspend fun all(): List<UnmatchedSightingEntity>

    @Upsert
    suspend fun upsert(s: UnmatchedSightingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<UnmatchedSightingEntity>)

    @Query("DELETE FROM unmatched_sighting WHERE nameKey = :key")
    suspend fun deleteName(key: String)

    @Query("SELECT * FROM ignored_name")
    fun observeIgnored(): Flow<List<IgnoredNameEntity>>

    @Query("SELECT * FROM ignored_name")
    suspend fun ignored(): List<IgnoredNameEntity>

    @Query("SELECT COUNT(*) FROM ignored_name WHERE nameKey = :key")
    suspend fun isIgnored(key: String): Int

    @Upsert
    suspend fun ignore(n: IgnoredNameEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoredIgnore(items: List<IgnoredNameEntity>)

    @Query("DELETE FROM ignored_name WHERE nameKey = :key")
    suspend fun unignore(key: String)
}
