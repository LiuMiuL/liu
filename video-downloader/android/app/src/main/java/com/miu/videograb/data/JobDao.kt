package com.miu.videograb.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadJob>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): DownloadJob?

    @Query("SELECT * FROM jobs WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): DownloadJob?

    @Query("SELECT COUNT(*) FROM jobs WHERE status = 'QUEUED'")
    suspend fun queuedCount(): Int

    @Query("SELECT * FROM jobs WHERE urlKey = :urlKey AND status IN ('QUEUED','RUNNING','SAVING') LIMIT 1")
    suspend fun activeByUrlKey(urlKey: String): DownloadJob?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(job: DownloadJob)

    @Query("UPDATE jobs SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: JobStatus)

    /** 상태가 기대값일 때만 바꾼다. 취소와 진행 갱신이 겹쳐도 취소가 덮어써지지 않게 한다. */
    @Query("UPDATE jobs SET status = :to WHERE id = :id AND status = :from")
    suspend fun transition(id: String, from: JobStatus, to: JobStatus): Int

    @Query("UPDATE jobs SET status = 'RUNNING', startedAt = :at, progress = NULL, etaSeconds = NULL, errorMessage = NULL, errorDetail = NULL WHERE id = :id AND status = 'QUEUED'")
    suspend fun markStarted(id: String, at: Long): Int

    @Query("UPDATE jobs SET progress = :progress, etaSeconds = :eta WHERE id = :id AND status = 'RUNNING'")
    suspend fun updateProgress(id: String, progress: Float?, eta: Long?)

    @Query("UPDATE jobs SET title = :title WHERE id = :id AND title IS NULL")
    suspend fun setTitleIfMissing(id: String, title: String)

    @Query("UPDATE jobs SET status = 'DONE', title = :title, outputUris = :uris, outputCount = :count, totalBytes = :bytes, progress = 100, etaSeconds = NULL, finishedAt = :at WHERE id = :id AND status = 'SAVING'")
    suspend fun markDone(id: String, title: String?, uris: String, count: Int, bytes: Long, at: Long): Int

    @Query("UPDATE jobs SET status = 'FAILED', errorMessage = :message, errorDetail = :detail, suggestEngineUpdate = :suggestUpdate, etaSeconds = NULL, finishedAt = :at WHERE id = :id AND status IN ('RUNNING','SAVING')")
    suspend fun markFailed(id: String, message: String, detail: String?, suggestUpdate: Boolean, at: Long): Int

    @Query("UPDATE jobs SET status = 'CANCELLED', etaSeconds = NULL, finishedAt = :at WHERE id = :id AND status IN ('QUEUED','RUNNING','SAVING')")
    suspend fun cancel(id: String, at: Long): Int

    @Query("UPDATE jobs SET status = 'QUEUED', progress = NULL, etaSeconds = NULL, errorMessage = NULL, errorDetail = NULL, suggestEngineUpdate = 0, autoRetried = 0, finishedAt = NULL, createdAt = :at WHERE id = :id AND status IN ('FAILED','CANCELLED')")
    suspend fun requeue(id: String, at: Long): Int

    /** 엔진을 업데이트한 뒤 자동으로 한 번 다시 시도한다. */
    @Query("UPDATE jobs SET status = 'QUEUED', autoRetried = 1, progress = NULL, etaSeconds = NULL WHERE id = :id AND status = 'RUNNING' AND autoRetried = 0")
    suspend fun requeueAfterEngineUpdate(id: String): Int

    /** 앱이 강제 종료되어 '다운로드 중'으로 남은 작업을 대기열로 되돌린다. */
    @Query("UPDATE jobs SET status = 'QUEUED', etaSeconds = NULL, restarts = restarts + 1 WHERE status IN ('RUNNING','SAVING')")
    suspend fun requeueInterrupted(): Int

    @Query("DELETE FROM jobs WHERE id = :id AND status NOT IN ('RUNNING','SAVING')")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM jobs WHERE status IN ('DONE','CANCELLED')")
    suspend fun clearFinished(): Int

    @Transaction
    suspend fun insertIfNotActive(job: DownloadJob): Boolean {
        if (activeByUrlKey(job.urlKey) != null) return false
        insert(job)
        return true
    }
}
