package com.miu.videograb

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.miu.videograb.data.JobStatus
import com.miu.videograb.queue.DownloadScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/** "공유 → VideoGrab": 대기열에 들어가고, 다운로드 작업이 예약되고, 화면이 바로 닫히는지. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareReceiverTest {
    private val app = ApplicationProvider.getApplicationContext<VideoGrabApp>()

    @Before fun initWorkManager() = WorkManagerTestInitHelper.initializeTestWorkManager(app)

    private fun share(text: String): ActivityScenario<ShareReceiverActivity> =
        ActivityScenario.launch(Intent(app, ShareReceiverActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text))

    private fun waitFor(condition: () -> Boolean) {
        repeat(200) { if (condition()) return; ShadowLooper.idleMainLooper(); Thread.sleep(20) }
        error("timeout")
    }

    @Test fun shareQueuesDownloadAndFinishes() {
        val scenario = share("재밌는 영상 https://youtu.be/dQw4w9WgXcQ?si=xyz")
        waitFor { ShadowToast.getLatestToast() != null }
        val jobs = runBlocking { app.repository.jobs.first() }
        assertEquals(1, jobs.size)
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=xyz", jobs[0].url)
        assertEquals(JobStatus.QUEUED, jobs[0].status)
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith("다운로드 시작 1개"))
        val work = WorkManager.getInstance(app).getWorkInfosForUniqueWork(DownloadScheduler.UNIQUE_NAME).get()
        assertEquals(1, work.size)
        assertTrue(work[0].state == WorkInfo.State.ENQUEUED || work[0].state == WorkInfo.State.RUNNING || work[0].state == WorkInfo.State.BLOCKED)
        scenario.onActivity { assertTrue(it.isFinishing) }
    }

    @Test fun shareWithoutLinkShowsMessageAndQueuesNothing() {
        share("링크 없는 글")
        waitFor { ShadowToast.getLatestToast() != null }
        assertEquals("공유된 내용에서 영상 링크를 찾지 못했어요.", ShadowToast.getTextOfLatestToast())
        assertTrue(runBlocking { app.repository.jobs.first() }.isEmpty())
        assertTrue(WorkManager.getInstance(app).getWorkInfosForUniqueWork(DownloadScheduler.UNIQUE_NAME).get().isEmpty())
        shadowOf(app)
    }
}
