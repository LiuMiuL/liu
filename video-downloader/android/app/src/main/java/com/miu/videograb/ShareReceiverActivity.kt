package com.miu.videograb

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 다른 앱의 "공유 → VideoGrab"을 받아 바로 대기열에 넣고 닫힌다(화면 없음).
 * 공유 텍스트는 링크를 찾는 데이터로만 쓴다.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면 회전 등으로 재생성될 때 같은 공유를 두 번 넣지 않는다.
        if (savedInstanceState != null) { finish(); return }
        val text = if (intent?.action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
                .joinToString("\n").take(MAX_CHARS)
        } else null
        val app = application as VideoGrabApp
        lifecycleScope.launch {
            val message = try {
                val settings = app.settings.current()
                val result = app.repository.enqueueFromText(text, settings.preset)
                if (result.addedCount > 0) app.scheduler.enqueue(settings.wifiOnly)
                when {
                    result.invalidText -> "공유된 내용에서 영상 링크를 찾지 못했어요."
                    result.addedCount == 0 -> "이미 받고 있는 링크예요."
                    else -> "다운로드 시작 ${result.addedCount}개 · ${settings.preset.label}" +
                        (if (result.duplicates.isNotEmpty()) " (중복 ${result.duplicates.size}개 제외)" else "") +
                        (if (settings.wifiOnly) " · Wi-Fi 연결 시" else "")
                }
            } catch (e: Exception) {
                "대기열에 넣지 못했어요: ${e.message ?: e::class.simpleName}"
            }
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    companion object {
        private const val MAX_CHARS = 20_000
    }
}
