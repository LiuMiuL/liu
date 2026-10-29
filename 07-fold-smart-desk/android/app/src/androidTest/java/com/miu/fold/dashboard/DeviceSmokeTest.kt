package com.miu.fold.dashboard

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실기기 스모크 테스트. 테스트 전용 앱(com.miu.fold.dashboard.devtest)에서 돌아 실제 앱 데이터와 분리된다.
 * 실행: gradlew connectedDeviceTestAndroidTest (tools/device-test.ps1이 대신 실행)
 */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FoldDeskApp

    @Before fun setUp() {
        // 타이머 시작 시 뜨는 알림 권한 창이 테스트를 막지 않도록 미리 허용한다(테스트 앱에만 적용).
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("빠른 메모").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun launchShowsCoreCards() {
        rule.onNodeWithText("빠른 메모").assertIsDisplayed()
        rule.onNodeWithText("오늘 할 일").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("집중 타이머").performScrollTo().assertIsDisplayed()
    }

    @Test fun quickMemoSurvivesActivityRecreate() {
        val text = "실기기 메모 ${System.currentTimeMillis()}"
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        rule.waitUntil(10_000) { runBlocking { app.container.db.noteDao().all() }.any { it.body == text } }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun timerStartPauseCancel() {
        runBlocking { app.container.timer.cancel() } // 이전 실행이 남긴 세션 정리
        rule.onNodeWithText("집중", substring = true).performScrollTo()
        rule.onAllNodesWithText("집중 ", substring = true).onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("일시정지").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(2_000) // 실제 시간이 흐르는지
        rule.onNodeWithText("일시정지").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("재개").fetchSemanticsNodes().isNotEmpty() }
        val s = runBlocking { app.container.db.timerDao().active() }.single()
        assertEquals("PAUSED", s.state)
        assertTrue("2초 이상 줄어야 함: ${s.remainingMs}", s.remainingMs <= s.plannedDurationMs - 1_500)
        rule.onAllNodesWithText("종료").onFirst().performClick()
        rule.waitUntil(10_000) { runBlocking { app.container.db.timerDao().active() }.isEmpty() }
    }

    @Test fun layoutEditCancelKeepsLayout() {
        val before = runBlocking { app.container.db.dashboardDao().allPlacements() }.sortedBy { it.profile + it.cardId }
        rule.onNodeWithContentDescription("배치 편집").performClick()
        rule.onAllNodesWithText("숨기기").onFirst().performClick()
        rule.onNodeWithText("취소").performClick()
        rule.waitForIdle()
        val after = runBlocking { app.container.db.dashboardDao().allPlacements() }.sortedBy { it.profile + it.cardId }
        assertEquals(before, after)
    }

    @Test fun webShortcutRejectsDangerousUrl() = runBlocking {
        val r = app.container.shortcuts.save(null, com.miu.fold.dashboard.shortcuts.ShortcutType.WEB, "", "javascript:alert(1)", null)
        assertTrue(r is com.miu.fold.dashboard.shortcuts.ShortcutSaveResult.Invalid)
    }
}
