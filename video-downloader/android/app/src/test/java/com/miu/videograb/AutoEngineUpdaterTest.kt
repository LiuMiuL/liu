package com.miu.videograb

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.miu.videograb.data.SettingsStore
import com.miu.videograb.engine.EngineUpdateOutcome
import com.miu.videograb.engine.UpdatableEngine
import com.miu.videograb.queue.AutoEngineUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoEngineUpdaterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) {
        File(context.filesDir, "datastore/updater-test.preferences_pb").also { it.delete() }
    })
    private var now = 10_000_000_000L
    private var updates = 0
    private var outcome = EngineUpdateOutcome.UPDATED
    private val engine = object : UpdatableEngine {
        override suspend fun update(): EngineUpdateOutcome { updates++; return outcome }
        override fun version() = "2026.10.26"
    }
    private val updater = AutoEngineUpdater(engine, store) { now }

    @After fun tearDown() = scope.cancel()

    // 하루 한 번만: 첫 시도 후 24시간 안에는 다시 하지 않는다
    @Test fun dailyUpdateRespectsInterval() = runBlocking {
        assertTrue(updater.updateIfDue(afterFailure = false))
        now += AutoEngineUpdater.DAILY_INTERVAL_MS - 1
        assertFalse(updater.updateIfDue(afterFailure = false))
        now += 1
        assertTrue(updater.updateIfDue(afterFailure = false))
        assertEquals(2, updates)
    }

    // 실패 직후에는 1시간 간격이면 다시 시도할 수 있다
    @Test fun afterFailureUsesShorterInterval() = runBlocking {
        updater.updateIfDue(afterFailure = false)
        now += AutoEngineUpdater.RETRY_INTERVAL_MS
        assertTrue(updater.updateIfDue(afterFailure = true))
        assertFalse(updater.updateIfDue(afterFailure = true))
    }

    // 이미 최신이면 false(자동 재시도 안 함), 꺼져 있으면 아무 것도 하지 않음, 업데이트 오류는 삼킨다
    @Test fun upToDateDisabledAndErrors() = runBlocking {
        outcome = EngineUpdateOutcome.UP_TO_DATE
        assertFalse(updater.updateIfDue(afterFailure = true))
        store.setAutoUpdateEngine(false)
        now += AutoEngineUpdater.DAILY_INTERVAL_MS
        assertFalse(updater.updateIfDue(afterFailure = false))
        assertEquals(1, updates)
        store.setAutoUpdateEngine(true)
        val failing = AutoEngineUpdater(object : UpdatableEngine {
            override suspend fun update(): EngineUpdateOutcome = throw IllegalStateException("network")
            override fun version() = null
        }, store) { now }
        assertFalse(failing.updateIfDue(afterFailure = false))
    }
}
