package com.miu.lastcontact

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.miu.lastcontact.data.SavePersonResult
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.Direction
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 문서용 미리보기(build/screenshots). 이름·날짜는 모두 '예시'이며 JVM 렌더링이라 실기기와 다를 수 있다. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun sample() = runBlocking {
        val c = ApplicationProvider.getApplicationContext<LastContactApp>().container
        val today = c.clock.today()
        suspend fun add(name: String, group: String, goal: Int?, daysAgo: Long?, ch: Channel) {
            val p = (c.repo.savePerson(null, name, "", "", group, goal, "") as SavePersonResult.Saved).person
            if (daysAgo != null) c.repo.record(p.id, today.minusDays(daysAgo), ch, Direction.BOTH)
        }
        add("엄마(예시)", "가족", 7, 12, Channel.KAKAO_EXPORT)
        add("아빠(예시)", "가족", 14, 3, Channel.CALL)
        add("민수(예시)", "친구", 30, 45, Channel.KAKAO_NOTIFICATION)
        add("지우(예시)", "친구", 60, 9, Channel.SMS)
        add("할머니(예시)", "가족", 14, null, Channel.MANUAL)
        add("동창 단톡 진호(예시)", "", null, 120, Channel.KAKAO_EXPORT)
    }

    private fun capture(name: String, wait: String) {
        rule.waitUntil(20_000) { rule.onAllNodesWithText(wait, substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi") fun list() = capture("people_360dp", "할머니(예시)")

    @Test @Config(qualifiers = "w840dp-h900dp-xhdpi") fun wide() {
        rule.waitUntil(20_000) { rule.onAllNodesWithText("민수(예시)").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("민수(예시)").onFirst().performClick()
        capture("people_detail_840dp", "경로별 마지막 연락")
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi") fun importTab() {
        rule.waitUntil(20_000) { rule.onAllNodesWithText("가져오기").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("가져오기").onFirst().performClick()
        capture("import_360dp", "카톡 대화 내보내기 가져오기")
    }
}
