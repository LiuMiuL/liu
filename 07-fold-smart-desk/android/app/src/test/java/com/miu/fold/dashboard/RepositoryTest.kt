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

    /** 홈 빠른 메모와 편집기가 같은 메모를 잡고 있을 때 서로 덮어쓰지 않는다. */
    @Test fun twoDraftsOnSameNoteNeverLoseEdits() {
        val scope = TestScope(StandardTestDispatcher())
        val repo = NoteRepository(db, clock)
        val note = kotlinx.coroutines.runBlocking { repo.create("", "원본") }
        val quick = NoteDraft(scope, repo)
        val editor = NoteDraft(scope, repo)
        quick.load(note); editor.load(note)
        scope.runCurrent()

        // 편집기가 먼저 저장 → 고친 게 없는 빠른 메모는 새 내용을 다시 불러온다
        editor.edit(body = "편집기에서 고침")
        editor.flush()
        scope.settle { editor.state.value.status == SaveStatus.SAVED && quick.state.value.body == "편집기에서 고침" }
        assertEquals("편집기에서 고침", quick.state.value.body)

        // 둘 다 고친 상태에서 각각 저장 → 나중 저장은 충돌 사본이 된다
        editor.edit(body = "편집기 2차")
        quick.edit(body = "빠른 메모 2차")
        editor.flush()
        scope.settle { editor.state.value.status == SaveStatus.SAVED }
        quick.flush()
        scope.settle { quick.state.value.status == SaveStatus.SAVED }

        val all = kotlinx.coroutines.runBlocking { repo.active.first() }
        assertEquals(2, all.size)
        assertEquals("편집기 2차", all.first { it.id == note.id }.body)
        val copy = all.first { it.id != note.id }
        assertEquals("빠른 메모 2차", copy.body)
        assertTrue(copy.title.contains("충돌 사본"))
        assertEquals(copy.id, quick.state.value.id)
        assertNotNull(quick.state.value.notice)
    }

    @Test fun draftClosesWhenNoteTrashedElsewhere() {
        val scope = TestScope(StandardTestDispatcher())
        val repo = NoteRepository(db, clock)
        val note = kotlinx.coroutines.runBlocking { repo.create("", "곧 삭제") }
        val quick = NoteDraft(scope, repo)
        quick.load(note)
        scope.runCurrent()
        kotlinx.coroutines.runBlocking { repo.moveToTrash(note.id) }
        scope.settle { quick.state.value.id == null }
        assertEquals(null, quick.state.value.id)
        // 휴지통 메모는 그대로 남아 있다(복원 가능)
        assertEquals(1, kotlinx.coroutines.runBlocking { repo.trash.first() }.size)
    }

    @Test fun t03_saveFailureKeepsDraft() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val full = object : com.miu.fold.dashboard.notes.NoteWriter {
            override suspend fun create(title: String, body: String, tags: String) = throw android.database.sqlite.SQLiteFullException("disk full")
            override suspend fun saveIfUnchanged(id: String, title: String, body: String, tags: String, expectedRevision: Int) = throw android.database.sqlite.SQLiteFullException("disk full")
            override fun observe(id: String) = kotlinx.coroutines.flow.flowOf<com.miu.fold.dashboard.data.NoteEntity?>(null)
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

    @Test fun todoMoveSwapsWithVisibleNeighbourOnly() = runTest {
        val todos = TodoRepository(db, clock)
        val today = LocalDate.of(2027, 1, 1)
        val a = todos.add("오늘 A", dueDate = today)!!
        todos.add("예정 X", dueDate = today.plusDays(5))
        val b = todos.add("오늘 B", dueDate = today)!!
        val ordered = { kotlinx.coroutines.runBlocking { todos.all.first() } }
        // '오늘' 필터 화면: [A, B] — B를 위로 올리면 숨은 X를 건너뛰고 A와 바뀌어야 한다
        val visible = ordered().filter { it.dueDate == today.toString() }
        todos.move(b.id, -1, visible)
        val after = ordered().map { it.title }
        assertEquals(listOf("오늘 B", "예정 X", "오늘 A"), after)
        // 맨 위에서 더 올려도 변화 없음
        todos.move(b.id, -1, ordered().filter { it.dueDate == today.toString() })
        assertEquals(after, ordered().map { it.title })
        assertEquals(3, ordered().map { it.orderKey }.distinct().size)
        assertEquals(a.id, ordered().last().id)
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

    @Test fun timerAlarmFollowsState() = runTest {
        val calls = mutableListOf<Pair<TimerState?, Long?>>()
        val alarm = object : com.miu.fold.dashboard.timer.TimerAlarm {
            override fun sync(s: com.miu.fold.dashboard.timer.TimerSnapshot?) {
                calls += (s?.state to s?.let { it.monotonicAnchor + it.remainingMs })
            }
        }
        val timer = TimerController(db.timerDao(), clock, alarm)
        val startElapsed = clock.elapsed
        timer.start(10 * 60_000, "집중")
        assertEquals(TimerState.RUNNING to startElapsed + 10 * 60_000, calls.last()) // 종료 시각(부팅 기준)에 예약
        clock.advance(3 * 60_000)
        timer.pause()
        assertEquals(TimerState.PAUSED, calls.last().first) // 일시정지 → 예약 취소(RUNNING 아님)
        clock.advance(60_000)
        timer.resume()
        assertEquals(TimerState.RUNNING to clock.elapsed + 7 * 60_000, calls.last())
        // 시스템 시각을 바꿔도 예약 시각(부팅 기준)은 그대로
        clock.now -= 3_600_000
        timer.reconcile()
        assertEquals(TimerState.RUNNING to clock.elapsed + 7 * 60_000, calls.last())
        timer.cancel()
        assertEquals(null to null, calls.last())
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
