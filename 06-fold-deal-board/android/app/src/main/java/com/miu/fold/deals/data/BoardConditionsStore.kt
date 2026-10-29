package com.miu.fold.deals.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.miu.fold.deals.listings.Category
import com.miu.fold.deals.listings.SpecFilter
import kotlinx.coroutines.flow.first

/**
 * 보드에서 고른 조건. 앱을 완전히 껐다 켜도 유지하도록 DataStore에 저장한다.
 * 검색어는 일시적인 입력이라 저장하지 않는다.
 */
data class BoardConditions(
    val sort: String,
    val onSaleOnly: Boolean,
    val hideExcluded: Boolean,
    val spec: SpecFilter,
)

class BoardConditionsStore(private val dataStore: DataStore<Preferences>) {

    /** 저장된 조건. 한 번도 저장하지 않았으면 null. */
    suspend fun load(): BoardConditions? {
        val p = dataStore.data.first()
        if (p[SAVED] != true) return null
        return BoardConditions(
            sort = p[SORT] ?: "",
            onSaleOnly = p[ON_SALE] ?: false,
            hideExcluded = p[HIDE_EXCLUDED] ?: true,
            spec = SpecFilter(
                // 앱 업데이트로 사라진 분류 이름은 조건 없음으로 읽는다.
                category = p[CATEGORY]?.let { name -> Category.entries.firstOrNull { it.name == name } },
                models = p[MODELS].orEmpty(),
                ramGb = p[RAM].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
                storageGb = p[STORAGE].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
                publishedWithinDays = p[PUBLISHED_DAYS],
                checkedWithinDays = p[CHECKED_DAYS],
                includeUnknown = p[INCLUDE_UNKNOWN] ?: false,
            ),
        )
    }

    suspend fun save(c: BoardConditions) {
        dataStore.edit { p ->
            p[SAVED] = true
            p[SORT] = c.sort
            p[ON_SALE] = c.onSaleOnly
            p[HIDE_EXCLUDED] = c.hideExcluded
            c.spec.category?.let { p[CATEGORY] = it.name } ?: p.remove(CATEGORY)
            p[MODELS] = c.spec.models
            p[RAM] = c.spec.ramGb.map { it.toString() }.toSet()
            p[STORAGE] = c.spec.storageGb.map { it.toString() }.toSet()
            c.spec.publishedWithinDays?.let { p[PUBLISHED_DAYS] = it } ?: p.remove(PUBLISHED_DAYS)
            c.spec.checkedWithinDays?.let { p[CHECKED_DAYS] = it } ?: p.remove(CHECKED_DAYS)
            p[INCLUDE_UNKNOWN] = c.spec.includeUnknown
        }
    }

    companion object {
        const val FILE_NAME = "board_conditions.preferences_pb"
        private val SAVED = booleanPreferencesKey("saved")
        private val SORT = stringPreferencesKey("sort")
        private val ON_SALE = booleanPreferencesKey("onSaleOnly")
        private val HIDE_EXCLUDED = booleanPreferencesKey("hideExcluded")
        private val CATEGORY = stringPreferencesKey("category")
        private val MODELS = stringSetPreferencesKey("models")
        private val RAM = stringSetPreferencesKey("ramGb")
        private val STORAGE = stringSetPreferencesKey("storageGb")
        private val PUBLISHED_DAYS = intPreferencesKey("publishedWithinDays")
        private val CHECKED_DAYS = intPreferencesKey("checkedWithinDays")
        private val INCLUDE_UNKNOWN = booleanPreferencesKey("includeUnknown")
    }
}
