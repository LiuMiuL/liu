package com.miu.videograb.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.miu.videograb.engine.QualityPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val preset: QualityPreset = QualityPreset.DEFAULT,
    val wifiOnly: Boolean = false,
    /** 엔진(yt-dlp) 자동 업데이트. 하루 한 번 + 사이트 변경으로 실패했을 때. */
    val autoUpdateEngine: Boolean = true,
    val lastEngineUpdateAt: Long? = null,
)

/** 앱 설정. 앱을 껐다 켜도 유지된다. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data.map { p ->
        Settings(QualityPreset.fromName(p[PRESET]), p[WIFI_ONLY] ?: false, p[AUTO_UPDATE] ?: true, p[LAST_UPDATE])
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setPreset(preset: QualityPreset) { dataStore.edit { it[PRESET] = preset.name } }
    suspend fun setWifiOnly(value: Boolean) { dataStore.edit { it[WIFI_ONLY] = value } }
    suspend fun setAutoUpdateEngine(value: Boolean) { dataStore.edit { it[AUTO_UPDATE] = value } }
    suspend fun setLastEngineUpdateAt(at: Long) { dataStore.edit { it[LAST_UPDATE] = at } }

    companion object {
        const val FILE_NAME = "settings.preferences_pb"
        private val PRESET = stringPreferencesKey("preset")
        private val WIFI_ONLY = booleanPreferencesKey("wifiOnly")
        private val AUTO_UPDATE = booleanPreferencesKey("autoUpdateEngine")
        private val LAST_UPDATE = longPreferencesKey("lastEngineUpdateAt")
    }
}
