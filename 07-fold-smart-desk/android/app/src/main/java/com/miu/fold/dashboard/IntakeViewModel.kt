package com.miu.fold.dashboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SharedText(val text: String, val subject: String?, val truncated: Boolean)

/** 외부 공유 수신 대기열. 회전해도 미리보기가 유지되도록 ViewModel에 둔다(Bundle에 본문을 넣지 않음). */
class IntakeViewModel : ViewModel() {
    private val _pending = MutableStateFlow<SharedText?>(null)
    val pending: StateFlow<SharedText?> = _pending.asStateFlow()

    fun offer(s: SharedText) { _pending.value = s }
    fun consume() { _pending.value = null }
}
