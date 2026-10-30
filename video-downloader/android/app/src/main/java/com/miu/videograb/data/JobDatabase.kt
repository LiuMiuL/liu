package com.miu.videograb.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DownloadJob::class], version = 1, exportSchema = true)
abstract class JobDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao

    companion object {
        const val FILE_NAME = "videograb.db"

        /** 파괴적 마이그레이션은 쓰지 않는다. 스키마를 바꾸면 Migration을 추가한다. */
        fun open(context: Context, name: String = FILE_NAME): JobDatabase =
            Room.databaseBuilder(context.applicationContext, JobDatabase::class.java, name).build()
    }
}
