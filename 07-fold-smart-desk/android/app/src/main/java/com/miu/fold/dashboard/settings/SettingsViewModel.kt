package com.miu.fold.dashboard.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.backup.BackupCodec
import com.miu.fold.dashboard.backup.BackupFile
import com.miu.fold.dashboard.backup.DecodeResult
import com.miu.fold.dashboard.backup.ImportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class PendingImport(val file: BackupFile, val warnings: List<String>)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setHideRecent(v: Boolean) = viewModelScope.launch { c.settings.setHideRecent(v) }
    fun setFocus(v: Int) = viewModelScope.launch { c.settings.setFocusMinutes(v) }
    fun setBreak(v: Int) = viewModelScope.launch { c.settings.setBreakMinutes(v) }
    fun markNotificationAsked() = viewModelScope.launch { c.settings.setNotificationAsked() }
    fun clearRecent() = viewModelScope.launch { c.recent.clear(); _message.value = "최근 항목 기록을 지웠습니다. 메모·할 일은 그대로입니다." }

    fun export(open: () -> OutputStream?) = viewModelScope.launch {
        val result = runCatching {
            val text = c.backup.export(c.settings.settings.first())
            withContext(Dispatchers.IO) {
                (open() ?: error("파일을 열 수 없습니다.")).use { it.write(text.toByteArray()) }
            }
        }
        _message.value = result.fold(
            { "백업을 저장했습니다. 일반 JSON 평문이므로 메모 등 개인 내용이 그대로 들어 있습니다." },
            { "백업 저장 실패: ${it.message}" },
        )
    }

    /** 복원 1단계: 읽고 검증만 한다. 사용자가 미리보기를 확인한 뒤에만 [confirmImport]로 저장된다. */
    fun readImport(open: () -> InputStream?) = viewModelScope.launch {
        val text = runCatching {
            withContext(Dispatchers.IO) {
                (open() ?: error("파일을 열 수 없습니다.")).use { input ->
                    // readNBytes는 API 33+라서 직접 상한까지만 읽는다.
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > BackupCodec.MAX_BYTES) error("백업 파일이 너무 큽니다(최대 10MB).")
                    }
                    out.toString(Charsets.UTF_8.name())
                }
            }
        }.getOrElse { _message.value = "가져오기 실패: ${it.message}"; return@launch }
        when (val r = BackupCodec.decode(text)) {
            is DecodeResult.Error -> _message.value = "가져오기 실패: ${r.message}"
            is DecodeResult.Ok -> _pending.value = PendingImport(r.file, r.warnings)
        }
    }

    fun cancelImport() { _pending.value = null }

    fun confirmImport(mode: ImportMode) = viewModelScope.launch {
        val p = _pending.value ?: return@launch
        _pending.value = null
        val result = runCatching { c.backup.import(p.file, mode, c.settings.settings.first()) }
        result.onSuccess { s ->
            if (mode == ImportMode.REPLACE_ALL) p.file.settings?.let { c.settings.apply(it) }
            val counts = s.added.entries.filter { it.value > 0 }.joinToString { "${label(it.key)} ${it.value}" }
            _message.value = buildString {
                append(if (mode == ImportMode.ADD_AS_NEW) "새 데이터로 가져왔습니다" else "전체 교체 복원을 마쳤습니다")
                if (counts.isNotEmpty()) append(": $counts")
                s.preRestoreBackup?.let { append("\n교체 전 상태는 앱 내부 파일(${it.name})로 보관했습니다.") }
            }
        }.onFailure { _message.value = "가져오기 실패, 기존 데이터는 바뀌지 않았습니다: ${it.message}" }
    }

    private fun label(k: String) = when (k) {
        "notes" -> "메모"; "todos" -> "할 일"; "events" -> "일정"; "shortcuts" -> "바로가기"
        "focusRecords" -> "집중 기록"; "cards" -> "카드"; "placements" -> "배치"; else -> k
    }
}
