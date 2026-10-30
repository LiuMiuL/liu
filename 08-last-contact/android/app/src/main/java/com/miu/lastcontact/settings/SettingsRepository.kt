package com.miu.lastcontact.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.miu.lastcontact.domain.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    /** 내 카톡 이름(줄바꿈 구분). 내보내기 가져오기에서 '나'를 자동 선택하는 데 쓴다. */
    val myKakaoNames: String = "",
    /** 알림 접근 권한이 있어도 사용자가 잠시 멈출 수 있게 하는 스위치 */
    val kakaoCaptureOn: Boolean = true,
    val callSmsOn: Boolean = false,
    val reminderOn: Boolean = false,
    val reminderHour: Int = 20,
    val sort: String = SortMode.DUE.name,
    val notificationAsked: Boolean = false,
) {
    val sortMode: SortMode get() = runCatching { SortMode.valueOf(sort) }.getOrDefault(SortMode.DUE)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val myNames = stringPreferencesKey("my_kakao_names")
        val kakaoOn = booleanPreferencesKey("kakao_capture_on")
        val callSms = booleanPreferencesKey("call_sms_on")
        val reminder = booleanPreferencesKey("reminder_on")
        val hour = intPreferencesKey("reminder_hour")
        val sort = stringPreferencesKey("sort")
        val asked = booleanPreferencesKey("notification_asked")
        val lastCall = longPreferencesKey("last_call_sync")
        val lastSms = longPreferencesKey("last_sms_sync")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.catch { emit(emptyPreferences()) }.map { p ->
        AppSettings(
            myKakaoNames = p[K.myNames] ?: "",
            kakaoCaptureOn = p[K.kakaoOn] ?: true,
            callSmsOn = p[K.callSms] ?: false,
            reminderOn = p[K.reminder] ?: false,
            reminderHour = (p[K.hour] ?: 20).coerceIn(0, 23),
            sort = p[K.sort] ?: SortMode.DUE.name,
            notificationAsked = p[K.asked] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(f: (AppSettings) -> AppSettings) {
        val n = f(current())
        context.dataStore.edit {
            it[K.myNames] = n.myKakaoNames.take(500)
            it[K.kakaoOn] = n.kakaoCaptureOn
            it[K.callSms] = n.callSmsOn
            it[K.reminder] = n.reminderOn
            it[K.hour] = n.reminderHour.coerceIn(0, 23)
            it[K.sort] = n.sort
            it[K.asked] = n.notificationAsked
        }
    }

    /** 통화·문자 마지막 동기화 시각(백업 대상 아님). 0이면 아직 안 함. */
    suspend fun lastSync(): Pair<Long, Long> = context.dataStore.data.first().let { (it[K.lastCall] ?: 0L) to (it[K.lastSms] ?: 0L) }

    suspend fun setLastSync(call: Long?, sms: Long?) = context.dataStore.edit {
        call?.let { v -> it[K.lastCall] = v }
        sms?.let { v -> it[K.lastSms] = v }
    }
}
