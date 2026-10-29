package com.miu.fold.dashboard.recent

import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.data.AppDatabase
import com.miu.fold.dashboard.data.RecentItemEntity

enum class RecentType(val key: String, val label: String) {
    NOTE("note", "메모"), TODO("todo", "할 일"), EVENT("event", "일정"), SHORTCUT("shortcut", "바로가기");
    companion object { fun fromKey(k: String) = entries.firstOrNull { it.key == k } }
}

/** 이 앱 안에서 사용자가 실제로 연 항목만 기록한다. */
class RecentRepository(db: AppDatabase, private val clock: AppClock) {
    private val dao = db.recentDao()
    val items = dao.observe(20)

    suspend fun record(type: RecentType, id: String) {
        dao.upsert(RecentItemEntity(type.key, id, clock.nowMillis()))
        dao.trim()
    }

    suspend fun remove(type: String, id: String) = dao.delete(type, id)
    suspend fun clear() = dao.clear()
}
