package com.miu.fold.dashboard.timer

import com.miu.fold.dashboard.core.AppClock
import com.miu.fold.dashboard.core.Ids
import com.miu.fold.dashboard.data.FocusRecordEntity
import com.miu.fold.dashboard.data.TimerDao
import com.miu.fold.dashboard.data.TimerSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 활성 세션은 최대 1개. 모든 상태 변경은 Mutex로 직렬화해 시작 버튼 연타로 세션이 여러 개 생기지 않게 한다.
 * 완료 기록은 sessionId 기본키 + IGNORE로 세션당 1건만 저장된다.
 */
class TimerController(
    private val dao: TimerDao,
    private val clock: AppClock,
    private val alarm: TimerAlarm = TimerAlarm.None,
) {
    private val mutex = Mutex()

    val active: Flow<TimerSnapshot?> = dao.observeActive().map { it?.toSnapshot() }

    /** 가장 최근 세션(완료·취소 포함). 화면에서 '방금 완료됨'을 보여줄 때 쓴다. */
    val latest: Flow<TimerSnapshot?> = dao.observeLatest().map { it?.toSnapshot() }
    val records: Flow<List<FocusRecordEntity>> = dao.observeRecords()

    suspend fun start(durationMs: Long, label: String, linkedTodoId: String? = null): TimerSnapshot = mutex.withLock {
        dao.active().firstOrNull()?.let { return@withLock it.toSnapshot() }
        val s = TimerEngine.start(clock, Ids.newId(), durationMs, label, linkedTodoId)
        dao.upsert(s.toEntity())
        alarm.sync(s)
        s
    }

    suspend fun pause() = mutate { TimerEngine.pause(it, clock) }
    suspend fun resume() = mutate { TimerEngine.resume(it, clock) }
    suspend fun checkpoint() = mutate { TimerEngine.checkpoint(it, clock) }

    suspend fun cancel() = mutex.withLock {
        val cur = dao.active().firstOrNull()?.toSnapshot() ?: return@withLock
        val (done, record) = TimerEngine.cancel(cur, clock) ?: return@withLock
        dao.upsert(done.toEntity())
        dao.insertRecord(record.toEntity())
        alarm.sync(null)
    }

    /** 같은 설정으로 다시 시작: 현재 세션은 취소로 기록하고 새 세션을 만든다. */
    suspend fun restart(): TimerSnapshot? {
        val cur = mutex.withLock { dao.active().firstOrNull()?.toSnapshot() } ?: return null
        cancel()
        return start(cur.plannedDurationMs, cur.label, cur.linkedTodoId)
    }

    /** 앱 복귀·틱마다 호출. 완료/재부팅 중단 전이를 적용하고 결과 상태를 돌려준다. */
    suspend fun reconcile(): TimerSnapshot? = mutex.withLock {
        val list = dao.active()
        // 비정상적으로 활성 세션이 여러 개면 최신 1개만 남기고 나머지는 취소 처리한다.
        list.drop(1).forEach { extra ->
            TimerEngine.cancel(extra.toSnapshot(), clock)?.let { (d, r) -> dao.upsert(d.toEntity()); dao.insertRecord(r.toEntity()) }
        }
        val cur = list.firstOrNull()?.toSnapshot() ?: run { alarm.sync(null); return@withLock null }
        val (next, record) = TimerEngine.reconcile(cur, clock)
        if (next != cur) dao.upsert(next.toEntity())
        if (record != null) dao.insertRecord(record.toEntity())
        // 앱 복귀·권한 변경 후에도 예약이 현재 상태와 맞도록 매번 다시 맞춘다(같은 요청 코드라 중복되지 않음).
        alarm.sync(next)
        next
    }

    /** 외부(알림 등)에서 같은 완료 이벤트가 중복 도착해도 기록은 1건. */
    suspend fun recordCompletion(result: FocusResult) {
        dao.insertRecord(result.toEntity())
    }

    fun remaining(s: TimerSnapshot): Long = TimerEngine.remaining(s, clock)

    private suspend fun mutate(f: (TimerSnapshot) -> TimerSnapshot) = mutex.withLock {
        val cur = dao.active().firstOrNull()?.toSnapshot() ?: return@withLock
        val next = f(cur)
        if (next != cur) dao.upsert(next.toEntity())
        alarm.sync(next)
    }
}

fun TimerSessionEntity.toSnapshot() = TimerSnapshot(
    id, label, linkedTodoId, plannedDurationMs, remainingMs,
    runCatching { TimerState.valueOf(state) }.getOrDefault(TimerState.INTERRUPTED),
    monotonicAnchor, wallClockDeadline, bootMarker, startedAt, focusElapsedMs, updatedAt,
)

fun TimerSnapshot.toEntity() = TimerSessionEntity(
    id, label, linkedTodoId, plannedDurationMs, remainingMs, state.name,
    monotonicAnchor, wallClockDeadline, bootMarker, startedAt, focusElapsedMs, updatedAt,
)

fun FocusResult.toEntity() = FocusRecordEntity(sessionId, startedAt, endedAt, elapsedMs, outcome.name)
