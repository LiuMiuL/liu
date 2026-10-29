package com.miu.fold.dashboard.timer

import com.miu.fold.dashboard.core.AppClock

enum class TimerState { IDLE, RUNNING, PAUSED, COMPLETED, CANCELLED, INTERRUPTED;
    val isActive: Boolean get() = this == RUNNING || this == PAUSED || this == INTERRUPTED
}

enum class FocusOutcome { COMPLETED, CANCELLED }

/**
 * 영속 저장되는 타이머 기준값.
 * - [remainingMs]는 [monotonicAnchor] 시점의 남은 시간이다.
 * - RUNNING이면 같은 부팅([bootMarker]) 안에서 `remainingMs - (elapsedRealtime - monotonicAnchor)`로 계산한다.
 * - [wallClockDeadline]은 표시·재부팅 후 안내용일 뿐 남은 시간 계산에는 쓰지 않는다(시스템 시각 변경 방지).
 */
data class TimerSnapshot(
    val id: String,
    val label: String,
    val linkedTodoId: String?,
    val plannedDurationMs: Long,
    val remainingMs: Long,
    val state: TimerState,
    val monotonicAnchor: Long,
    val wallClockDeadline: Long?,
    val bootMarker: String,
    val startedAt: Long,
    val focusElapsedMs: Long,
    val updatedAt: Long,
)

data class FocusResult(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val elapsedMs: Long,
    val outcome: FocusOutcome,
)

/** 상태 전이는 모두 순수 함수. 잘못된 전이는 원래 스냅샷을 그대로 돌려준다. */
object TimerEngine {

    fun start(clock: AppClock, id: String, durationMs: Long, label: String, linkedTodoId: String? = null): TimerSnapshot {
        require(durationMs > 0) { "durationMs must be > 0" }
        val now = clock.nowMillis()
        return TimerSnapshot(
            id = id, label = label, linkedTodoId = linkedTodoId,
            plannedDurationMs = durationMs, remainingMs = durationMs,
            state = TimerState.RUNNING,
            monotonicAnchor = clock.elapsedRealtime(),
            wallClockDeadline = now + durationMs,
            bootMarker = clock.bootMarker(),
            startedAt = now, focusElapsedMs = 0, updatedAt = now,
        )
    }

    /** 표시용 남은 시간. 음수가 되지 않는다. 다른 부팅 세션이면 마지막 저장값을 돌려준다. */
    fun remaining(s: TimerSnapshot, clock: AppClock): Long {
        if (s.state != TimerState.RUNNING) return s.remainingMs.coerceAtLeast(0)
        if (s.bootMarker != clock.bootMarker()) return s.remainingMs.coerceAtLeast(0)
        val passed = (clock.elapsedRealtime() - s.monotonicAnchor).coerceAtLeast(0)
        return (s.remainingMs - passed).coerceAtLeast(0)
    }

    private fun sameBoot(s: TimerSnapshot, clock: AppClock): Boolean =
        s.bootMarker == clock.bootMarker() && clock.elapsedRealtime() >= s.monotonicAnchor

    fun pause(s: TimerSnapshot, clock: AppClock): TimerSnapshot {
        if (s.state != TimerState.RUNNING) return s
        val left = remaining(s, clock)
        return s.copy(
            state = TimerState.PAUSED,
            remainingMs = left,
            focusElapsedMs = s.focusElapsedMs + (s.remainingMs - left),
            monotonicAnchor = clock.elapsedRealtime(),
            wallClockDeadline = null,
            updatedAt = clock.nowMillis(),
        )
    }

    /** PAUSED 또는 INTERRUPTED(재부팅) 상태에서 남은 시간으로 이어서 시작. 새 monotonicAnchor를 만든다. */
    fun resume(s: TimerSnapshot, clock: AppClock): TimerSnapshot {
        if (s.state != TimerState.PAUSED && s.state != TimerState.INTERRUPTED) return s
        if (s.remainingMs <= 0) return s
        val now = clock.nowMillis()
        return s.copy(
            state = TimerState.RUNNING,
            monotonicAnchor = clock.elapsedRealtime(),
            bootMarker = clock.bootMarker(),
            wallClockDeadline = now + s.remainingMs,
            updatedAt = now,
        )
    }

    /** RUNNING 중 주기적으로(화면 이탈 시 등) 기준값을 갱신해 재부팅 시 잃는 시간을 줄인다. */
    fun checkpoint(s: TimerSnapshot, clock: AppClock): TimerSnapshot {
        if (s.state != TimerState.RUNNING || !sameBoot(s, clock)) return s
        val left = remaining(s, clock)
        return s.copy(
            remainingMs = left,
            focusElapsedMs = s.focusElapsedMs + (s.remainingMs - left),
            monotonicAnchor = clock.elapsedRealtime(),
            updatedAt = clock.nowMillis(),
        )
    }

    fun cancel(s: TimerSnapshot, clock: AppClock): Pair<TimerSnapshot, FocusResult>? {
        if (!s.state.isActive) return null
        val settled = if (s.state == TimerState.RUNNING && sameBoot(s, clock)) checkpoint(s, clock) else s
        val now = clock.nowMillis()
        val done = settled.copy(state = TimerState.CANCELLED, wallClockDeadline = null, updatedAt = now)
        return done to FocusResult(s.id, s.startedAt, now, settled.focusElapsedMs, FocusOutcome.CANCELLED)
    }

    /**
     * 앱 복귀·프로세스 재생성 시 호출.
     * - 같은 부팅 + 남은 시간 0 → COMPLETED와 집중 기록(세션당 1건은 저장소의 sessionId 유일 제약으로 보장)
     * - 다른 부팅 + RUNNING → INTERRUPTED. 마지막 기준값의 남은 시간을 유지하고 완료 이력은 만들지 않는다.
     */
    fun reconcile(s: TimerSnapshot, clock: AppClock): Pair<TimerSnapshot, FocusResult?> {
        if (s.state != TimerState.RUNNING) return s to null
        if (!sameBoot(s, clock)) {
            return s.copy(state = TimerState.INTERRUPTED, wallClockDeadline = null, updatedAt = clock.nowMillis()) to null
        }
        val left = remaining(s, clock)
        if (left > 0) return s to null
        val now = clock.nowMillis()
        val elapsed = s.focusElapsedMs + s.remainingMs
        // 늦게 확인했더라도 종료 시각은 실제로 0이 된 순간(= 지금 − 초과 경과)으로 기록한다.
        val overshoot = ((clock.elapsedRealtime() - s.monotonicAnchor) - s.remainingMs).coerceAtLeast(0)
        val endedAt = now - overshoot
        val done = s.copy(
            state = TimerState.COMPLETED, remainingMs = 0, focusElapsedMs = elapsed,
            monotonicAnchor = clock.elapsedRealtime(), wallClockDeadline = null, updatedAt = now,
        )
        return done to FocusResult(s.id, s.startedAt, endedAt, elapsed, FocusOutcome.COMPLETED)
    }
}

object TimerFormat {
    fun mmss(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val sec = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
