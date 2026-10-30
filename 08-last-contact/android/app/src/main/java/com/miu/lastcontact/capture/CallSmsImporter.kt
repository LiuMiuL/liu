package com.miu.lastcontact.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.miu.lastcontact.data.ContactRepository
import com.miu.lastcontact.domain.CallSmsRules
import com.miu.lastcontact.domain.LogRow
import com.miu.lastcontact.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncResult(val callDays: Int, val smsDays: Int, val callAllowed: Boolean, val smsAllowed: Boolean)

/**
 * 통화 기록·문자함에서 번호·시각·종류만 읽어 등록한 사람과의 연락 날짜를 남긴다. 문자 본문(BODY)은 조회하지 않는다.
 * 처음에는 최근 1년, 그다음부터는 마지막 동기화 이후만 읽는다.
 */
class CallSmsImporter(
    private val context: Context,
    private val repo: ContactRepository,
    private val settings: SettingsRepository,
) {
    fun canReadCalls() = granted(Manifest.permission.READ_CALL_LOG)
    fun canReadSms() = granted(Manifest.permission.READ_SMS)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    suspend fun sync(nowMillis: Long, full: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val (lastCall, lastSms) = settings.lastSync()
        val yearAgo = nowMillis - 365L * 24 * 3600 * 1000
        val index = repo.phoneIndex()
        var callDays = 0
        var smsDays = 0
        if (canReadCalls()) {
            val since = if (full || lastCall == 0L) yearAgo else lastCall
            val rows = readCalls(since)
            callDays = apply(CallSmsRules.match(rows, index))
            settings.setLastSync(call = nowMillis, sms = null)
        }
        if (canReadSms()) {
            val since = if (full || lastSms == 0L) yearAgo else lastSms
            val rows = readSms(since)
            smsDays = apply(CallSmsRules.match(rows, index))
            settings.setLastSync(call = null, sms = nowMillis)
        }
        SyncResult(callDays, smsDays, canReadCalls(), canReadSms())
    }

    private suspend fun apply(list: List<com.miu.lastcontact.domain.MatchedLog>): Int {
        var fresh = 0
        for (m in list) if (repo.record(m.personId, repo.dayOf(m.atMillis), m.channel, m.direction, m.atMillis)) fresh++
        return fresh
    }

    private fun readCalls(since: Long): List<LogRow> {
        val out = mutableListOf<LogRow>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION),
            "${CallLog.Calls.DATE} > ?", arrayOf(since.toString()), "${CallLog.Calls.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val kind = when (c.getInt(2)) {
                    CallLog.Calls.INCOMING_TYPE -> LogRow.Kind.CALL_IN
                    CallLog.Calls.OUTGOING_TYPE -> LogRow.Kind.CALL_OUT
                    CallLog.Calls.MISSED_TYPE -> LogRow.Kind.CALL_MISSED
                    else -> LogRow.Kind.OTHER
                }
                out += LogRow(c.getString(0), c.getLong(1), kind, c.getLong(3))
            }
        }
        return out
    }

    private fun readSms(since: Long): List<LogRow> {
        val out = mutableListOf<LogRow>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val kind = when (c.getInt(2)) {
                    Telephony.Sms.MESSAGE_TYPE_INBOX -> LogRow.Kind.SMS_IN
                    Telephony.Sms.MESSAGE_TYPE_SENT -> LogRow.Kind.SMS_OUT
                    else -> LogRow.Kind.OTHER
                }
                out += LogRow(c.getString(0), c.getLong(1), kind)
            }
        }
        return out
    }
}
