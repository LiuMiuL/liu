package com.miu.lastcontact.core

import android.os.SystemClock
import java.time.ZoneId

class AndroidClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun bootMarker(): String = "unused"
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
