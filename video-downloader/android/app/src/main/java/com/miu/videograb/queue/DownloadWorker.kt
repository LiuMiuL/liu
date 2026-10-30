package com.miu.videograb.queue

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.miu.videograb.VideoGrabApp
import com.miu.videograb.data.DownloadJob
import com.miu.videograb.data.JobStatus

/** 대기열이 빌 때까지 받는다. 오래 걸리므로 알림을 띄운 포그라운드 작업으로 실행한다. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground("다운로드 준비 중", null, 0)

    private fun foreground(title: String, progress: Float?, remaining: Int) = ForegroundInfo(
        Notifications.ID_PROGRESS,
        Notifications.progress(applicationContext, title, progress, remaining),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    override suspend fun doWork(): Result {
        val app = applicationContext as VideoGrabApp
        // 시스템이 포그라운드 시작을 막아도(백그라운드 제한) 다운로드는 계속 시도한다.
        runCatching { setForeground(getForegroundInfo()) }
        // 앱이 강제 종료돼 '다운로드 중'으로 남은 작업은 이어 받기로 다시 시작한다(yt-dlp가 .part 파일에서 이어 받음).
        app.repository.requeueInterrupted()
        // 엔진 자동 업데이트(하루 한 번, 설정에서 끌 수 있음). 실패해도 기존 엔진으로 계속한다.
        runCatching { app.engineUpdater.updateIfDue(afterFailure = false) }
        val listener = object : QueueListener {
            override fun onStarted(job: DownloadJob, remaining: Int) {
                runCatching { setForegroundAsync(foreground(job.title ?: job.url, null, remaining)) }
            }

            override fun onProgress(job: DownloadJob, title: String?, progress: Float?, remaining: Int) {
                runCatching { setForegroundAsync(foreground(title ?: job.url, progress, remaining)) }
            }

            override fun onFinished(job: DownloadJob, status: JobStatus, title: String?, message: String?) {
                val name = title ?: job.title ?: job.url
                when (status) {
                    JobStatus.DONE -> Notifications.result(applicationContext, job.id.hashCode(), "저장 완료", name)
                    JobStatus.FAILED -> Notifications.result(applicationContext, job.id.hashCode(), "다운로드 실패", "$name\n${message ?: ""}")
                    else -> Unit
                }
            }
        }
        QueueProcessor(app.repository, app.engine, app.saver, app.workRoot, listener, app.engineUpdater).runUntilEmpty()
        return Result.success()
    }
}
