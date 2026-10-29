package com.miu.fold.dashboard.backup

import com.miu.fold.dashboard.data.DashboardCardEntity
import com.miu.fold.dashboard.data.FocusRecordEntity
import com.miu.fold.dashboard.data.LayoutPlacementEntity
import com.miu.fold.dashboard.data.LocalEventEntity
import com.miu.fold.dashboard.data.NoteEntity
import com.miu.fold.dashboard.data.ShortcutEntity
import com.miu.fold.dashboard.data.TodoEntity
import com.miu.fold.dashboard.settings.AppSettings
import com.miu.fold.dashboard.shortcuts.ShortcutType
import com.miu.fold.dashboard.shortcuts.UrlCheck
import com.miu.fold.dashboard.shortcuts.UrlValidator
import com.miu.fold.dashboard.shortcuts.PackageNameRule
import com.miu.fold.dashboard.shortcuts.InternalTarget
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class BackupHeader(
    val appId: String,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val recordCounts: Map<String, Int>,
)

/**
 * 사용자 실행 JSON 백업. 토큰·API 키·인증정보는 이 앱에 저장하지 않으므로 포함되지 않는다.
 * 실행 중 타이머 세션(부팅 기준 monotonic 값)은 다른 기기·부팅에서 의미가 없어 제외하고, 집중 기록만 포함한다.
 */
@Serializable
data class BackupFile(
    val header: BackupHeader,
    val cards: List<DashboardCardEntity> = emptyList(),
    val placements: List<LayoutPlacementEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val todos: List<TodoEntity> = emptyList(),
    val events: List<LocalEventEntity> = emptyList(),
    val shortcuts: List<ShortcutEntity> = emptyList(),
    val focusRecords: List<FocusRecordEntity> = emptyList(),
    val settings: AppSettings? = null,
)

sealed interface DecodeResult {
    data class Ok(val file: BackupFile, val warnings: List<String>) : DecodeResult
    data class Error(val message: String) : DecodeResult
}

object BackupCodec {
    const val APP_ID = "com.miu.fold.dashboard"
    const val SCHEMA_VERSION = 1
    const val MAX_BYTES = 10 * 1024 * 1024

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun counts(f: BackupFile) = mapOf(
        "cards" to f.cards.size, "placements" to f.placements.size, "notes" to f.notes.size,
        "todos" to f.todos.size, "events" to f.events.size, "shortcuts" to f.shortcuts.size,
        "focusRecords" to f.focusRecords.size,
    )

    fun encode(f: BackupFile): String = json.encodeToString(BackupFile.serializer(), f)

    /** 크기·형식·앱 ID·스키마·항목 수·참조·바로가기 목적지를 검사한다. 통과하지 못하면 아무것도 쓰지 않는다. */
    fun decode(text: String): DecodeResult {
        if (text.toByteArray().size > MAX_BYTES) return DecodeResult.Error("백업 파일이 너무 큽니다(최대 10MB).")
        val f = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrElse {
            return DecodeResult.Error("백업 형식이 올바르지 않습니다.")
        }
        val h = f.header
        if (h.appId != APP_ID) return DecodeResult.Error("다른 앱(${h.appId.take(80)})의 백업입니다. 이 앱 백업만 가져올 수 있습니다.")
        if (h.schemaVersion > SCHEMA_VERSION) return DecodeResult.Error("더 새로운 버전 앱에서 만든 백업입니다(스키마 ${h.schemaVersion}). 앱을 업데이트하세요.")
        if (h.schemaVersion < 1) return DecodeResult.Error("알 수 없는 스키마 버전입니다.")
        val warnings = mutableListOf<String>()
        val actual = counts(f)
        if (h.recordCounts.isNotEmpty() && h.recordCounts.filterKeys { it in actual } != actual.filterKeys { it in h.recordCounts }) {
            return DecodeResult.Error("백업 헤더의 항목 수와 실제 내용이 다릅니다. 파일이 손상되었을 수 있습니다.")
        }
        val ids = listOf(f.cards.map { it.id }, f.notes.map { it.id }, f.todos.map { it.id }, f.events.map { it.id }, f.shortcuts.map { it.id })
        if (ids.any { it.size != it.toSet().size }) return DecodeResult.Error("백업에 중복된 ID가 있습니다.")
        val cardIds = f.cards.map { it.id }.toSet()
        if (f.placements.any { it.cardId !in cardIds }) return DecodeResult.Error("배치가 존재하지 않는 카드를 가리킵니다.")
        if (f.placements.map { it.cardId to it.profile }.let { it.size != it.toSet().size }) return DecodeResult.Error("같은 카드·프로필 배치가 중복되어 있습니다.")
        if (f.todos.any { it.dueDate != null && runCatching { LocalDate.parse(it.dueDate) }.isFailure }) return DecodeResult.Error("할 일 날짜 형식이 올바르지 않습니다.")
        if (f.events.any { runCatching { LocalDate.parse(it.localDateStart); LocalDate.parse(it.localDateEnd) }.isFailure }) return DecodeResult.Error("일정 날짜 형식이 올바르지 않습니다.")
        val unknown = f.cards.count { com.miu.fold.dashboard.dashboard.CardType.fromTypeId(it.type) == null }
        if (unknown > 0) warnings += "지원하지 않는 카드 ${unknown}개가 있습니다. 홈에서 '지원하지 않는 카드'로 표시되며 삭제할 수 있습니다."
        val badShortcuts = f.shortcuts.filterNot { validShortcut(it) }
        if (badShortcuts.isNotEmpty()) warnings += "안전하지 않거나 잘못된 바로가기 ${badShortcuts.size}개는 가져오지 않습니다."
        val noteIds = f.notes.map { it.id }.toSet()
        val brokenRefs = f.todos.count { it.sourceNoteId != null && it.sourceNoteId !in noteIds }
        if (brokenRefs > 0) warnings += "원본 메모가 없는 할 일 ${brokenRefs}개는 메모 연결 없이 가져옵니다."
        val cleaned = f.copy(
            shortcuts = f.shortcuts.filter { validShortcut(it) },
            todos = f.todos.map { if (it.sourceNoteId != null && it.sourceNoteId !in noteIds) it.copy(sourceNoteId = null) else it },
        )
        return DecodeResult.Ok(cleaned, warnings)
    }

    fun validShortcut(s: ShortcutEntity): Boolean {
        val type = runCatching { ShortcutType.valueOf(s.type) }.getOrNull() ?: return false
        val fallbackOk = s.fallbackUrl == null || UrlValidator.check(s.fallbackUrl) is UrlCheck.Ok
        return fallbackOk && when (type) {
            ShortcutType.WEB -> UrlValidator.check(s.target) is UrlCheck.Ok
            ShortcutType.APP -> PackageNameRule.isValid(s.target)
            ShortcutType.INTERNAL -> InternalTarget.fromKey(s.target) != null
        }
    }
}
