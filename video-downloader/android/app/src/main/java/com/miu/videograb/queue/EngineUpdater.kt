package com.miu.videograb.queue

import com.miu.videograb.data.SettingsStore
import com.miu.videograb.engine.EngineUpdateOutcome
import com.miu.videograb.engine.UpdatableEngine

/** 대기열 처리 중 엔진 자동 업데이트. 테스트에서는 가짜로 바꾼다. */
interface EngineUpdater {
    /**
     * 자동 업데이트가 켜져 있고 마지막 시도 후 충분한 시간이 지났으면 업데이트한다.
     * [afterFailure]면 사이트 변경 오류 직후라 간격을 짧게(1시간) 본다. 새 버전을 받았으면 true.
     */
    suspend fun updateIfDue(afterFailure: Boolean): Boolean
}

class AutoEngineUpdater(
    private val engine: UpdatableEngine,
    private val settings: SettingsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : EngineUpdater {
    override suspend fun updateIfDue(afterFailure: Boolean): Boolean {
        val s = settings.current()
        if (!s.autoUpdateEngine) return false
        val interval = if (afterFailure) RETRY_INTERVAL_MS else DAILY_INTERVAL_MS
        val last = s.lastEngineUpdateAt
        if (last != null && clock() - last < interval) return false
        // 실패해도 다운로드는 기존 엔진으로 계속한다. 시도 시각은 기록해 반복 시도를 막는다.
        settings.setLastEngineUpdateAt(clock())
        return runCatching { engine.update() == EngineUpdateOutcome.UPDATED }.getOrDefault(false)
    }

    companion object {
        const val DAILY_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val RETRY_INTERVAL_MS = 60 * 60 * 1000L
    }
}
