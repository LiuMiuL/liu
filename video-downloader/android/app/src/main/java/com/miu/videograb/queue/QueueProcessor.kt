package com.miu.videograb.queue

import com.miu.videograb.data.DownloadJob
import com.miu.videograb.data.JobRepository
import com.miu.videograb.data.JobStatus
import com.miu.videograb.engine.EngineResult
import com.miu.videograb.engine.ErrorClassifier
import com.miu.videograb.engine.OutputFiles
import com.miu.videograb.engine.QualityPreset
import com.miu.videograb.engine.VideoEngine
import com.miu.videograb.save.MediaSaver
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** 진행 상황 알림용. 작업자(DownloadWorker)가 알림으로 보여준다. */
interface QueueListener {
    fun onStarted(job: DownloadJob, remaining: Int) {}
    fun onProgress(job: DownloadJob, title: String?, progress: Float?, remaining: Int) {}
    fun onFinished(job: DownloadJob, status: JobStatus, title: String?, message: String?) {}
}

/**
 * 대기열을 하나씩 처리한다. 링크를 연달아 공유하면 순서대로 받는다.
 * 작업마다 [workRoot]/작업ID 폴더에 받고, 갤러리에 저장한 뒤 폴더를 지운다.
 */
class QueueProcessor(
    private val repo: JobRepository,
    private val engine: VideoEngine,
    private val saver: MediaSaver,
    private val workRoot: File,
    private val listener: QueueListener = object : QueueListener {},
    private val updater: EngineUpdater? = null,
    /** 진행률 DB 기록 간격(ms). 너무 자주 쓰지 않도록 한다. */
    private val progressIntervalMs: Long = 500,
) {
    data class Summary(val done: Int, val failed: Int, val cancelled: Int)

    suspend fun runUntilEmpty(): Summary {
        var done = 0; var failed = 0; var cancelled = 0
        while (true) {
            val job = repo.nextQueued() ?: break
            when (process(job)) {
                JobStatus.DONE -> done++
                JobStatus.FAILED -> failed++
                JobStatus.CANCELLED -> cancelled++
                else -> Unit // QUEUED: 엔진 업데이트 뒤 다시 대기열로 — 다음 반복에서 처리
            }
        }
        return Summary(done, failed, cancelled)
    }

    /** 작업 하나를 처리하고 최종 상태를 돌려준다. 코루틴이 취소되면(작업자 중지) 상태를 바꾸지 않고 그대로 던진다. */
    suspend fun process(job: DownloadJob): JobStatus {
        if (!repo.markStarted(job.id)) return repo.get(job.id)?.status ?: JobStatus.CANCELLED
        val remaining = repo.queuedCount()
        listener.onStarted(job, remaining)
        val dir = File(workRoot, job.id).apply { mkdirs() }
        val progress = MutableStateFlow<Pair<Float?, Long?>?>(null)
        val title = MutableStateFlow(job.title)

        val result = coroutineScope {
            val writer = launch {
                progress.filterNotNull().conflate().collect { (p, eta) ->
                    repo.updateProgress(job.id, p, eta)
                    listener.onProgress(job, title.value, p, remaining)
                    delay(progressIntervalMs)
                }
            }
            val titleWriter = launch {
                val t = title.filterNotNull().first()
                repo.setTitleIfMissing(job.id, t)
            }
            val r = engine.download(job.id, job.url, QualityPreset.fromName(job.preset), dir) { p, eta, line ->
                OutputFiles.titleFromProgressLine(line)?.let { if (title.value == null) title.value = it }
                if (p >= 0f) progress.value = p.coerceAtMost(100f) to eta.takeIf { it >= 0 }
            }
            writer.cancel(); titleWriter.cancel()
            r
        }

        return when (result) {
            is EngineResult.Cancelled -> finishCancelledOrInterrupted(job, dir)
            is EngineResult.Failure -> {
                val kind = ErrorClassifier.classify(result.errorText)
                // 사용자가 취소해 프로세스가 죽은 경우 오류로 바꾸지 않는다.
                if (repo.get(job.id)?.status == JobStatus.CANCELLED) return cleanup(dir, JobStatus.CANCELLED, job, title.value, null)
                // 사이트 변경·차단 계열이면 엔진을 업데이트해 보고, 새 버전을 받았으면 이 링크만 한 번 더 시도한다.
                if (kind.suggestEngineUpdate && !job.autoRetried && updater?.updateIfDue(afterFailure = true) == true &&
                    repo.requeueAfterEngineUpdate(job.id)
                ) {
                    dir.deleteRecursively()
                    return JobStatus.QUEUED
                }
                repo.markFailed(job.id, kind.message, ErrorClassifier.summarize(result.errorText), kind.suggestEngineUpdate)
                cleanup(dir, JobStatus.FAILED, job, title.value, kind.message)
            }
            is EngineResult.Success -> saveOutputs(job, dir, title.value)
        }
    }

    private suspend fun finishCancelledOrInterrupted(job: DownloadJob, dir: File): JobStatus {
        if (repo.get(job.id)?.status == JobStatus.CANCELLED) return cleanup(dir, JobStatus.CANCELLED, job, null, null)
        // 사용자가 취소하지 않았는데 엔진이 멈췄다면(시스템이 프로세스를 끝냄 등) 실패로 남겨 다시 시도할 수 있게 한다.
        val msg = "다운로드가 중간에 멈췄어요. 다시 시도해 주세요."
        repo.markFailed(job.id, msg, null, false)
        return cleanup(dir, JobStatus.FAILED, job, null, msg)
    }

    private suspend fun saveOutputs(job: DownloadJob, dir: File, progressTitle: String?): JobStatus {
        if (!repo.markSaving(job.id)) return cleanup(dir, repo.get(job.id)?.status ?: JobStatus.CANCELLED, job, progressTitle, null)
        val media = OutputFiles.mediaFiles(dir)
        if (media.isEmpty()) {
            val msg = "받은 파일을 찾지 못했어요. 다른 화질로 다시 시도해 보세요."
            repo.markFailed(job.id, msg, dir.list()?.joinToString(), false)
            return cleanup(dir, JobStatus.FAILED, job, progressTitle, msg)
        }
        val saved = mutableListOf<String>()
        var bytes = 0L
        try {
            for (m in media) {
                val name = OutputFiles.displayName(OutputFiles.titleFromFileName(m.file.name), m.file.extension)
                val item = saver.save(m, name)
                saved += item.uri; bytes += item.bytes
            }
        } catch (e: Exception) {
            saved.forEach { saver.delete(it) }
            val kind = ErrorClassifier.classify(e.message)
            val msg = if (kind == ErrorClassifier.Kind.STORAGE) kind.message else "갤러리에 저장하지 못했어요: ${e.message ?: e::class.simpleName}"
            repo.markFailed(job.id, msg, e.toString(), false)
            return cleanup(dir, JobStatus.FAILED, job, progressTitle, msg)
        }
        val title = OutputFiles.titleFromFileName(media.first().file.name).let { if (media.size > 1) "$it 외 ${media.size - 1}개" else it }
        if (!repo.markDone(job.id, title, saved, bytes)) {
            // 저장 중에 사용자가 취소했다.
            saved.forEach { saver.delete(it) }
            return cleanup(dir, JobStatus.CANCELLED, job, title, null)
        }
        return cleanup(dir, JobStatus.DONE, job, title, null)
    }

    private fun cleanup(dir: File, status: JobStatus, job: DownloadJob, title: String?, message: String?): JobStatus {
        dir.deleteRecursively()
        listener.onFinished(job, status, title, message)
        return status
    }
}
