package com.miu.videograb

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miu.videograb.engine.EngineUpdateOutcome
import com.miu.videograb.ui.EngineMaintenance
import com.miu.videograb.ui.MainScreen
import com.miu.videograb.ui.MainViewModel
import com.miu.videograb.ui.VideoGrabTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as VideoGrabApp
                MainViewModel(
                    app.repository, app.settings, app.engine,
                    object : EngineMaintenance {
                        override suspend fun update(): String {
                            val outcome = app.engine.update()
                            app.settings.setLastEngineUpdateAt(System.currentTimeMillis())
                            val v = app.engine.version() ?: "버전 확인 불가"
                            return if (outcome == EngineUpdateOutcome.UPDATED) "업데이트 완료: yt-dlp $v" else "이미 최신 버전이에요 (yt-dlp $v)"
                        }
                        override suspend fun version() = app.engine.version()
                    },
                    app.scheduler::enqueue,
                )
            }
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VideoGrabTheme { MainScreen(vm, onFirstDownload = ::askNotificationPermissionOnce) } }
    }

    /** 다운로드를 처음 누를 때만 알림 권한을 묻는다. 거절해도 다운로드와 앱 안 목록은 그대로 동작한다. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getBoolean("askedNotification", false)) return
        prefs.edit().putBoolean("askedNotification", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
