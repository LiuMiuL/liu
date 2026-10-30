package com.miu.lastcontact.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 시간 추상화. 테스트에서는 [FakeClock]을 써서 실제로 기다리지 않고 경과시간을 조작한다.
 *
 * - [nowMillis]: wall clock (UTC epoch ms). 사용자가 시스템 시각을 바꾸면 변한다.
 * - [elapsedRealtime]: 부팅 이후 monotonic 경과시간. 같은 부팅 세션에서는 시각 변경 영향을 받지 않는다.
 * - [bootMarker]: 부팅 세션 식별자. 값이 달라지면 재부팅된 것으로 본다.
 */
interface AppClock {
    fun nowMillis(): Long
    fun elapsedRealtime(): Long
    fun bootMarker(): String
    fun zone(): ZoneId

    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone()).toLocalDate()
}

class FakeClock(
    var now: Long = 1_800_000_000_000L,
    var elapsed: Long = 10_000_000L,
    var boot: String = "boot-1",
    var zoneId: ZoneId = ZoneId.of("Asia/Seoul"),
) : AppClock {
    override fun nowMillis(): Long = now
    override fun elapsedRealtime(): Long = elapsed
    override fun bootMarker(): String = boot
    override fun zone(): ZoneId = zoneId

    /** 실제 시간이 흐른 것처럼 두 시계를 함께 전진시킨다. */
    fun advance(ms: Long) {
        now += ms
        elapsed += ms
    }
}
