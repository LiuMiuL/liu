package com.miu.lastcontact.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.lastcontact.AppContainer
import com.miu.lastcontact.backup.BackupCodec
import com.miu.lastcontact.backup.BackupFile
import com.miu.lastcontact.backup.DecodeResult
import com.miu.lastcontact.backup.ImportMode
import com.miu.lastcontact.capture.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _pending = MutableStateFlow<BackupFile?>(null)
    val pending = _pending.asStateFlow()

    fun consumeMessage() { _message.value = null }
    fun setMyNames(v: String) = viewModelScope.launch { c.settings.update { it.copy(myKakaoNames = v) } }
    fun markNotificationAsked() = viewModelScope.launch { c.settings.update { it.copy(notificationAsked = true) } }

    fun setReminder(context: Context, on: Boolean, hour: Int) = viewModelScope.launch {
        c.settings.update { it.copy(reminderOn = on, reminderHour = hour) }
        if (on) Reminders.schedule(context.applicationContext, hour) else if (!settings.value.callSmsOn) Reminders.cancel(context.applicationContext)
    }

    fun export(open: () -> OutputStream?) = viewModelScope.launch {
        val r = runCatching {
            val text = c.backup.export(c.settings.current())
            withContext(Dispatchers.IO) { (open() ?: error("파일을 열 수 없습니다.")).use { it.write(text.toByteArray()) } }
        }
        _message.value = r.fold({ "백업을 저장했습니다. 일반 텍스트(JSON)라 이름·연락 날짜가 그대로 들어 있습니다." }, { "백업 실패: ${it.message}" })
    }

    fun readImport(open: () -> InputStream?) = viewModelScope.launch {
        val text = runCatching {
            withContext(Dispatchers.IO) {
                (open() ?: error("파일을 열 수 없습니다.")).use { input ->
                    val out = ByteArrayOutputStream(); val buf = ByteArray(65536)
                    while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > BackupCodec.MAX_BYTES) error("백업 파일이 너무 큽니다.") }
                    out.toString(Charsets.UTF_8.name())
                }
            }
        }.getOrElse { _message.value = "가져오기 실패: ${it.message}"; return@launch }
        when (val d = BackupCodec.decode(text)) {
            is DecodeResult.Error -> _message.value = "가져오기 실패: ${d.message}"
            is DecodeResult.Ok -> _pending.value = d.file
        }
    }

    fun cancelImport() { _pending.value = null }

    fun confirmImport(mode: ImportMode) = viewModelScope.launch {
        val f = _pending.value ?: return@launch
        _pending.value = null
        val r = runCatching { c.backup.import(f, mode, c.settings.current()) }
        r.onSuccess { (counts, pre) ->
            if (mode == ImportMode.REPLACE) f.settings?.let { s -> c.settings.update { s.copy(notificationAsked = it.notificationAsked) } }
            _message.value = "복원했습니다: 사람 ${counts["people"]}명, 연락 기록 ${counts["events"]}건" + (pre?.let { "\n교체 전 상태는 앱 내부(${it.name})에 보관했습니다." } ?: "")
        }.onFailure { _message.value = "복원 실패, 기존 자료는 그대로입니다: ${it.message}" }
    }

    fun deleteAll() = viewModelScope.launch {
        runCatching { c.backup.deleteAll() }
            .onSuccess { _message.value = "모든 사람과 연락 기록을 지웠습니다." }
            .onFailure { _message.value = "지우지 못했습니다: ${it.message}" }
    }
}
