package com.miu.lastcontact

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 다른 앱(카톡)에서 공유받은 파일·텍스트. 회전해도 유지되도록 ViewModel에 둔다(Bundle에 본문을 넣지 않음). */
sealed interface Shared {
    data class File(val uri: Uri) : Shared
    data class Text(val text: String) : Shared
}

class IntakeViewModel : ViewModel() {
    private val _pending = MutableStateFlow<Shared?>(null)
    val pending = _pending.asStateFlow()
    fun offer(s: Shared) { _pending.value = s }
    fun consume() { _pending.value = null }
}
