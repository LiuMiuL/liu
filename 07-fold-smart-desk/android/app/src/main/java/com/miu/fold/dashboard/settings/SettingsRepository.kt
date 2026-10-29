package com.miu.fold.dashboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class ThemeMode(val label: String) { SYSTEM("시스템"), LIGHT("밝게"), DARK("어둡게") }

@Serializable
data class AppSettings(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val hideRecentContent: Boolean = false,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
) {
    val theme: ThemeMode get() = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val hideRecent = booleanPreferencesKey("hide_recent_content")
        val focus = intPreferencesKey("focus_minutes")
        val brk = intPreferencesKey("break_minutes")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            AppSettings(
                themeMode = p[Keys.theme] ?: ThemeMode.SYSTEM.name,
                hideRecentContent = p[Keys.hideRecent] ?: false,
                focusMinutes = (p[Keys.focus] ?: 25).coerceIn(1, 180),
                breakMinutes = (p[Keys.brk] ?: 5).coerceIn(1, 60),
            )
        }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setHideRecent(v: Boolean) = context.dataStore.edit { it[Keys.hideRecent] = v }
    suspend fun setFocusMinutes(v: Int) = context.dataStore.edit { it[Keys.focus] = v.coerceIn(1, 180) }
    suspend fun setBreakMinutes(v: Int) = context.dataStore.edit { it[Keys.brk] = v.coerceIn(1, 60) }

    suspend fun apply(s: AppSettings) = context.dataStore.edit {
        it[Keys.theme] = s.theme.name
        it[Keys.hideRecent] = s.hideRecentContent
        it[Keys.focus] = s.focusMinutes.coerceIn(1, 180)
        it[Keys.brk] = s.breakMinutes.coerceIn(1, 60)
    }
}
