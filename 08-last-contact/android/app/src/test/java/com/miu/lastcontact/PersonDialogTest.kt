package com.miu.lastcontact

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.miu.lastcontact.people.PeopleViewModel
import com.miu.lastcontact.people.PersonEditDialog
import com.miu.lastcontact.ui.LastContactTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 사람 추가 대화상자 단독 검증.
 * 참고: Robolectric에서 화면을 360×780dp로 흉내 내면 입력칸이 든 대화상자가 idle 상태가 되지 않아 테스트가 멈춘다
 * (입력칸 없는 대화상자·기본 크기에서는 정상). 앱 안 흐름은 실기기 테스트(DeviceSmokeTest)에서 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class PersonDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun addPersonWithGoalAndPhone() {
        val app = ApplicationProvider.getApplicationContext<LastContactApp>()
        val vm = PeopleViewModel(app.container, SavedStateHandle())
        var savedId: String? = "unset"
        rule.setContent { LastContactTheme { PersonEditDialog(vm, null, onDone = { savedId = it }) } }
        // 빈 이름으로 저장 → 오류 안내
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("이름을 입력하세요", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("이름") and hasSetTextAction()).performTextReplacement("엄마")
        rule.onNode(hasText("전화번호", substring = true) and hasSetTextAction()).performTextReplacement("+82 10-1234-5678")
        rule.onNodeWithText("2주").performScrollTo().performClick()
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { savedId != "unset" && savedId != null }
        val p = runBlocking { app.container.repo.allPeople() }.single()
        assertEquals("엄마", p.name)
        assertEquals(14, p.goalDays)
        assertEquals("01012345678", p.phones)
        assertTrue(savedId == p.id)
    }
}
