package com.miu.videograb.data

import com.miu.videograb.engine.QualityPreset
import com.miu.videograb.engine.UrlExtractor
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** 작업 추가 결과. */
data class EnqueueResult(val added: List<DownloadJob>, val duplicates: List<String>, val invalidText: Boolean) {
    val addedCount: Int get() = added.size
}

class JobRepository(private val dao: JobDao, private val clock: () -> Long = System::currentTimeMillis) {
    val jobs: Flow<List<DownloadJob>> = dao.observeAll()

    fun now(): Long = clock()

    /** 텍스트 안의 링크를 모두 대기열에 넣는다. 이미 대기·진행 중인 같은 링크는 건너뛴다. */
    suspend fun enqueueFromText(text: String?, preset: QualityPreset): EnqueueResult {
        val urls = UrlExtractor.extract(text)
        if (urls.isEmpty()) return EnqueueResult(emptyList(), emptyList(), invalidText = true)
        val added = mutableListOf<DownloadJob>()
        val dup = mutableListOf<String>()
        urls.forEachIndexed { i, url ->
            // 같은 시각이면 대기 순서가 섞이지 않도록 1ms씩 차이를 둔다.
            val job = DownloadJob(
                id = UUID.randomUUID().toString(), url = url, urlKey = UrlExtractor.normalize(url),
                preset = preset.name, status = JobStatus.QUEUED, createdAt = clock() + i,
            )
            if (dao.insertIfNotActive(job)) added += job else dup += url
        }
        return EnqueueResult(added, dup, invalidText = false)
    }

    suspend fun get(id: String) = dao.get(id)
    suspend fun nextQueued() = dao.nextQueued()
    suspend fun queuedCount() = dao.queuedCount()
    suspend fun markStarted(id: String) = dao.markStarted(id, clock()) == 1
    suspend fun updateProgress(id: String, progress: Float?, eta: Long?) = dao.updateProgress(id, progress, eta)
    suspend fun setTitleIfMissing(id: String, title: String) = dao.setTitleIfMissing(id, title)
    suspend fun markSaving(id: String) = dao.transition(id, JobStatus.RUNNING, JobStatus.SAVING) == 1
    suspend fun markDone(id: String, title: String?, uris: List<String>, bytes: Long) =
        dao.markDone(id, title, uris.joinToString("\n"), uris.size, bytes, clock()) == 1
    suspend fun markFailed(id: String, message: String, detail: String?, suggestUpdate: Boolean) =
        dao.markFailed(id, message, detail, suggestUpdate, clock()) == 1
    suspend fun cancel(id: String) = dao.cancel(id, clock()) == 1
    suspend fun retry(id: String) = dao.requeue(id, clock()) == 1
    suspend fun requeueInterrupted() = dao.requeueInterrupted()
    suspend fun requeueAfterEngineUpdate(id: String) = dao.requeueAfterEngineUpdate(id) == 1
    suspend fun delete(id: String) = dao.delete(id) == 1
    suspend fun clearFinished() = dao.clearFinished()
}
