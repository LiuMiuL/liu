package com.miu.fold.dashboard.calendar

import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.LocalEventEntity
import kotlinx.coroutines.flow.Flow

/** 이 앱 DB에만 저장되는 로컬 일정. 시스템/Google 캘린더와 동기화하지 않는다. */
class EventRepository(db: AppDatabase, private val clock: AppClock) {
    private val dao = db.eventDao()
    val all: Flow<List<LocalEventEntity>> = dao.observeAll()

    suspend fun get(id: String) = dao.get(id)

    suspend fun save(id: String?, title: String, span: EventSpan, zoneId: String?, memo: String): LocalEventEntity {
        val now = clock.nowMillis()
        val old = id?.let { dao.get(it) }
        val e = LocalEventEntity(
            id = old?.id ?: Ids.newId(),
            title = title.trim().ifEmpty { "제목 없는 일정" }.take(300),
            allDay = span.allDay,
            localDateStart = span.startDate.toString(),
            localDateEnd = span.endDate.toString(),
            startInstant = span.startInstant,
            endInstant = span.endInstant,
            zoneId = zoneId,
            memo = memo.take(5000),
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(e)
        return e
    }

    suspend fun delete(id: String) = dao.delete(id)
}

fun LocalEventEntity.span(): EventSpan = EventSpan(
    allDay = allDay,
    startDate = java.time.LocalDate.parse(localDateStart),
    endDate = java.time.LocalDate.parse(localDateEnd),
    startInstant = startInstant,
    endInstant = endInstant,
)
