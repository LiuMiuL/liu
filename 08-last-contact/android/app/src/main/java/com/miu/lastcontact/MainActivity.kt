package com.miu.lastcontact

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.miu.lastcontact.ui.AppRoot
import com.miu.lastcontact.ui.LastContactTheme

class MainActivity : ComponentActivity() {
    private val intake: IntakeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { LastContactTheme { AppRoot(intake) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** 카톡 '대화 내보내기' 공유: 파일(EXTRA_STREAM) 또는 텍스트(EXTRA_TEXT). 내용은 가져오기 화면에서 사용자가 확인한 뒤에만 반영된다. */
    private fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        when {
            uri != null -> intake.offer(Shared.File(uri))
            !intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank() -> intake.offer(Shared.Text(intent.getStringExtra(Intent.EXTRA_TEXT)!!))
        }
        setIntent(Intent(this, MainActivity::class.java))
    }
}
