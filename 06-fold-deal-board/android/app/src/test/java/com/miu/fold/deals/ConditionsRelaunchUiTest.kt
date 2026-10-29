package com.miu.fold.deals

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.miu.fold.deals.listings.Category
import com.miu.fold.deals.listings.ListingDraft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 조건을 고른 뒤 액티비티를 완전히 닫고(ViewModel·SavedState 폐기) 새로 실행해도 조건이 유지되는지.
 * 실제 프로세스 종료는 Robolectric에서 재현할 수 없으므로 디스크 왕복은 BoardConditionsStoreTest가 맡는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp")
class ConditionsRelaunchUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<DealApp>()

    private fun waitText(text: String) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun conditionsAreRestoredAfterRelaunch() {
        val d = ListingDraft(modelRaw = "Galaxy Z Fold6", ram = "12GB", storage = "512GB", category = Category.PHONE, published = "2026-10-01")
        runBlocking { app.repository.create(d.toListing("L1", null, app.repository.now()), null) }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("▼ 조건")
            rule.onNodeWithText("▼ 조건").performClick()
            rule.onNode(hasText("512GB") and hasClickAction()).performScrollTo().performClick()
            rule.onAllNodes(hasText("일주일") and hasClickAction())[0].performScrollTo().performClick()
            rule.onNode(hasText("판매 중만") and hasClickAction()).performClick()
            waitText("조건 충족 0개")
            // 저장이 디스크에 반영될 때까지 기다린다.
            rule.waitUntil(10_000) { runBlocking { app.boardConditions.load()?.let { it.spec.publishedWithinDays == 7 && it.onSaleOnly } == true } }
        }

        // 새로 실행: 저장된 조건 2개(용량·등록일)와 판매 중만이 복원된다.
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("▼ 조건 (2)")
            waitText("조건 충족 0개")
            rule.onNode(hasText("판매 중만") and hasClickAction()).assertIsSelected()
            rule.onNodeWithText("▼ 조건 (2)").performClick()
            rule.onNode(hasText("✓ 512GB") and hasClickAction()).performScrollTo()
            check(rule.onAllNodes(hasText("✓ 일주일") and hasClickAction()).fetchSemanticsNodes().size == 1)
            // 조건 초기화도 저장되어 다음 실행에 반영된다.
            rule.onNodeWithText("조건 초기화").performScrollTo().performClick()
            rule.waitUntil(10_000) { runBlocking { app.boardConditions.load()?.spec?.activeCount == 0 } }
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("▼ 조건")
            check(rule.onAllNodesWithText("▼ 조건 (2)").fetchSemanticsNodes().isEmpty())
        }
    }
}
