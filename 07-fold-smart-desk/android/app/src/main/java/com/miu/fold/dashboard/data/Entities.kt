package com.miu.fold.dashboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "dashboard_card")
data class DashboardCardEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val configJson: String = "{}",
    val enabled: Boolean = true,
)

/** cardId + profile 유일. 숨김(visible)은 프로필별이라 한쪽에서 숨겨도 다른 프로필 배치는 그대로다. */
@Serializable
@Entity(
    tableName = "layout_placement",
    primaryKeys = ["cardId", "profile"],
    foreignKeys = [ForeignKey(
        entity = DashboardCardEntity::class, parentColumns = ["id"], childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profile")],
)
data class LayoutPlacementEntity(
    val cardId: String,
    val profile: String,
    val orderKey: Int,
    val span: Int,
    val visible: Boolean = true,
)

@Serializable
@Entity(tableName = "note", indices = [Index("updatedAt"), Index("deletedAt")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val tags: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Int = 1,
)

/** dueDate는 날짜만(ISO yyyy-MM-dd), dueInstant는 정확 시각. 둘을 구분해 저장한다. */
@Serializable
@Entity(tableName = "todo", indices = [Index("dueDate"), Index("doneAt"), Index("sourceNoteId")])
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val dueDate: String? = null,
    val dueInstant: Long? = null,
    val priority: Int = 0,
    val doneAt: Long? = null,
    val orderKey: Long,
    val sourceNoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "local_event", indices = [Index("localDateStart")])
data class LocalEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val allDay: Boolean,
    val localDateStart: String,
    val localDateEnd: String,
    val startInstant: Long? = null,
    val endInstant: Long? = null,
    val zoneId: String? = null,
    val memo: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** target: WEB=검증된 URL, APP=패키지명, INTERNAL=InternalTarget.key. 토큰·비밀번호는 저장하지 않는다. */
@Serializable
@Entity(tableName = "shortcut")
data class ShortcutEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val target: String,
    val iconRef: String? = null,
    val fallbackUrl: String? = null,
    val orderKey: Int = 0,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "timer_session", indices = [Index("state")])
data class TimerSessionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val linkedTodoId: String?,
    val plannedDurationMs: Long,
    val remainingMs: Long,
    val state: String,
    val monotonicAnchor: Long,
    val wallClockDeadline: Long?,
    val bootMarker: String,
    val startedAt: Long,
    val focusElapsedMs: Long,
    val updatedAt: Long,
)

/** sessionId가 기본키이므로 같은 세션의 완료 이벤트가 중복 도착해도 1건만 남는다. */
@Serializable
@Entity(tableName = "focus_record")
data class FocusRecordEntity(
    @PrimaryKey val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val elapsedMs: Long,
    val outcome: String,
)

@Serializable
@Entity(tableName = "recent_item", primaryKeys = ["type", "localId"], indices = [Index("openedAt")])
data class RecentItemEntity(
    val type: String,
    val localId: String,
    val openedAt: Long,
)
