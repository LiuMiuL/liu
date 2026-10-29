package com.miu.fold.dashboard.backup

import androidx.room.withTransaction
import com.miu.fold.dashboard.BuildConfig
import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.dashboard.CardType
import com.miu.fold.dashboard.dashboard.DashboardRepository
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.settings.AppSettings
import java.io.File

enum class ImportMode { ADD_AS_NEW, REPLACE_ALL }

data class ImportSummary(val added: Map<String, Int>, val warnings: List<String>, val preRestoreBackup: File?)

class BackupService(
    private val db: AppDatabase,
    private val dashboard: DashboardRepository,
    private val clock: AppClock,
    private val backupDir: File,
) {
    suspend fun export(settings: AppSettings?): String {
        val f = BackupFile(
            header = BackupHeader(BackupCodec.APP_ID, BackupCodec.SCHEMA_VERSION, BuildConfig.VERSION_NAME, clock.nowMillis(), emptyMap()),
            cards = db.dashboardDao().cards(),
            placements = db.dashboardDao().allPlacements(),
            notes = db.noteDao().all(),
            todos = db.todoDao().all(),
            events = db.eventDao().all(),
            shortcuts = db.shortcutDao().all(),
            focusRecords = db.timerDao().allRecords(),
            settings = settings,
        )
        return BackupCodec.encode(f.copy(header = f.header.copy(recordCounts = BackupCodec.counts(f))))
    }

    /**
     * ADD_AS_NEW(기본): 메모·할 일·일정·바로가기·집중 기록을 새 ID로 추가한다. 기존 자료는 바꾸지 않는다.
     *   카드는 현재 없는 종류(지원하지 않는 종류 포함)만 추가하고, 현재 배치는 유지한다.
     * REPLACE_ALL: 현재 상태를 먼저 내부 파일로 백업한 뒤 전체를 교체한다.
     * 둘 다 하나의 트랜잭션이라 실패하면 기존 DB는 그대로다.
     */
    suspend fun import(file: BackupFile, mode: ImportMode, currentSettings: AppSettings?): ImportSummary {
        var pre: File? = null
        if (mode == ImportMode.REPLACE_ALL) {
            backupDir.mkdirs()
            pre = File(backupDir, "pre-restore-${clock.nowMillis()}.json").also { it.writeText(export(currentSettings)) }
        }
        val added = mutableMapOf<String, Int>()
        db.withTransaction {
            when (mode) {
                ImportMode.REPLACE_ALL -> {
                    // FK 순서를 고려해 자식 테이블부터 지운다(같은 트랜잭션 안).
                    val w = db.openHelper.writableDatabase
                    listOf("layout_placement", "dashboard_card", "todo", "note", "local_event", "shortcut", "focus_record", "recent_item")
                        .forEach { w.execSQL("DELETE FROM $it") }
                    db.dashboardDao().insertCards(file.cards)
                    db.dashboardDao().insertPlacements(file.placements)
                    db.noteDao().insertAll(file.notes)
                    db.todoDao().insertAll(file.todos)
                    db.eventDao().insertAll(file.events)
                    db.shortcutDao().insertAll(file.shortcuts)
                    db.timerDao().insertRecords(file.focusRecords)
                    added.putAll(BackupCodec.counts(file))
                }
                ImportMode.ADD_AS_NEW -> {
                    val noteMap = file.notes.associate { it.id to Ids.newId() }
                    db.noteDao().insertAll(file.notes.map { it.copy(id = noteMap.getValue(it.id)) })
                    val base = db.todoDao().maxOrder()
                    db.todoDao().insertAll(file.todos.mapIndexed { i, t ->
                        t.copy(id = Ids.newId(), sourceNoteId = t.sourceNoteId?.let { noteMap[it] }, orderKey = base + 1 + i)
                    })
                    db.eventDao().insertAll(file.events.map { it.copy(id = Ids.newId()) })
                    val sBase = db.shortcutDao().maxOrder()
                    db.shortcutDao().insertAll(file.shortcuts.mapIndexed { i, s -> s.copy(id = Ids.newId(), orderKey = sBase + 1 + i) })
                    db.timerDao().insertRecords(file.focusRecords)
                    val presentTypes = db.dashboardDao().cards().map { it.type }.toSet()
                    val newCards = file.cards
                        .filter { it.type !in presentTypes || CardType.fromTypeId(it.type) == null }
                        .distinctBy { it.type }
                        .map { it.copy(id = Ids.newId()) }
                    db.dashboardDao().insertCards(newCards)
                    added["notes"] = file.notes.size; added["todos"] = file.todos.size
                    added["events"] = file.events.size; added["shortcuts"] = file.shortcuts.size
                    added["focusRecords"] = file.focusRecords.size; added["cards"] = newCards.size
                }
            }
            dashboard.ensureDefaults()
        }
        return ImportSummary(added, emptyList(), pre)
    }
}
