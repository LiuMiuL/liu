package com.miu.fold.dashboard.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.data.FocusRecordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimerUi(val snapshot: TimerSnapshot?, val remainingMs: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModel(private val c: AppContainer) : ViewModel() {
    private val timer = c.timer

    /**
     * 화면에 구독자가 있을 때만(WhileSubscribed) 250ms마다 남은 시간을 다시 계산한다.
     * 값은 매번 영속 기준값에서 계산하므로 틱이 멈춰도 오차가 누적되지 않는다.
     */
    val ui: StateFlow<TimerUi> = timer.latest.flatMapLatest { s ->
        if (s == null || s.state != TimerState.RUNNING) flowOf(TimerUi(s, s?.remainingMs ?: 0))
        else flow {
            while (true) {
                val left = timer.remaining(s)
                emit(TimerUi(s, left))
                if (left <= 0) { timer.reconcile(); break }
                delay(250)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), TimerUi(null, 0))

    val records: StateFlow<List<FocusRecordEntity>> =
        timer.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start(minutes: Int, label: String, todoId: String? = null) =
        viewModelScope.launch { timer.start(minutes.coerceIn(1, 600) * 60_000L, label, todoId) }

    fun pause() = viewModelScope.launch { timer.pause() }
    fun resume() = viewModelScope.launch { timer.resume() }
    fun cancel() = viewModelScope.launch { timer.cancel() }
    fun restart() = viewModelScope.launch { timer.restart() }
    fun onAppResume() = viewModelScope.launch { timer.reconcile() }
    fun onAppStop() = viewModelScope.launch { timer.checkpoint() }
}
