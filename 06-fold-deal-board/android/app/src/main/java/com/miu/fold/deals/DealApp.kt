package com.miu.fold.deals

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.miu.fold.deals.data.BoardConditionsStore
import com.miu.fold.deals.data.DealDatabase
import com.miu.fold.deals.data.ListingRepository
import java.io.File

class DealApp : Application() {
    val repository: ListingRepository by lazy { ListingRepository(DealDatabase.open(this).dao()) }

    /** 같은 파일에 DataStore가 둘 생기지 않도록 앱 전체에서 하나만 만든다. */
    val boardConditions: BoardConditionsStore by lazy {
        BoardConditionsStore(PreferenceDataStoreFactory.create {
            File(filesDir, "datastore/${BoardConditionsStore.FILE_NAME}")
        })
    }
}
