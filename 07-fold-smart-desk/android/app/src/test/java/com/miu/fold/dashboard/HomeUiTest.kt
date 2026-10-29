package com.miu.fold.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import com.miu.fold.dashboard.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Robolectric(JVM) 위에서 실제 MainActivity를 띄우는 UI 테스트.
 * 실제 폴드 기기·에뮬레이터 검증을 대체하지 않는다. 창 크기는 리소스 한정자로 바꿔 접기/펼치기를 흉내 낸다.
 */
@RunWith(RobolectricTestRunner::class)
class HomeUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<FoldDeskApp>()

    @Before fun waitReady() {
        runCatching { rule.waitUntil(20_000) { rule.onAllNodesWithText("빠른 메모").fetchSemanticsNodes().isNotEmpty() } }
            .onFailure {
                System.err.println(rule.onRoot(useUnmergedTree = true).printToString())
                System.err.println(runBlocking { app.container.db.dashboardDao().cards() })
                throw it
            }
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun t01_firstRunNarrowShowsCoreCardsWithoutOtherApps() {
        rule.onNodeWithText("빠른 메모").assertIsDisplayed()
        rule.onNodeWithText("오늘 할 일").assertIsDisplayed()
        rule.onNodeWithText("홈").assertIsDisplayed() // 하단 메뉴
        rule.onNodeWithText("기록").assertIsDisplayed()
        // 타이머·바로가기 카드는 스크롤 아래에 있다
        rule.onNodeWithText("집중 타이머").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("바로가기").performScrollTo().assertIsDisplayed()
        // 다른 앱 연동 카드가 기본으로 깔리지 않는다
        assertTrue(rule.onAllNodesWithText("연동 필요").fetchSemanticsNodes().isEmpty())
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun quickMemoAutosavesToDatabase() {
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("폴드에서 쓴 아이디어")
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("저장됨").fetchSemanticsNodes().isNotEmpty() }
        val notes = runBlocking { app.container.db.noteDao().all() }
        assertEquals(1, notes.size)
        assertEquals("폴드에서 쓴 아이디어", notes[0].body)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun timerStartAndPauseFromHomeCard() {
        rule.onNodeWithText("집중 25분").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("일시정지").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("일시정지").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("재개").fetchSemanticsNodes().isNotEmpty() }
        val active = runBlocking { app.container.db.timerDao().active() }
        assertEquals(1, active.size)
        assertEquals("PAUSED", active[0].state)
    }

    @Test @Config(qualifiers = "w900dp-h800dp")
    fun t07_foldDuringEditKeepsLockedProfileAndCancelRestores() {
        val before = runBlocking { app.container.db.dashboardDao().allPlacements() }.sortedBy { it.profile + it.orderKey }
        rule.onNodeWithContentDescription("배치 편집").performClick()
        rule.onNodeWithText("배치 편집 중: 넓은 화면 배치").assertIsDisplayed()
        rule.onAllNodesWithText("숨기기").onFirst().performClick()
        // 편집 중 접기(창이 좁아짐)
        RuntimeEnvironment.setQualifiers("w360dp-h780dp")
        rule.activityRule.scenario.recreate()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("배치 편집 중: 넓은 화면 배치").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("현재 창은 좁은 화면입니다", substring = true).assertIsDisplayed()
        rule.onNodeWithText("취소").performClick()
        rule.waitForIdle()
        val after = runBlocking { app.container.db.dashboardDao().allPlacements() }.sortedBy { it.profile + it.orderKey }
        assertEquals(before, after)
        assertTrue(after.isNotEmpty())
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun dragHandleReordersDraftAndSaveKeepsIt() {
        val before = runBlocking { app.container.db.dashboardDao().placements("narrow") }.sortedBy { it.orderKey }.map { it.cardId }
        rule.onNodeWithContentDescription("배치 편집").performClick()
        rule.waitForIdle()
        // 첫 카드 손잡이를 아래로 끌어 다음 카드 위에 놓는다
        rule.onAllNodesWithContentDescription("끌어서 순서 바꾸기").onFirst().performTouchInput {
            down(center)
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, 30f)) }
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithText("저장").performClick()
        rule.waitForIdle()
        rule.waitUntil(5_000) {
            runBlocking { app.container.db.dashboardDao().placements("narrow") }.sortedBy { it.orderKey }.map { it.cardId } != before
        }
        val after = runBlocking { app.container.db.dashboardDao().placements("narrow") }.sortedBy { it.orderKey }.map { it.cardId }
        assertEquals(before.toSet(), after.toSet())
        assertTrue(after.indexOf(before[0]) > 0) // 첫 카드가 뒤로 이동
        // 넓은 화면 배치는 그대로
        assertEquals(6, runBlocking { app.container.db.dashboardDao().placements("wide") }.size)
    }

    @Test @Config(qualifiers = "w1280dp-h800dp")
    fun wideWindowUsesRailAndRenders() {
        rule.onNodeWithText("빠른 메모").assertIsDisplayed()
        rule.onNodeWithText("집중 타이머").assertIsDisplayed()
        rule.onNodeWithText("설정").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w600dp-h900dp")
    fun midWidthRenders() {
        rule.onNodeWithText("빠른 메모").assertIsDisplayed()
    }
}
