package com.miu.lastcontact

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.miu.lastcontact.data.SavePersonResult
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.Direction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric(JVM)에서 실제 MainActivity를 띄우는 화면 테스트. 실기기 검증을 대체하지 않는다. */
@RunWith(RobolectricTestRunner::class)
class UiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<LastContactApp>()

    private fun waitText(t: String, sub: Boolean = false) =
        rule.waitUntil(20_000) { rule.onAllNodesWithText(t, substring = sub).fetchSemanticsNodes().isNotEmpty() }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun lc_t01_addPersonThenContactedTodayAndUndo() {
        waitText("아직 등록한 사람이 없습니다", sub = true)
        rule.onNodeWithText("사람 추가", useUnmergedTree = true).performClick()
        // 대화상자 안 입력칸에 포커스를 주면 커서 깜빡임 때문에 Robolectric에서 idle이 되지 않아, 포커스 없이 값을 넣는다.
        rule.onNode(hasText("이름") and hasSetTextAction()).performTextReplacement("엄마")
        rule.onNodeWithText("2주").performClick()
        rule.onNodeWithText("저장").performClick()
        // 저장 후 상세 화면으로 이동
        waitText("마지막 연락: 연락 기록 없음")
        rule.onNodeWithText("오늘 연락함").performClick()
        waitText("마지막 연락: 오늘", sub = true)
        val p = runBlocking { app.container.repo.allPeople() }.single()
        assertEquals(14, p.goalDays)
        // 되돌리기
        rule.onNodeWithText("되돌리기").performClick()
        waitText("마지막 연락: 연락 기록 없음")
        // 목록으로
        rule.onNodeWithContentDescription("목록으로").performClick()
        waitText("목표 14일 · 아직 기록 없음", sub = true)
    }

    @Test @Config(qualifiers = "w360dp-h780dp")
    fun lc_t04_sharedKakaoExportFlow() {
        val mom = runBlocking { (app.container.repo.savePerson(null, "엄마", "", "", "가족", 7, "") as SavePersonResult.Saved).person }
        waitText("엄마")
        val text = "엄마 님과 카카오톡 대화\n--------------- 2026년 10월 20일 화요일 ---------------\n[엄마] [오후 1:00] 밥은?\n[김지우] [오후 1:05] 먹었어요\n"
        rule.onNodeWithText("가져오기").performClick()
        // 공유로 들어온 것과 같은 경로(IntakeViewModel → 가져오기 화면)
        val intake = androidx.lifecycle.ViewModelProvider(rule.activity)[IntakeViewModel::class.java]
        rule.runOnIdle { intake.offer(Shared.Text(text)) }
        waitText("가져오기 확인")
        rule.onNodeWithText("1:1 대화로 보고", substring = true).assertIsDisplayed()
        // 엄마는 자동으로 등록된 사람에 연결, 김지우는 1:1에서 방 제목과 달라 '나'로 추정
        rule.onNodeWithText("저장").performClick()
        waitText("가져왔습니다", sub = true)
        val sum = runBlocking { app.container.repo.summaries.first() }
        assertEquals("2026-10-20", sum.single { it.person.id == mom.id }.last!!.day.toString())
        assertTrue(runBlocking { app.container.settings.current() }.myKakaoNames.contains("김지우"))
    }

    @Test @Config(qualifiers = "w900dp-h800dp")
    fun wideShowsListAndDetailSideBySide() {
        runBlocking {
            val p = (app.container.repo.savePerson(null, "민수", "", "", "친구", 30, "") as SavePersonResult.Saved).person
            app.container.repo.record(p.id, app.container.clock.today().minusDays(40), Channel.MANUAL, Direction.UNKNOWN)
        }
        waitText("민수")
        rule.onNodeWithText("왼쪽에서 사람을 고르세요.").assertIsDisplayed()
        rule.onAllNodesWithText("민수").onFirst().performClick()
        waitText("마지막 연락: 40일", sub = true)
        rule.onNodeWithText("⚠ 목표 30일 · 10일 지남").assertIsDisplayed() // 목록과 상세가 동시에 보임
    }
}
