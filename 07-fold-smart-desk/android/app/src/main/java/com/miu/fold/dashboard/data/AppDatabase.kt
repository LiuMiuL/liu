package com.miu.fold.dashboard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 이 앱 전용 DB. 다른 앱 DB를 읽거나 공유하지 않는다.
 * 스키마 변경 시 version을 올리고 Migration을 추가한다. fallbackToDestructiveMigration은 쓰지 않는다.
 */
@Database(
    entities = [
        DashboardCardEntity::class, LayoutPlacementEntity::class, NoteEntity::class, TodoEntity::class,
        LocalEventEntity::class, ShortcutEntity::class, TimerSessionEntity::class, FocusRecordEntity::class,
        RecentItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dashboardDao(): DashboardDao
    abstract fun noteDao(): NoteDao
    abstract fun todoDao(): TodoDao
    abstract fun eventDao(): EventDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun timerDao(): TimerDao
    abstract fun recentDao(): RecentDao

    companion object {
        const val NAME = "fold_smart_desk.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}

/** LIKE 검색용 이스케이프. */
fun likePattern(q: String): String {
    val escaped = q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}
