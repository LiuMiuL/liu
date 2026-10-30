package com.miu.lastcontact.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** aliases: 카톡 등에서 쓰는 다른 이름(줄바꿈 구분). phones: 정규화한 번호(쉼표 구분). goalDays: 목표 연락 주기(일). */
@Serializable
@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aliases: String = "",
    val phones: String = "",
    val groupName: String = "",
    val goalDays: Int? = null,
    val memo: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 연락 한 건(하루·경로 단위로 합침). 내용은 저장하지 않는다.
 * day: 기기 시간대 기준 yyyy-MM-dd. atMillis: 알 수 있으면 그날 마지막 연락 시각(UTC ms).
 * dedupeKey = personId|day|channel → 유일.
 */
@Serializable
@Entity(
    tableName = "contact_event",
    foreignKeys = [ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("personId"), Index("day"), Index(value = ["dedupeKey"], unique = true)],
)
data class ContactEventEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val day: String,
    val atMillis: Long? = null,
    val channel: String,
    val direction: String,
    val note: String = "",
    val dedupeKey: String,
    val createdAt: Long,
)

/** 알림으로 들어왔지만 등록된 사람과 안 맞은 카톡 이름. 나중에 사람에 연결하면 이 날짜들이 기록으로 옮겨진다. */
@Serializable
@Entity(tableName = "unmatched_sighting", primaryKeys = ["nameKey", "day", "channel"], indices = [Index("nameKey")])
data class UnmatchedSightingEntity(
    val nameKey: String,
    val displayName: String,
    val day: String,
    val channel: String,
    val lastAt: Long,
    val count: Int,
    val roomName: String? = null,
)

/** 사용자가 '무시'한 이름(광고 채널·단체방 모르는 사람 등). */
@Serializable
@Entity(tableName = "ignored_name")
data class IgnoredNameEntity(
    @PrimaryKey val nameKey: String,
    val displayName: String,
)
