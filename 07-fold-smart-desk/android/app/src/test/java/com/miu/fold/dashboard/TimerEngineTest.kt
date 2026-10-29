package com.miu.fold.dashboard

import com.miu.fold.dashboard.core.FakeClock
import com.miu.fold.dashboard.timer.FocusOutcome
import com.miu.fold.dashboard.timer.TimerEngine
import com.miu.fold.dashboard.timer.TimerFormat
import com.miu.fold.dashboard.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimerEngineTest {
    private val min = 60_000L

    @Test fun t08_tenMinutesThreeElapsedPauseLeavesSeven() {
        val c = FakeClock()
        var s = TimerEngine.start(c, "s1", 10 * min, "집중")
        c.advance(3 * min)
        assertEquals(7 * min, TimerEngine.remaining(s, c))
        s = TimerEngine.pause(s, c)
        assertEquals(TimerState.PAUSED, s.state)
        c.advance(5 * min)
        assertEquals(7 * min, TimerEngine.remaining(s, c)) // 정지 중 감소 없음
        s = TimerEngine.resume(s, c)
        c.advance(2 * min)
        assertEquals(5 * min, TimerEngine.remaining(s, c))
    }

    @Test fun t09_wallClockChangeDoesNotAffectSameBoot() {
        val c = FakeClock()
        val s = TimerEngine.start(c, "s1", 10 * min, "집중")
        c.elapsed += 2 * min
        c.now += 2 * min
        c.now -= 3 * 60 * min // 사용자가 시스템 시각을 3시간 뒤로
        assertEquals(8 * min, TimerEngine.remaining(s, c))
        c.now += 24 * 60 * min // 하루 앞으로
        assertEquals(8 * min, TimerEngine.remaining(s, c))
    }

    @Test fun neverNegativeAndCompletesOnce() {
        val c = FakeClock()
        val s = TimerEngine.start(c, "s1", 1 * min, "집중")
        c.advance(5 * min)
        assertEquals(0, TimerEngine.remaining(s, c))
        val (done, record) = TimerEngine.reconcile(s, c)
        assertEquals(TimerState.COMPLETED, done.state)
        assertNotNull(record)
        assertEquals(FocusOutcome.COMPLETED, record!!.outcome)
        assertEquals(1 * min, record.elapsedMs)
        // 이미 완료된 세션을 다시 reconcile해도 기록이 또 생기지 않는다.
        assertNull(TimerEngine.reconcile(done, c).second)
    }

    @Test fun t12_rebootInterruptsWithoutCompletion() {
        val c = FakeClock()
        var s = TimerEngine.start(c, "s1", 25 * min, "집중")
        c.advance(5 * min)
        s = TimerEngine.checkpoint(s, c)
        assertEquals(20 * min, s.remainingMs)
        // 재부팅: 부팅 마커가 바뀌고 elapsedRealtime이 작아진다. 실제로는 30분이 흘렀다.
        c.boot = "boot-2"; c.elapsed = 1_000; c.now += 30 * min
        val (r, record) = TimerEngine.reconcile(s, c)
        assertEquals(TimerState.INTERRUPTED, r.state)
        assertNull(record) // 임의 완료 이력 없음
        assertEquals(20 * min, TimerEngine.remaining(r, c))
        val resumed = TimerEngine.resume(r, c)
        assertEquals(TimerState.RUNNING, resumed.state)
        assertEquals("boot-2", resumed.bootMarker)
        c.advance(1 * min)
        assertEquals(19 * min, TimerEngine.remaining(resumed, c))
    }

    @Test fun cancelRecordsOnlyFocusedTime() {
        val c = FakeClock()
        var s = TimerEngine.start(c, "s1", 25 * min, "집중")
        c.advance(4 * min)
        s = TimerEngine.pause(s, c)
        c.advance(10 * min)
        val (done, rec) = TimerEngine.cancel(s, c)!!
        assertEquals(TimerState.CANCELLED, done.state)
        assertEquals(FocusOutcome.CANCELLED, rec.outcome)
        assertEquals(4 * min, rec.elapsedMs) // 취소를 완료 시간으로 부풀리지 않음
        assertNull(TimerEngine.cancel(done, c))
    }

    @Test fun invalidTransitionsAreNoOps() {
        val c = FakeClock()
        val s = TimerEngine.start(c, "s1", 5 * min, "x")
        assertEquals(s, TimerEngine.resume(s, c))
        val p = TimerEngine.pause(s, c)
        assertEquals(p, TimerEngine.pause(p, c))
    }

    @Test fun format() {
        assertEquals("25:00", TimerFormat.mmss(25 * min))
        assertEquals("00:01", TimerFormat.mmss(1))
        assertEquals("00:00", TimerFormat.mmss(-5))
        assertEquals("1:00:00", TimerFormat.mmss(60 * min))
    }
}
