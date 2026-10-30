package com.miu.videograb.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.videograb.data.DownloadJob
import com.miu.videograb.data.JobRepository
import com.miu.videograb.data.Settings
import com.miu.videograb.data.SettingsStore
import com.miu.videograb.engine.QualityPreset
import com.miu.videograb.engine.VideoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 엔진 업데이트·버전 확인. 테스트에서는 가짜로 바꾼다. */
interface EngineMaintenance {
    suspend fun update(): String
    suspend fun version(): String?
}

class MainViewModel(
    private val repo: JobRepository,
    private val settingsStore: SettingsStore,
    private val engine: VideoEngine,
    private val maintenance: EngineMaintenance,
    private val schedule: (wifiOnly: Boolean) -> Unit,
) : ViewModel() {

    val jobs: StateFlow<List<DownloadJob>?> = repo.jobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _engineStatus = MutableStateFlow<String?>(null)
    val engineStatus: StateFlow<String?> = _engineStatus
    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    /** 입력란·클립보드 텍스트의 링크를 모두 대기열에 넣는다. 성공하면 true(입력란 비우기용). */
    fun enqueue(text: String, onAdded: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val s = settingsStore.current()
                val r = repo.enqueueFromText(text, s.preset)
                if (r.addedCount > 0) {
                    schedule(s.wifiOnly)
                    onAdded()
                }
                messages.send(
                    when {
                        r.invalidText -> "영상 링크를 찾지 못했어요. http로 시작하는 주소를 붙여넣어 주세요."
                        r.addedCount == 0 -> "이미 받고 있는 링크예요."
                        else -> "${r.addedCount}개 대기열에 추가 · ${s.preset.label}" +
                            if (r.duplicates.isNotEmpty()) " (중복 ${r.duplicates.size}개 제외)" else ""
                    },
                )
            } catch (e: Exception) {
                messages.send("추가하지 못했어요: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    fun cancel(job: DownloadJob) = launchSafely {
        if (repo.cancel(job.id)) engine.cancel(job.id)
    }

    fun retry(job: DownloadJob) = launchSafely {
        if (repo.retry(job.id)) schedule(settingsStore.current().wifiOnly)
    }

    fun remove(job: DownloadJob) = launchSafely {
        if (!repo.delete(job.id)) messages.send("받는 중인 항목은 먼저 취소해 주세요.")
    }

    fun clearFinished() = launchSafely {
        val n = repo.clearFinished()
        messages.send(if (n > 0) "완료·취소 항목 ${n}개를 목록에서 지웠어요. 저장된 파일은 그대로예요." else "지울 항목이 없어요.")
    }

    fun setPreset(p: QualityPreset) = launchSafely { settingsStore.setPreset(p) }
    fun setWifiOnly(v: Boolean) = launchSafely { settingsStore.setWifiOnly(v) }
    fun setAutoUpdateEngine(v: Boolean) = launchSafely { settingsStore.setAutoUpdateEngine(v) }

    fun loadEngineVersion() = viewModelScope.launch(Dispatchers.IO) {
        _engineStatus.value = runCatching { maintenance.version() }.getOrNull()?.let { "yt-dlp $it" } ?: "버전 확인 전"
    }

    fun updateEngine() {
        if (_updating.value) return
        _updating.value = true
        viewModelScope.launch {
            try {
                _engineStatus.value = maintenance.update()
            } catch (e: Exception) {
                _engineStatus.value = "업데이트 실패: ${e.message ?: e::class.simpleName}"
            } finally {
                _updating.value = false
            }
        }
    }

    private fun launchSafely(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { messages.send("처리하지 못했어요: ${e.message ?: e::class.simpleName}") }
    }
}
