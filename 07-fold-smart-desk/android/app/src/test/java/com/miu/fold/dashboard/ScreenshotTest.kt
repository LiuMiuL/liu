package com.miu.fold.dashboard

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 문서용 미리보기 캡처(build/screenshots). JVM 렌더링이라 실기기 화면과 다를 수 있으며 검증을 대체하지 않는다.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        runCatching { rule.waitUntil(20_000) { rule.onAllNodesWithText("빠른 메모").fetchSemanticsNodes().isNotEmpty() } }
            .onFailure { println(rule.onRoot(useUnmergedTree = true).printToString()) ; throw it }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi") fun folded() = capture("home_folded_360dp")
    @Test @Config(qualifiers = "w840dp-h900dp-xhdpi") fun unfolded() = capture("home_unfolded_840dp")
    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi") fun large() = capture("home_large_1280dp")
}
