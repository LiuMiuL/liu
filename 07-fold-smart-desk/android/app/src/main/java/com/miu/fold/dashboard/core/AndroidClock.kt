package com.miu.fold.dashboard.core

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import java.time.ZoneId

class AndroidClock(private val resolver: ContentResolver) : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun bootMarker(): String {
        val count = runCatching { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT) }.getOrNull()
        // BOOT_COUNT를 못 읽는 기기에서는 대략적인 부팅 시각(분 단위)으로 대체한다.
        return if (count != null) "count:$count"
        else "approx:${(System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000}"
    }
}
