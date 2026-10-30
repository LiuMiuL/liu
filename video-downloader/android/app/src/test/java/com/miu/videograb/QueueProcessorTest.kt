package com.miu.videograb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miu.videograb.data.JobDatabase
import com.miu.videograb.data.JobRepository
import com.miu.videograb.data.JobStatus
import com.miu.videograb.engine.EngineResult
import com.miu.videograb.engine.OutputFiles
import com.miu.videograb.engine.QualityPreset
import com.miu.videograb.engine.VideoEngine
import com.miu.videograb.queue.EngineUpdater
import com.miu.videograb.queue.QueueProcessor
import com.miu.videograb.save.MediaSaver
import com.miu.videograb.save.SavedItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** 가짜 엔진: 링크별로 정한 동작을 한다. 실제 yt-dlp 출력 줄을 흉내 낸다. */
class FakeEngine : VideoEngine {
    val calls = mutableListOf<String>()
    var behavior: (url: String, dir: File, progress: (Float, Long, String) -> Unit) -> EngineResult = { url, dir, p ->
        val name = "제목-${url.substringAfterLast('/')} [${url.hashCode().toUInt()}]"
        p(-1f, -1, "[download] Destination: ${dir.absolutePath}/$name.f137.mp4")
        p(50f, 10, "[download]  50.0% of 1MiB")
        p(100f, 0, "[download] 100% of 1MiB")
        File(dir, "$name.mp4").writeBytes(ByteArray(1000))
        File(dir, "$name.f137.mp4.part").writeBytes(ByteArray(10))
        EngineResult.Success
    }
    val cancelled = mutableSetOf<String>()
    var cancelSignal = CompletableDeferred<Unit>()

    override suspend fun download(jobId: String, url: String, preset: QualityPreset, outputDir: File, onProgress: (Float, Long, String) -> Unit): EngineResult {
        calls += url
        return behavior(url, outputDir, onProgress)
    }

    override fun cancel(jobId: String) { cancelled += jobId; cancelSignal.complete(Unit) }
}

class FakeSaver(var failOn: Int = -1) : MediaSaver {
    val saved = mutableListOf<Pair<String, String>>() // name, mime
    val deleted = mutableListOf<String>()
    override suspend fun save(media: OutputFiles.Media, displayName: String): SavedItem {
        if (saved.size == failOn) throw IOException("No space left on device")
        saved += displayName to media.mimeType
        return SavedItem("content://media/external/video/media/${saved.size}", media.file.length())
    }
    override suspend fun delete(uri: String) { deleted += uri }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueProcessorTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: JobDatabase
    private lateinit var repo: JobRepository
    private val engine = FakeEngine()
    private val saver = FakeSaver()
    private var clock = 1_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), JobDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = JobRepository(db.jobs()) { clock }
    }
    @After fun tearDown() = db.close()

    private fun processor(updater: EngineUpdater? = null) = QueueProcessor(repo, engine, saver, tmp.root, progressIntervalMs = 0, updater = updater)
    private fun all() = runBlocking { repo.jobs.first() }.associateBy { it.url }

    // 공유 텍스트의 링크 여러 개 → 순서대로 받아 갤러리에 저장, 임시 폴더 정리
    @Test fun processesQueueInOrderAndSaves() = runBlocking {
        val r = repo.enqueueFromText("https://a.com/v/1 그리고 https://a.com/v/2\nhttps://a.com/v/3", QualityPreset.P1080)
        assertEquals(3, r.addedCount)
        val summary = processor().runUntilEmpty()
        assertEquals(QueueProcessor.Summary(3, 0, 0), summary)
        assertEquals(listOf("https://a.com/v/1", "https://a.com/v/2", "https://a.com/v/3"), engine.calls)
        assertEquals(listOf("제목-1.mp4", "제목-2.mp4", "제목-3.mp4"), saver.saved.map { it.first })
        assertTrue(saver.saved.all { it.second == "video/mp4" })
        val j = all().getValue("https://a.com/v/1")
        assertEquals(JobStatus.DONE, j.status)
        assertEquals("제목-1", j.title)
        assertEquals(100f, j.progress)
        assertEquals(1000L, j.totalBytes)
        assertEquals("content://media/external/video/media/1", j.firstOutputUri)
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    // 대기·진행 중인 같은 링크는 다시 넣지 않는다. 끝난 뒤에는 다시 받을 수 있다.
    @Test fun duplicateActiveLinksAreSkipped() = runBlocking {
        repo.enqueueFromText("https://youtu.be/AAA", QualityPreset.P1080)
        val dup = repo.enqueueFromText("https://YOUTU.BE/AAA/", QualityPreset.P720)
        assertEquals(0, dup.addedCount); assertEquals(1, dup.duplicates.size)
        processor().runUntilEmpty()
        assertEquals(1, repo.enqueueFromText("https://youtu.be/AAA", QualityPreset.P1080).addedCount)
        assertTrue(repo.enqueueFromText("링크 없음", QualityPreset.P1080).invalidText)
    }

    // 실패한 링크는 안내 문구와 함께 실패로 남고 다음 링크는 계속 받는다
    @Test fun failureIsClassifiedAndQueueContinues() = runBlocking {
        val default = engine.behavior
        engine.behavior = { url, dir, p ->
            if (url.endsWith("/bad")) EngineResult.Failure("ERROR: [youtube] x: Private video. Sign in if you've been granted access")
            else default(url, dir, p)
        }
        repo.enqueueFromText("https://a.com/bad https://a.com/good", QualityPreset.P1080)
        assertEquals(QueueProcessor.Summary(1, 1, 0), processor().runUntilEmpty())
        val bad = all().getValue("https://a.com/bad")
        assertEquals(JobStatus.FAILED, bad.status)
        assertEquals("비공개이거나 삭제된 영상이에요.", bad.errorMessage)
        assertTrue(bad.errorDetail!!.contains("Private video"))
        assertFalse(bad.suggestEngineUpdate)
        // 다시 시도하면 대기열로 돌아간다
        assertTrue(repo.retry(bad.id)); assertEquals(JobStatus.QUEUED, repo.get(bad.id)!!.status)
    }

    // 사이트 변경 오류 → 엔진 업데이트 성공 → 그 링크만 자동으로 한 번 더 시도
    @Test fun extractorErrorTriggersEngineUpdateAndOneRetry() = runBlocking {
        var attempts = 0
        val default = engine.behavior
        engine.behavior = { url, dir, p ->
            attempts++
            if (attempts == 1) EngineResult.Failure("ERROR: [youtube] x: Unable to extract nsig function code; please report this issue")
            else default(url, dir, p)
        }
        var updates = 0
        val updater = object : EngineUpdater { override suspend fun updateIfDue(afterFailure: Boolean): Boolean { updates++; return afterFailure } }
        repo.enqueueFromText("https://a.com/v/9", QualityPreset.P1080)
        assertEquals(QueueProcessor.Summary(1, 0, 0), processor(updater).runUntilEmpty())
        assertEquals(2, attempts); assertEquals(1, updates)
        val j = all().values.single()
        assertEquals(JobStatus.DONE, j.status); assertTrue(j.autoRetried)
    }

    // 자동 재시도는 한 번만: 업데이트 뒤에도 실패하면 실패로 남기고 업데이트 권유 표시
    @Test fun autoRetryHappensOnlyOnce() = runBlocking {
        engine.behavior = { _, _, _ -> EngineResult.Failure("ERROR: Unable to extract video data") }
        val updater = object : EngineUpdater { override suspend fun updateIfDue(afterFailure: Boolean) = true }
        repo.enqueueFromText("https://a.com/v/9", QualityPreset.P1080)
        assertEquals(QueueProcessor.Summary(0, 1, 0), processor(updater).runUntilEmpty())
        assertEquals(2, engine.calls.size)
        val j = all().values.single()
        assertEquals(JobStatus.FAILED, j.status); assertTrue(j.suggestEngineUpdate)
    }

    // 받는 중 취소 → 엔진 중지, 저장하지 않고 임시 파일 삭제
    @Test fun cancelWhileRunning() = runBlocking {
        val started = CompletableDeferred<Unit>()
        engine.behavior = { _, dir, p ->
            File(dir, "x [1].mp4.part").writeBytes(ByteArray(5))
            p(30f, 5, "[download]  30.0%")
            started.complete(Unit)
            runBlocking { engine.cancelSignal.await() }
            EngineResult.Cancelled
        }
        repo.enqueueFromText("https://a.com/v/1", QualityPreset.P1080)
        val id = all().values.single().id
        val run = async(kotlinx.coroutines.Dispatchers.IO) { processor().runUntilEmpty() }
        started.await()
        assertTrue(repo.cancel(id)); engine.cancel(id)
        assertEquals(QueueProcessor.Summary(0, 0, 1), run.await())
        assertEquals(JobStatus.CANCELLED, repo.get(id)!!.status)
        assertTrue(saver.saved.isEmpty())
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    // 앱 강제 종료로 '다운로드 중'에 남은 작업은 다음 실행에서 대기열로 돌아간다(이어 받기)
    @Test fun interruptedJobsAreRequeued() = runBlocking {
        repo.enqueueFromText("https://a.com/v/1", QualityPreset.P1080)
        val id = all().values.single().id
        assertTrue(repo.markStarted(id))
        assertEquals(1, repo.requeueInterrupted())
        val j = repo.get(id)!!
        assertEquals(JobStatus.QUEUED, j.status); assertEquals(1, j.restarts)
    }

    // 엔진은 성공했는데 파일이 없으면 실패
    @Test fun missingOutputFails() = runBlocking {
        engine.behavior = { _, _, _ -> EngineResult.Success }
        repo.enqueueFromText("https://a.com/v/1", QualityPreset.P1080)
        processor().runUntilEmpty()
        assertEquals(JobStatus.FAILED, all().values.single().status)
    }

    // 여러 파일 중 하나 저장 실패 → 앞서 저장한 것도 지우고 실패(공간 부족 안내)
    @Test fun saveFailureRollsBack() = runBlocking {
        engine.behavior = { _, dir, _ ->
            File(dir, "a [1].mp4").writeBytes(ByteArray(200)); File(dir, "b [2].mp4").writeBytes(ByteArray(100))
            EngineResult.Success
        }
        saver.failOn = 1
        repo.enqueueFromText("https://a.com/list", QualityPreset.P1080)
        processor().runUntilEmpty()
        val j = all().values.single()
        assertEquals(JobStatus.FAILED, j.status)
        assertEquals("기기 저장 공간이 부족해요.", j.errorMessage)
        assertEquals(listOf("content://media/external/video/media/1"), saver.deleted)
    }

    // 재생목록처럼 파일이 여러 개면 모두 저장하고 제목에 개수 표시
    @Test fun multipleOutputs() = runBlocking {
        engine.behavior = { _, dir, _ ->
            File(dir, "첫 영상 [1].mp4").writeBytes(ByteArray(300)); File(dir, "둘째 [2].mp4").writeBytes(ByteArray(100))
            EngineResult.Success
        }
        repo.enqueueFromText("https://a.com/playlist", QualityPreset.P1080)
        processor().runUntilEmpty()
        val j = all().values.single()
        assertEquals("첫 영상 외 1개", j.title); assertEquals(2, j.outputCount)
        yield()
        assertNull(null)
    }
}
