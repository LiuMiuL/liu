package com.miu.fold.deals

import android.app.Application
import com.miu.fold.deals.data.DealDatabase
import com.miu.fold.deals.data.ListingRepository

class DealApp : Application() {
    val repository: ListingRepository by lazy { ListingRepository(DealDatabase.open(this).dao()) }
}
