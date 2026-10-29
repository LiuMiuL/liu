package com.miu.fold.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.miu.fold.dashboard.backup.BackupCodec
import com.miu.fold.dashboard.backup.BackupService
import com.miu.fold.dashboard.backup.DecodeResult
import com.miu.fold.dashboard.backup.ImportMode
import com.miu.fold.dashboard.core.FakeClock
import com.miu.fold.dashboard.core.SaveStatus
import com.miu.fold.dashboard.dashboard.CardType
import com.miu.fold.dashboard.dashboard.DashboardRepository
import com.miu.fold.dashboard.dashboard.LayoutEditor
import com.miu.fold.dashboard.dashboard.LayoutProfile
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.DashboardCardEntity
import com.miu.fold.dashboard.notes.NoteDraft
import com.miu.fold.dashboard.notes.NoteRepository
import com.miu.fold.dashboard.shortcuts.ShortcutRepository
import com.miu.fold.dashboard.shortcuts.ShortcutSaveResult
import com.miu.fold.dashboard.shortcuts.ShortcutType
import com.miu.fold.dashboard.timer.FocusOutcome
import com.miu.fold.dashboard.timer.FocusResult
import com.miu.fold.dashboard.timer.TimerController
import com.miu.fold.dashboard.timer.TimerState
import com.miu.fold.dashboard.todos.TodoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    /** Room은 자체 executor에서 실행되므로, 테스트 디스패처를 돌리며 조건이 될 때까지 짧게 기다린다. */
    private fun TestScope.settle(timeoutMs: Long = 5_000, until: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            runCurrent()
            if (until()) return
            Thread.sleep(10)
        }
        runCurrent()
    }

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val clock = FakeClock()
    private val dbName = "test.db"

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .allowMainThreadQueries().build()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = open()
    }

    @After fun tearDown() { db.close(); context.deleteDatabase(dbName) }

    @Test fun t05_t06_narrowChangeDoesNotTouchWide() = runTest {
        val repo = DashboardRepository(db)
        repo.ensureDefaults()
        val s = repo.state.first()
        val wideBefore = s.layouts.getValue(LayoutProfile.WIDE)
        val narrow = s.layouts.getValue(LayoutProfile.NARROW)
        val moved = LayoutEditor.move(narrow, narrow.first().cardId, 3)
        repo.saveLayout(LayoutProfile.NARROW, moved)
        val after = repo.state.first()
        assertEquals(LayoutEditor.normalize(moved), after.layouts.getValue(LayoutProfile.NARROW))
        assertEquals(wideBefore, after.layouts.getValue(LayoutProfile.WIDE))
    }

    @Test fun ensureDefaultsDoesNotOverwriteUserLayout() = runTest {
        val repo = DashboardRepository(db)
        repo.ensureDefaults()
        val wide = repo.state.first().layouts.getValue(LayoutProfile.WIDE)
        val custom = LayoutEditor.setVisible(LayoutEditor.move(wide, wide[0].cardId, 4), wide[1].cardId, false)
        repo.saveLayout(LayoutProfile.WIDE, custom)
        repo.ensureDefaults() // 다음 실행
        assertEquals(LayoutEditor.normalize(custom), repo.state.first().layouts.getValue(LayoutProfile.WIDE))
        assertEquals(CardType.entries.size, repo.state.first().cards.size)
    }

    @Test fun t04_resetLayoutKeepsData() = runTest {
        val repo = DashboardRepository(db)
        repo.ensureDefaults()
        val notes = NoteRepository(db, clock)
        val todos = TodoRepository(db, clock)
        notes.create("", "메모 내용")
        todos.add("할 일")
        val narrow = repo.state.first().layouts.getValue(LayoutProfile.NARROW)
        val todoCard = repo.state.first().cards.first { it.type == CardType.TODAY_TODOS.typeId }
        repo.saveLayout(LayoutProfile.NARROW, LayoutEditor.setVisible(narrow, todoCard.id, false))
        assertEquals(1, todos.all.first().size) // 숨겨도 할 일은 남음
        val wideBefore = repo.state.first().layouts.getValue(LayoutProfile.WIDE)
        repo.resetLayout(LayoutProfile.NARROW)
        assertTrue(repo.state.first().layouts.getValue(LayoutProfile.NARROW).all { it.visible })
        assertEquals(wideBefore, repo.state.first().layouts.getValue(LayoutProfile.WIDE))
        assertEquals(1, notes.active.first().size)
        assertEquals(1, todos.all.first().size)
        repo.deleteCard(todoCard.id)
        assertEquals(1, todos.all.first().size) // 카드 삭제도 데이터는 유지
    }

    @Test fun addCardRespectsLimit() = runTest {
        val repo = DashboardRepository(db)
        repo.ensureDefaults()
        assertFalse(repo.addCard(CardType.QUICK_NOTE))
        val card = repo.state.first().cards.first { it.type == CardType.RECENT.typeId }
        repo.deleteCard(card.id)
        assertTrue(repo.addCard(CardType.RECENT))
        val s = repo.state.first()
        val newId = s.cards.first { it.type == CardType.RECENT.typeId }.id
        assertTrue(LayoutProfile.entries.all { p -> s.layouts.getValue(p).any { it.cardId == newId } })
    }

    @Test fun t02_noteSurvivesReopen() = runTest {
        val body = "가".repeat(500)
        val id = NoteRepository(db, clock).create("", body).id
        db.close()
        db = open()
        assertEquals(body, NoteRepository(db, clock).get(id)!!.body)
    }

    @Test fun noteDraftDebouncesAndSaves() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val repo = NoteRepository(db, clock)
        val draft = NoteDraft(scope, repo)
        draft.edit(body = "a")
        draft.edit(body = "ab")
        scope.advanceTimeBy(400); scope.runCurrent()
        assertEquals(SaveStatus.DIRTY, draft.state.value.status)
        scope.advanceTimeBy(200)
        scope.settle { draft.state.value.status == SaveStatus.SAVED }
        assertEquals(SaveStatus.SAVED, draft.state.value.status)
        val id = draft.state.value.id
        assertNotNull(id)
        draft.edit(body = "abc")
        draft.flush()
        scope.settle { draft.state.value.status == SaveStatus.SAVED }
        assertEquals(id, draft.state.value.id) // 새 메모가 또 생기지 않음
        kotlinx.coroutines.runBlocking { assertEquals(1, repo.active.first().size); assertEquals("abc", repo.get(id!!)!!.body) }
    }

    @Test fun t03_saveFailureKeepsDraft() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val full = object : com.miu.fold.dashboard.notes.NoteWriter {
            override suspend fun create(title: String, body: String, tags: String) = throw android.database.sqlite.SQLiteFullException("disk full")
            override suspend fun save(id: String, title: String, body: String, tags: String) = throw android.database.sqlite.SQLiteFullException("disk full")
        }
        val draft = NoteDraft(scope, full)
        draft.edit(body = "잃으면 안 되는 초안")
        scope.advanceTimeBy(600)
        scope.settle { draft.state.value.status == SaveStatus.FAILED }
        assertEquals(SaveStatus.FAILED, draft.state.value.status)
        assertEquals("잃으면 안 되는 초안", draft.state.value.body)
        assertTrue(draft.state.value.hasUnsaved)
        assertTrue(draft.state.value.error!!.contains("저장 공간"))
    }

    @Test fun t19_todoDoneUndoSpamConverges() = runTest {
        val todos = TodoRepository(db, clock)
        val t = todos.add("연타", dueDate = LocalDate.of(2027, 1, 1))!!
        repeat(7) { i -> todos.setDone(t.id, i % 2 == 0) }
        val list = todos.all.first()
        assertEquals(1, list.size)
        assertNotNull(list[0].doneAt) // 마지막 요청(i=6 → 완료)
        todos.setDone(t.id, false)
        assertEquals(null, todos.get(t.id)!!.doneAt)
        assertEquals("2027-01-01", todos.get(t.id)!!.dueDate)
    }

    @Test fun t10_t11_timerSingleSessionAndSingleRecord() = runTest {
        val timer = TimerController(db.timerDao(), clock)
        val a = timer.start(10 * 60_000, "a")
        val b = timer.start(10 * 60_000, "b") // 연타
        assertEquals(a.id, b.id)
        assertEquals(1, db.timerDao().active().size)
        // 프로세스 재생성: 새 컨트롤러가 같은 DB 기준값에서 복원
        clock.advance(3 * 60_000)
        val restored = TimerController(db.timerDao(), clock)
        val snap = restored.reconcile()!!
        assertEquals(7 * 60_000, restored.remaining(snap))
        clock.advance(8 * 60_000)
        val done = restored.reconcile()
        assertEquals(TimerState.COMPLETED, done!!.state)
        // 완료 이벤트 중복 도착
        val dup = FocusResult(a.id, a.startedAt, clock.nowMillis(), 10 * 60_000, FocusOutcome.COMPLETED)
        restored.recordCompletion(dup); restored.recordCompletion(dup); restored.reconcile()
        assertEquals(1, db.timerDao().recordCount(a.id))
        assertEquals(0, db.timerDao().active().size)
    }

    @Test fun shortcutRejectsUnsafe() = runTest {
        val repo = ShortcutRepository(db, clock)
        assertTrue(repo.save(null, ShortcutType.WEB, "", "javascript:alert(1)", null) is ShortcutSaveResult.Invalid)
        assertTrue(repo.save(null, ShortcutType.APP, "", "not a package", null) is ShortcutSaveResult.Invalid)
        assertTrue(repo.save(null, ShortcutType.INTERNAL, "", "rm_rf", null) is ShortcutSaveResult.Invalid)
        val ok = repo.save(null, ShortcutType.WEB, "", "claude.ai", null) as ShortcutSaveResult.Saved
        assertEquals("claude.ai", ok.shortcut.label)
        assertEquals("https://claude.ai", ok.shortcut.target)
    }

    @Test fun t17_t20_backupRoundTripAndUnknownCard() = runTest {
        val dash = DashboardRepository(db)
        dash.ensureDefaults()
        val notes = NoteRepository(db, clock)
        val todos = TodoRepository(db, clock)
        val n = notes.create("제목", "본문")
        todos.add("메모에서", sourceNoteId = n.id)
        ShortcutRepository(db, clock).save(null, ShortcutType.WEB, "클로드", "https://claude.ai", null)
        TimerController(db.timerDao(), clock).apply { start(60_000, "x"); clock.advance(70_000); reconcile() }
        // 미래 버전 카드 종류가 섞인 상태
        db.dashboardDao().upsertCard(DashboardCardEntity("future", "pc_status_v9", "PC 상태"))
        dash.ensureDefaults()
        val dir = File(context.filesDir, "backups-test")
        val service = BackupService(db, dash, clock, dir)
        val json = service.export(null)
        assertFalse(json.contains("token", ignoreCase = true))

        val decoded = BackupCodec.decode(json) as DecodeResult.Ok
        assertTrue(decoded.warnings.any { it.contains("지원하지 않는 카드") })

        // 새 데이터로 추가: 기존은 그대로, 복사본이 새 ID로 추가, 메모 참조가 새 ID로 연결
        service.import(decoded.file, ImportMode.ADD_AS_NEW, null)
        val allNotes = notes.active.first()
        assertEquals(2, allNotes.size)
        val allTodos = todos.all.first()
        assertEquals(2, allTodos.size)
        assertTrue(allTodos.all { t -> allNotes.any { it.id == t.sourceNoteId } })
        assertNotEquals(allTodos[0].sourceNoteId, allTodos[1].sourceNoteId)
        assertEquals(1, db.timerDao().allRecords().size) // sessionId 중복은 1건

        // 전체 교체: 이전 상태를 내부 파일로 먼저 저장
        val summary = service.import(decoded.file, ImportMode.REPLACE_ALL, null)
        assertTrue(summary.preRestoreBackup!!.exists())
        assertEquals(1, notes.active.first().size)
        assertEquals(n.id, notes.active.first()[0].id)
        val s = dash.state.first()
        assertTrue(s.cards.any { it.type == "pc_status_v9" })
        assertTrue(LayoutProfile.entries.all { p -> s.layouts.getValue(p).size == s.cards.size })
    }

    @Test fun searchCoversOnlyLocalDataAndEscapesWildcards() = runTest {
        NoteRepository(db, clock).create("", "폴드 100% 활용법")
        TodoRepository(db, clock).add("폴드 케이스 사기")
        ShortcutRepository(db, clock).save(null, ShortcutType.WEB, "폴드 커뮤니티", "https://example.com", null)
        val search = com.miu.fold.dashboard.search.SearchRepository(db)
        val all = com.miu.fold.dashboard.recent.RecentType.entries.toSet()
        assertEquals(3, search.search("폴드", all).size)
        assertEquals(1, search.search("100%", all).size) // %는 와일드카드가 아니라 문자로 검색
        assertEquals(0, search.search("_", all).size)
        assertEquals(1, search.search("폴드", setOf(com.miu.fold.dashboard.recent.RecentType.TODO)).size)
        assertTrue(search.search("   ", all).isEmpty())
    }

    @Test fun fixtureBackupDecodesWithWarnings() {
        val text = File(System.getProperty("user.dir"), "../../tests/fixtures/backup-unknown-card.json").readText()
        val r = BackupCodec.decode(text) as DecodeResult.Ok
        assertEquals(1, r.file.shortcuts.size)
        assertTrue(r.warnings.any { it.contains("지원하지 않는 카드") })
        assertTrue(r.warnings.any { it.contains("바로가기") })
    }

    @Test fun backupRejectsForeignAndTampered() {
        val other = """{"header":{"appId":"com.other.app","schemaVersion":1,"appVersion":"1","exportedAt":0,"recordCounts":{}}}"""
        assertTrue(BackupCodec.decode(other) is DecodeResult.Error)
        val future = """{"header":{"appId":"com.miu.fold.dashboard","schemaVersion":99,"appVersion":"9","exportedAt":0,"recordCounts":{}}}"""
        assertTrue(BackupCodec.decode(future) is DecodeResult.Error)
        val tampered = """{"header":{"appId":"com.miu.fold.dashboard","schemaVersion":1,"appVersion":"1","exportedAt":0,"recordCounts":{"notes":5}},"notes":[]}"""
        assertTrue(BackupCodec.decode(tampered) is DecodeResult.Error)
        assertTrue(BackupCodec.decode("not json") is DecodeResult.Error)
        val badShortcut = """{"header":{"appId":"com.miu.fold.dashboard","schemaVersion":1,"appVersion":"1","exportedAt":0,"recordCounts":{}},
            "shortcuts":[{"id":"s","type":"WEB","label":"x","target":"javascript:alert(1)","updatedAt":0}]}"""
        val r = BackupCodec.decode(badShortcut) as DecodeResult.Ok
        assertTrue(r.file.shortcuts.isEmpty())
        assertTrue(r.warnings.isNotEmpty())
    }
}
