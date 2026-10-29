package com.miu.fold.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miu.fold.dashboard.notes.NoteRules
import com.miu.fold.dashboard.settings.SettingsViewModel
import com.miu.fold.dashboard.ui.AppRoot
import com.miu.fold.dashboard.ui.appViewModel
import com.miu.fold.dashboard.ui.theme.FoldDeskTheme

class MainActivity : ComponentActivity() {
    private val intake: IntakeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 재생성 시 같은 공유 인텐트를 다시 처리하지 않는다.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settingsVm = appViewModel { c, _ -> SettingsViewModel(c) }
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            FoldDeskTheme(settings.theme) {
                AppRoot(intake)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** text/plain 공유만 받는다. 내용은 데이터로만 취급하고, 사용자가 미리보기에서 저장해야 메모가 된다. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/plain") != true) return
        val raw = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: return
        if (raw.isBlank()) return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(100)
        val truncated = raw.length > NoteRules.SHARED_TEXT_MAX
        intake.offer(SharedText(raw.take(NoteRules.SHARED_TEXT_MAX), subject, truncated))
        // 처리한 인텐트를 비워 다시 들어와도 중복 저장 안내가 뜨지 않게 한다.
        setIntent(Intent(this, MainActivity::class.java))
    }
}
