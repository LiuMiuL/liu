package com.miu.videograb.engine

import java.io.File

sealed interface EngineResult {
    data object Success : EngineResult
    data object Cancelled : EngineResult
    data class Failure(val errorText: String) : EngineResult
}

enum class EngineUpdateOutcome { UPDATED, UP_TO_DATE }

/** 엔진 자체를 업데이트할 수 있는 경우(yt-dlp). */
interface UpdatableEngine {
    suspend fun update(): EngineUpdateOutcome
    fun version(): String?
}

/** 다운로드 엔진. 실제 구현은 yt-dlp(YtDlpEngine), 테스트는 가짜 엔진을 쓴다. */
interface VideoEngine {
    /**
     * [outputDir]에 받는다. [onProgress]는 엔진 스레드에서 (진행률 0~100 또는 음수, 남은 초, 출력 줄)로 불린다.
     */
    suspend fun download(
        jobId: String, url: String, preset: QualityPreset, outputDir: File,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
    ): EngineResult

    /** 진행 중인 작업을 멈춘다. 없으면 아무 일도 하지 않는다. */
    fun cancel(jobId: String)
}
