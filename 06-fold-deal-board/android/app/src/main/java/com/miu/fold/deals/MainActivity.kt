package com.miu.fold.deals

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.miu.fold.deals.ui.AppViewModel
import com.miu.fold.deals.ui.DealRoot
import com.miu.fold.deals.ui.DealTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel(createSavedStateHandle(), (application as DealApp).repository) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 재생성 시 같은 공유 Intent를 다시 처리하지 않는다.
        if (savedInstanceState == null) handleShare(intent)
        setContent { DealTheme { DealRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(MAX_SHARED_CHARS) ?: return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(500)
        vm.onSharedText(listOfNotNull(subject, text).joinToString("\n"))
        intent.action = null
    }

    companion object {
        private const val MAX_SHARED_CHARS = 20_000
    }
}
