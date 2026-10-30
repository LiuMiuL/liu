package com.miu.videograb.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

/** youtubedl-android(yt-dlp + Python + FFmpeg + QuickJS)로 받는다. */
class YtDlpEngine(private val context: Context) : VideoEngine, UpdatableEngine {

    @Volatile private var initialized = false

    /** 처음 한 번 Python·yt-dlp·FFmpeg 파일을 앱 내부 저장소에 푼다. 수 초 걸리므로 IO 스레드에서 부른다. */
    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        YoutubeDL.getInstance().init(context)
        FFmpeg.getInstance().init(context)
        initialized = true
    }

    override suspend fun download(
        jobId: String, url: String, preset: QualityPreset, outputDir: File,
        onProgress: (Float, Long, String) -> Unit,
    ): EngineResult = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val request = YoutubeDLRequest(url).addCommands(DownloadCommand.options(preset, outputDir.absolutePath))
            runInterruptible {
                YoutubeDL.getInstance().execute(request, jobId) { progress, eta, line -> onProgress(progress, eta, line) }
            }
            EngineResult.Success
        } catch (e: YoutubeDL.CanceledException) {
            EngineResult.Cancelled
        } catch (e: InterruptedException) {
            EngineResult.Cancelled
        } catch (e: YoutubeDLException) {
            EngineResult.Failure(e.message ?: e.toString())
        } finally {
            // 작업자가 멈추거나 코루틴이 취소돼도 yt-dlp 프로세스가 남지 않게 한다.
            YoutubeDL.getInstance().destroyProcessById(jobId)
        }
    }

    override fun cancel(jobId: String) {
        YoutubeDL.getInstance().destroyProcessById(jobId)
    }

    /** GitHub의 yt-dlp **안정판**으로 업데이트한다(개발판 master는 쓰지 않음). 네트워크가 필요하다. */
    override suspend fun update(): EngineUpdateOutcome = withContext(Dispatchers.IO) {
        ensureInitialized()
        when (YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)) {
            YoutubeDL.UpdateStatus.DONE -> EngineUpdateOutcome.UPDATED
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> EngineUpdateOutcome.UP_TO_DATE
            else -> EngineUpdateOutcome.UP_TO_DATE
        }
    }

    override fun version(): String? = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
}
