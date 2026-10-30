package com.miu.lastcontact

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miu.lastcontact.backup.BackupCodec
import com.miu.lastcontact.backup.BackupService
import com.miu.lastcontact.backup.DecodeResult
import com.miu.lastcontact.backup.ImportMode
import com.miu.lastcontact.core.FakeClock
import com.miu.lastcontact.core.Names
import com.miu.lastcontact.data.AppDatabase
import com.miu.lastcontact.data.ContactRepository
import com.miu.lastcontact.data.SavePersonResult
import com.miu.lastcontact.data.SightingResult
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.Direction
import com.miu.lastcontact.domain.ImportPlanner
import com.miu.lastcontact.domain.KakaoExportParser
import com.miu.lastcontact.domain.MappingKind
import com.miu.lastcontact.domain.ParseResult
import com.miu.lastcontact.domain.SenderMapping
import com.miu.lastcontact.domain.Sighting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val seoul = ZoneId.of("Asia/Seoul")
    private val clock = FakeClock(now = LocalDate.of(2026, 10, 30).atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli())
    private lateinit var repo: ContactRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ContactRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun person(name: String, aliases: String = "", phones: String = "", goal: Int? = null) =
        (repo.savePerson(null, name, aliases, phones, "가족", goal, "") as SavePersonResult.Saved).person

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDate.of(y, m, d).atTime(h, 0).atZone(seoul).toInstant().toEpochMilli()

    @Test fun lc_t01_t03_recordMergesSameDayAndChannel() = runTest {
        val mom = person("엄마")
        assertTrue(repo.record(mom.id, LocalDate.of(2026, 10, 30), Channel.MANUAL, Direction.UNKNOWN, note = "전화함"))
        assertFalse(repo.record(mom.id, LocalDate.of(2026, 10, 30), Channel.MANUAL, Direction.UNKNOWN))
        assertTrue(repo.record(mom.id, LocalDate.of(2026, 10, 30), Channel.SMS, Direction.IN, at(2026, 10, 30, 9)))
        assertFalse(repo.record(mom.id, LocalDate.of(2026, 10, 30), Channel.SMS, Direction.OUT, at(2026, 10, 30, 10)))
        val events = repo.observeEvents(mom.id).first()
        assertEquals(2, events.size)
        val sms = events.first { it.channel == Channel.SMS.name }
        assertEquals(Direction.BOTH.name, sms.direction)
        assertEquals(at(2026, 10, 30, 10), sms.atMillis)
        assertEquals("전화함", events.first { it.channel == Channel.MANUAL.name }.note) // 빈 메모로 덮지 않음
        val sum = repo.summaries.first().single()
        assertEquals(LocalDate.of(2026, 10, 30), sum.last!!.day)
        assertEquals(Channel.SMS, sum.last!!.channel) // 같은 날이면 시각이 있는 기록
    }

    @Test fun latestPicksMostRecentDay() = runTest {
        val a = person("가")
        person("나") // 기록 없음
        repo.record(a.id, LocalDate.of(2026, 1, 1), Channel.MANUAL, Direction.UNKNOWN)
        repo.record(a.id, LocalDate.of(2026, 9, 1), Channel.CALL, Direction.OUT, at(2026, 9, 1))
        repo.record(a.id, LocalDate.of(2026, 5, 1), Channel.SMS, Direction.IN)
        val s = repo.summaries.first().associateBy { it.person.name }
        assertEquals(LocalDate.of(2026, 9, 1), s.getValue("가").last!!.day)
        assertEquals(null, s.getValue("나").last)
    }

    @Test fun savePersonValidatesAndNormalizes() = runTest {
        assertTrue(repo.savePerson(null, "  ", "", "", "", null, "") is SavePersonResult.Invalid)
        assertTrue(repo.savePerson(null, "a", "", "", "", 0, "") is SavePersonResult.Invalid)
        val p = person("김민수", aliases = "민수\n김민수\n민수", phones = "+82 10-1111-2222, 02-123-4567")
        assertEquals("민수", p.aliases) // 이름과 같은 별칭·중복 제거
        assertEquals("01011112222,021234567", p.phones)
    }

    @Test fun lc_t07_t08_sightingMatchedUnmatchedIgnoredAndLink() = runTest {
        val mom = person("엄마", aliases = "김영희")
        assertEquals(SightingResult.RECORDED, repo.onSighting(Sighting("김 영희", at(2026, 10, 29), null), Channel.KAKAO_NOTIFICATION))
        assertEquals(SightingResult.UNMATCHED, repo.onSighting(Sighting("민수", at(2026, 10, 20), "동창방"), Channel.KAKAO_NOTIFICATION))
        assertEquals(SightingResult.UNMATCHED, repo.onSighting(Sighting("민수", at(2026, 10, 20, 18), "동창방"), Channel.KAKAO_NOTIFICATION))
        assertEquals(SightingResult.UNMATCHED, repo.onSighting(Sighting("민수", at(2026, 10, 25), null), Channel.KAKAO_NOTIFICATION))
        assertEquals(SightingResult.UNMATCHED, repo.onSighting(Sighting("광고채널", at(2026, 10, 25), null), Channel.KAKAO_NOTIFICATION))
        val unmatched = repo.unmatchedSummary.first().associateBy { it.displayName }
        assertEquals(2, unmatched.getValue("민수").days)
        assertEquals("동창방", unmatched.getValue("민수").roomName)

        repo.ignoreName(Names.key("광고채널"), "광고채널")
        assertEquals(SightingResult.IGNORED, repo.onSighting(Sighting("광고채널", at(2026, 10, 26), null), Channel.KAKAO_NOTIFICATION))

        val minsu = person("김민수")
        assertEquals(2, repo.linkUnmatched(Names.key("민수"), minsu.id))
        assertTrue(repo.unmatchedSummary.first().isEmpty())
        assertEquals("민수", repo.allPeople().first { it.id == minsu.id }.aliases)
        assertEquals(LocalDate.of(2026, 10, 25), repo.summaries.first().first { it.person.id == minsu.id }.last!!.day)
        // 이제 별칭으로 바로 기록된다
        assertEquals(SightingResult.RECORDED, repo.onSighting(Sighting("민수", at(2026, 10, 30), null), Channel.KAKAO_NOTIFICATION))
        assertEquals(1, repo.observeEvents(mom.id).first().size)
    }

    @Test fun lc_t04_importExportCreatesPersonAliasAndDays() = runTest {
        val dad = person("아빠")
        val text = """
            가족방 님과 카카오톡 대화
            2026. 9. 1. 오후 3:12, 김철수 : 다들 잘 지내?
            2026. 9. 2. 오후 3:40, 누나 : 응
            2026. 9. 3. 오전 8:00, 김철수 : 출근
            2026. 9. 3. 오전 9:00, 나 : ㅎㅇ
        """.trimIndent()
        val summary = (KakaoExportParser.parse(text) as ParseResult.Ok).summary
        val maps = listOf(
            SenderMapping("나", MappingKind.ME), SenderMapping("김철수", MappingKind.PERSON, dad.id), SenderMapping("누나", MappingKind.NEW_PERSON),
        )
        val plan = ImportPlanner.plan(summary, maps)
        val r = repo.applyImport(plan, mapOf("new:누나" to "누나"), mapOf(dad.id to listOf("김철수"), "new:누나" to listOf("누나")))
        assertEquals(3, r.newDays)
        assertEquals(1, r.createdPeople)
        val s = repo.summaries.first().associateBy { it.person.name }
        assertEquals(LocalDate.of(2026, 9, 3), s.getValue("아빠").last!!.day)
        assertEquals(LocalDate.of(2026, 9, 2), s.getValue("누나").last!!.day)
        assertEquals("김철수", s.getValue("아빠").person.aliases)
        // 같은 파일 다시 가져오기 → 새 날짜 0, 사람 중복 없음
        val again = repo.applyImport(plan, mapOf("new:누나" to "누나"), emptyMap())
        assertEquals(0, again.newDays)
        assertEquals(2, repo.allPeople().size)
    }

    @Test fun deletingPersonDeletesTheirRecords() = runTest {
        val p = person("삭제될 사람")
        repo.record(p.id, LocalDate.of(2026, 10, 1), Channel.MANUAL, Direction.UNKNOWN)
        repo.deletePerson(p.id)
        assertTrue(db.eventDao().all().isEmpty())
    }

    @Test fun lc_t12_backupAddAndReplace() = runTest {
        val mom = person("엄마", phones = "010-1234-5678", goal = 7)
        repo.record(mom.id, LocalDate.of(2026, 10, 1), Channel.CALL, Direction.IN)
        repo.onSighting(Sighting("모르는이름", at(2026, 10, 2), null), Channel.KAKAO_NOTIFICATION)
        val service = BackupService(db, clock, File(context.filesDir, "bk-test"))
        val json = service.export(null)
        val f = (BackupCodec.decode(json) as DecodeResult.Ok).file
        assertEquals(1, f.people.size)

        service.import(f, ImportMode.ADD, null)
        assertEquals(2, repo.allPeople().size)
        assertEquals(2, db.eventDao().all().size)
        assertEquals(1, db.unmatchedDao().all().size) // 같은 키는 1건

        val (_, pre) = service.import(f, ImportMode.REPLACE, null)
        assertNotNull(pre); assertTrue(pre!!.exists())
        assertEquals(listOf(mom.id), repo.allPeople().map { it.id })
        assertEquals(7, repo.allPeople().single().goalDays)

        assertTrue(BackupCodec.decode("""{"header":{"appId":"com.miu.fold.dashboard","schemaVersion":1,"appVersion":"1","exportedAt":0,"recordCounts":{}}}""") is DecodeResult.Error)
        assertTrue(BackupCodec.decode("oops") is DecodeResult.Error)
        val badRef = json.replace("\"personId\": \"${mom.id}\"", "\"personId\": \"nobody\"")
        assertTrue(BackupCodec.decode(badRef) is DecodeResult.Error)
    }
}
