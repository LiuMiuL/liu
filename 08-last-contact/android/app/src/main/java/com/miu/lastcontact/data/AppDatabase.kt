package com.miu.lastcontact.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** 이 앱 전용 DB. 스키마를 바꾸면 version을 올리고 Migration을 추가한다(파괴적 마이그레이션 금지). */
@Database(
    entities = [PersonEntity::class, ContactEventEntity::class, UnmatchedSightingEntity::class, IgnoredNameEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun eventDao(): EventDao
    abstract fun unmatchedDao(): UnmatchedDao

    companion object {
        const val NAME = "last_contact.db"
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
    }
}
