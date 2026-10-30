package com.miu.lastcontact.backup

import androidx.room.withTransaction
import com.miu.lastcontact.BuildConfig
import com.miu.lastcontact.core.AppClock
import com.miu.lastcontact.core.Ids
import com.miu.lastcontact.data.AppDatabase
import com.miu.lastcontact.data.ContactEventEntity
import com.miu.lastcontact.data.IgnoredNameEntity
import com.miu.lastcontact.data.PersonEntity
import com.miu.lastcontact.data.UnmatchedSightingEntity
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.ContactRules
import com.miu.lastcontact.settings.AppSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

@Serializable
data class BackupHeader(val appId: String, val schemaVersion: Int, val appVersion: String, val exportedAt: Long, val recordCounts: Map<String, Int>)

/** 사람·연락 날짜·미확인 이름·설정. 대화·문자 내용은 애초에 저장하지 않으므로 들어가지 않는다. */
@Serializable
data class BackupFile(
    val header: BackupHeader,
    val people: List<PersonEntity> = emptyList(),
    val events: List<ContactEventEntity> = emptyList(),
    val unmatched: List<UnmatchedSightingEntity> = emptyList(),
    val ignored: List<IgnoredNameEntity> = emptyList(),
    val settings: AppSettings? = null,
)

sealed interface DecodeResult {
    data class Ok(val file: BackupFile) : DecodeResult
    data class Error(val message: String) : DecodeResult
}

enum class ImportMode { ADD, REPLACE }

object BackupCodec {
    const val APP_ID = "com.miu.lastcontact"
    const val SCHEMA = 1
    const val MAX_BYTES = 20 * 1024 * 1024
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun counts(f: BackupFile) = mapOf("people" to f.people.size, "events" to f.events.size, "unmatched" to f.unmatched.size, "ignored" to f.ignored.size)

    fun encode(f: BackupFile): String = json.encodeToString(BackupFile.serializer(), f)

    fun decode(text: String): DecodeResult {
        if (text.length > MAX_BYTES) return DecodeResult.Error("백업 파일이 너무 큽니다(20MB 초과).")
        val f = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrElse { return DecodeResult.Error("백업 형식이 올바르지 않습니다.") }
        if (f.header.appId != APP_ID) return DecodeResult.Error("다른 앱(${f.header.appId.take(60)})의 백업입니다.")
        if (f.header.schemaVersion !in 1..SCHEMA) return DecodeResult.Error("지원하지 않는 백업 버전(${f.header.schemaVersion})입니다. 앱을 업데이트하세요.")
        val actual = counts(f)
        if (f.header.recordCounts.isNotEmpty() && f.header.recordCounts.filterKeys { it in actual } != actual.filterKeys { it in f.header.recordCounts }) {
            return DecodeResult.Error("백업 헤더의 항목 수와 내용이 다릅니다. 파일이 손상되었을 수 있습니다.")
        }
        if (f.people.map { it.id }.toSet().size != f.people.size) return DecodeResult.Error("백업에 중복된 사람 ID가 있습니다.")
        val ids = f.people.map { it.id }.toSet()
        if (f.events.any { it.personId !in ids }) return DecodeResult.Error("존재하지 않는 사람을 가리키는 기록이 있습니다.")
        if (f.events.any { runCatching { LocalDate.parse(it.day) }.isFailure || Channel.of(it.channel) == null }) return DecodeResult.Error("기록의 날짜·경로 형식이 올바르지 않습니다.")
        return DecodeResult.Ok(f)
    }
}

class BackupService(private val db: AppDatabase, private val clock: AppClock, private val dir: File) {
    suspend fun export(settings: AppSettings?): String {
        val f = BackupFile(
            BackupHeader(BackupCodec.APP_ID, BackupCodec.SCHEMA, BuildConfig.VERSION_NAME, clock.nowMillis(), emptyMap()),
            db.personDao().all(), db.eventDao().all(), db.unmatchedDao().all(), db.unmatchedDao().ignored(), settings,
        )
        return BackupCodec.encode(f.copy(header = f.header.copy(recordCounts = BackupCodec.counts(f))))
    }

    /**
     * ADD: 사람을 새 ID로 추가하고 기록도 새 ID로 옮긴다(기존 자료 유지). 같은 사람·날·경로는 중복 키로 1건만 남는다.
     * REPLACE: 현재 상태를 앱 내부 파일로 먼저 백업한 뒤 전체 교체. 어느 쪽이든 한 트랜잭션이라 실패하면 그대로다.
     */
    suspend fun import(f: BackupFile, mode: ImportMode, current: AppSettings?): Pair<Map<String, Int>, File?> {
        var pre: File? = null
        if (mode == ImportMode.REPLACE) {
            dir.mkdirs()
            pre = File(dir, "pre-restore-${clock.nowMillis()}.json").also { it.writeText(export(current)) }
        }
        db.withTransaction {
            when (mode) {
                ImportMode.REPLACE -> {
                    val w = db.openHelper.writableDatabase
                    listOf("contact_event", "person", "unmatched_sighting", "ignored_name").forEach { w.execSQL("DELETE FROM $it") }
                    db.personDao().insertAll(f.people)
                    db.eventDao().insertAllIgnore(f.events)
                }
                ImportMode.ADD -> {
                    val map = f.people.associate { it.id to Ids.newId() }
                    db.personDao().insertAll(f.people.map { it.copy(id = map.getValue(it.id)) })
                    db.eventDao().insertAllIgnore(f.events.map { e ->
                        val pid = map.getValue(e.personId)
                        e.copy(id = Ids.newId(), personId = pid, dedupeKey = ContactRules.dedupeKey(pid, LocalDate.parse(e.day), Channel.of(e.channel)!!))
                    })
                }
            }
            db.unmatchedDao().insertAllIgnore(f.unmatched)
            db.unmatchedDao().insertIgnoredIgnore(f.ignored)
        }
        return BackupCodec.counts(f) to pre
    }

    suspend fun deleteAll() = db.withTransaction {
        val w = db.openHelper.writableDatabase
        listOf("contact_event", "person", "unmatched_sighting", "ignored_name").forEach { w.execSQL("DELETE FROM $it") }
    }
}
