package com.miu.lastcontact

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
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
 * 실기기 스모크 테스트. 테스트 전용 앱(com.miu.lastcontact.devtest)에서 돌아 실제 앱 자료와 분리된다.
 * PC(Robolectric)에서 검증하지 못한 '앱 안에서 사람 추가 대화상자에 직접 입력' 흐름을 여기서 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LastContactApp

    @Before fun clean() {
        runBlocking { app.container.backup.deleteAll() }
        rule.waitUntil(20_000) { rule.onAllNodes(hasText("사람 추가", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitText(t: String) = rule.waitUntil(20_000) { rule.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun addPersonByTypingThenContactedToday() {
        rule.onNodeWithText("사람 추가", useUnmergedTree = true).performClick()
        rule.onNode(hasText("이름") and hasSetTextAction()).performTextInput("실기기테스트")
        rule.onNodeWithText("1주").performScrollTo().performClick()
        rule.onNodeWithText("저장").performClick()
        waitText("마지막 연락: 연락 기록 없음")
        rule.onNodeWithText("오늘 연락함").performClick()
        waitText("마지막 연락: 오늘")
        val p = runBlocking { app.container.repo.allPeople() }.single()
        assertEquals(7, p.goalDays)
        assertEquals(1, runBlocking { app.container.db.eventDao().all() }.size)
    }

    @Test fun importSharedKakaoText() {
        val text = "엄마 님과 카카오톡 대화\n--------------- 2026년 10월 20일 화요일 ---------------\n[엄마] [오후 1:00] 밥은?\n[나] [오후 1:05] 먹었어요\n"
        rule.onNodeWithText("가져오기").performClick()
        val intake = androidx.lifecycle.ViewModelProvider(rule.activity)[IntakeViewModel::class.java]
        rule.runOnIdle { intake.offer(Shared.Text(text)) }
        waitText("가져오기 확인")
        rule.onNodeWithText("저장").performClick()
        waitText("가져왔습니다")
        val people = runBlocking { app.container.repo.allPeople() }
        assertTrue(people.any { it.name == "엄마" })
    }

    @Test fun settingsAndPermissionsScreensOpen() {
        rule.onNodeWithText("설정").performClick()
        waitText("매일 연락 알림")
        rule.onNodeWithText("가져오기").performClick()
        waitText("카톡 알림 자동 기록")
        rule.onNodeWithText("통화·문자 기록").performScrollTo().assertIsDisplayed()
    }

    @Test fun backButtonFromDetailReturnsToList() {
        runBlocking { app.container.repo.savePerson(null, "뒤로가기", "", "", "", null, "") }
        waitText("뒤로가기")
        rule.onNodeWithText("뒤로가기").performClick()
        waitText("마지막 연락")
        // 좁은 화면이면 '목록으로' 버튼, 넓은 화면(펼친 폴드)이면 2패널이라 버튼이 없다
        runCatching { rule.onNodeWithContentDescription("목록으로").performClick() }
        waitText("사람 추가")
    }
}
