package com.miu.fold.deals.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.miu.fold.deals.comparison.Comparison
import com.miu.fold.deals.listings.Listing
import com.miu.fold.deals.observations.PriceObservation

@Database(
    entities = [Listing::class, PriceObservation::class, Comparison::class],
    version = 1,
    exportSchema = true,
)
abstract class DealDatabase : RoomDatabase() {
    abstract fun dao(): DealDao

    companion object {
        const val FILE_NAME = "fold_deal_board.db"

        /** 파괴적 마이그레이션은 쓰지 않는다. 스키마를 바꾸면 Migration을 추가한다. */
        fun open(context: Context, name: String = FILE_NAME): DealDatabase =
            Room.databaseBuilder(context.applicationContext, DealDatabase::class.java, name).build()
    }
}
