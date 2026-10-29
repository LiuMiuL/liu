package com.miu.fold.deals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.miu.fold.deals.data.DealDatabase
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실제 MainActivity·Room DB를 사용한 사용자 흐름 테스트(Robolectric, 실기기 아님).
 * 좁은 창(360dp)과 넓은 창(900dp) 두 구성에서 실행한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class AppFlowUiTestBase {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun resetDb() {
        // 이전 테스트가 만든 DB는 Robolectric이 테스트마다 앱 데이터를 초기화하므로 비어 있다.
        check(ApplicationProvider.getApplicationContext<DealApp>().getDatabasePath(DealDatabase.FILE_NAME) != null)
    }

    private fun input(label: String, text: String) {
        rule.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(text)
    }

    protected fun addListing(model: String, ram: String, price: String, includedShipping: Boolean) {
        rule.onNodeWithTag("add-listing").performClick()
        input("모델 원문 (예: 갤럭시 Z 폴드6)", model)
        input("RAM (예: 12GB, 16GB×2)", ram)
        input("저장공간 (예: 512GB, 1TB)", "512GB")
        input("판매가 원문 (예: 70만, 700,000원, 가격 협의)", price)
        if (includedShipping) rule.onNodeWithText("배송비 포함").performScrollTo().performClick()
        rule.onNodeWithText("저장").performClick()
        rule.waitForIdle()
    }

    protected fun waitText(text: String) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    protected fun runFlow(singlePane: Boolean) {
        waitText("아직 저장한 매물이 없어요.")
        rule.onNodeWithText("아직 저장한 매물이 없어요.").assertIsDisplayed()
        rule.onNodeWithText("수동 기록 모드 · 자동 가격 추적 없음 · 직접 기록한 값만 비교합니다").assertIsDisplayed()

        addListing("갤럭시 Z 폴드6", "12GB", "70만", includedShipping = true)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("700,000원").fetchSemanticsNodes().isNotEmpty() }
        if (singlePane) rule.onNode(hasContentDescription("뒤로")).performClick()

        addListing("Galaxy Z Fold6", "16GB", "65만", includedShipping = false)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("미확인 — 배송비 미확인").fetchSemanticsNodes().isNotEmpty() }
        if (singlePane) rule.onNode(hasContentDescription("뒤로")).performClick()

        // 두 매물을 비교에 추가
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("비교에 추가", substring = true)).fetchSemanticsNodes().size == 2 }
        rule.onAllNodes(hasContentDescription("비교에 추가", substring = true))[0].performClick()
        rule.onAllNodes(hasContentDescription("비교에 추가", substring = true))[0].performClick()
        rule.onNodeWithText("비교하기").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("! 사양이 서로 다름 — 같은 조건 비교 아님").assertIsDisplayed()
        rule.onAllNodesWithText("≠ RAM").fetchSemanticsNodes().isNotEmpty().let(::check)

        // 화면 재생성 후에도 비교 선택과 데이터가 유지된다.
        rule.activityRule.scenario.recreate()
        waitText("비교 (2개)")
        rule.onNodeWithText("비교 (2개)").assertIsDisplayed()
    }
}

@Config(qualifiers = "w360dp-h780dp")
class AppFlowNarrowUiTest : AppFlowUiTestBase() {
    @Test fun addTwoListingsCompareAndRecreate() = runFlow(singlePane = true)
}

@Config(qualifiers = "w900dp-h700dp")
class AppFlowWideUiTest : AppFlowUiTestBase() {
    @Test fun addTwoListingsCompareAndRecreate() {
        waitText("왼쪽에서 매물을 선택하거나 2~4개를 골라 비교하세요.")
        rule.onNodeWithText("왼쪽에서 매물을 선택하거나 2~4개를 골라 비교하세요.").assertIsDisplayed()
        runFlow(singlePane = false)
    }
}
