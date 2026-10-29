package com.miu.fold.dashboard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard_card")
    fun observeCards(): Flow<List<DashboardCardEntity>>

    @Query("SELECT * FROM dashboard_card")
    suspend fun cards(): List<DashboardCardEntity>

    @Query("SELECT * FROM layout_placement")
    fun observePlacements(): Flow<List<LayoutPlacementEntity>>

    @Query("SELECT * FROM layout_placement WHERE profile = :profile")
    suspend fun placements(profile: String): List<LayoutPlacementEntity>

    @Query("SELECT * FROM layout_placement")
    suspend fun allPlacements(): List<LayoutPlacementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCards(cards: List<DashboardCardEntity>)

    @Upsert
    suspend fun upsertCard(card: DashboardCardEntity)

    @Query("DELETE FROM dashboard_card WHERE id = :id")
    suspend fun deleteCard(id: String)

    @Query("DELETE FROM layout_placement WHERE profile = :profile")
    suspend fun clearProfile(profile: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlacements(items: List<LayoutPlacementEntity>)

    /** 한 프로필의 배치를 통째로 교체. 다른 프로필은 건드리지 않는다. */
    @Transaction
    suspend fun replaceProfile(profile: String, items: List<LayoutPlacementEntity>) {
        clearProfile(profile)
        insertPlacements(items.map { it.copy(profile = profile) })
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note WHERE deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM note WHERE id = :id")
    fun observe(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM note")
    suspend fun all(): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("UPDATE note SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: String, at: Long?)

    @Query("UPDATE note SET pinned = :pinned, updatedAt = :at WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, at: Long)

    @Query("DELETE FROM note WHERE id = :id AND deletedAt IS NOT NULL")
    suspend fun purge(id: String)

    @Query("DELETE FROM note WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTrash()

    @Query("SELECT * FROM note WHERE deletedAt IS NULL AND (title LIKE :q ESCAPE '\\' OR body LIKE :q ESCAPE '\\' OR tags LIKE :q ESCAPE '\\') ORDER BY updatedAt DESC LIMIT 50")
    suspend fun search(q: String): List<NoteEntity>
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo ORDER BY (doneAt IS NOT NULL), orderKey ASC, createdAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo WHERE id = :id")
    suspend fun get(id: String): TodoEntity?

    @Query("SELECT * FROM todo")
    suspend fun all(): List<TodoEntity>

    @Query("SELECT COALESCE(MAX(orderKey), 0) FROM todo")
    suspend fun maxOrder(): Long

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<TodoEntity>)

    @Query("UPDATE todo SET doneAt = :doneAt, updatedAt = :at WHERE id = :id")
    suspend fun setDone(id: String, doneAt: Long?, at: Long)

    @Query("UPDATE todo SET orderKey = :orderKey WHERE id = :id")
    suspend fun setOrder(id: String, orderKey: Long)

    @Query("DELETE FROM todo WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM todo WHERE title LIKE :q ESCAPE '\\' OR description LIKE :q ESCAPE '\\' ORDER BY updatedAt DESC LIMIT 50")
    suspend fun search(q: String): List<TodoEntity>
}

@Dao
interface EventDao {
    @Query("SELECT * FROM local_event ORDER BY localDateStart ASC, startInstant ASC")
    fun observeAll(): Flow<List<LocalEventEntity>>

    @Query("SELECT * FROM local_event WHERE id = :id")
    suspend fun get(id: String): LocalEventEntity?

    @Query("SELECT * FROM local_event")
    suspend fun all(): List<LocalEventEntity>

    @Upsert
    suspend fun upsert(e: LocalEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<LocalEventEntity>)

    @Query("DELETE FROM local_event WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM local_event WHERE title LIKE :q ESCAPE '\\' OR memo LIKE :q ESCAPE '\\' ORDER BY localDateStart DESC LIMIT 50")
    suspend fun search(q: String): List<LocalEventEntity>
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcut ORDER BY orderKey ASC, updatedAt ASC")
    fun observeAll(): Flow<List<ShortcutEntity>>

    @Query("SELECT * FROM shortcut WHERE id = :id")
    suspend fun get(id: String): ShortcutEntity?

    @Query("SELECT * FROM shortcut")
    suspend fun all(): List<ShortcutEntity>

    @Query("SELECT COALESCE(MAX(orderKey), 0) FROM shortcut")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(s: ShortcutEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ShortcutEntity>)

    @Query("DELETE FROM shortcut WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM shortcut WHERE label LIKE :q ESCAPE '\\' OR target LIKE :q ESCAPE '\\' LIMIT 50")
    suspend fun search(q: String): List<ShortcutEntity>
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timer_session WHERE state IN ('RUNNING','PAUSED','INTERRUPTED') ORDER BY updatedAt DESC LIMIT 1")
    fun observeActive(): Flow<TimerSessionEntity?>

    @Query("SELECT * FROM timer_session ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatest(): Flow<TimerSessionEntity?>

    @Query("SELECT * FROM timer_session WHERE state IN ('RUNNING','PAUSED','INTERRUPTED') ORDER BY updatedAt DESC")
    suspend fun active(): List<TimerSessionEntity>

    @Query("SELECT * FROM timer_session WHERE id = :id")
    suspend fun get(id: String): TimerSessionEntity?

    @Query("SELECT * FROM timer_session")
    suspend fun all(): List<TimerSessionEntity>

    @Upsert
    suspend fun upsert(s: TimerSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(r: FocusRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecords(items: List<FocusRecordEntity>)

    @Query("SELECT * FROM focus_record ORDER BY endedAt DESC LIMIT :limit")
    fun observeRecords(limit: Int = 50): Flow<List<FocusRecordEntity>>

    @Query("SELECT * FROM focus_record")
    suspend fun allRecords(): List<FocusRecordEntity>

    @Query("SELECT COUNT(*) FROM focus_record WHERE sessionId = :id")
    suspend fun recordCount(id: String): Int
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_item ORDER BY openedAt DESC LIMIT :limit")
    fun observe(limit: Int = 20): Flow<List<RecentItemEntity>>

    @Upsert
    suspend fun upsert(item: RecentItemEntity)

    @Query("DELETE FROM recent_item WHERE type = :type AND localId = :id")
    suspend fun delete(type: String, id: String)

    @Query("DELETE FROM recent_item")
    suspend fun clear()

    @Query("DELETE FROM recent_item WHERE openedAt < (SELECT MIN(openedAt) FROM (SELECT openedAt FROM recent_item ORDER BY openedAt DESC LIMIT 100))")
    suspend fun trim()
}
