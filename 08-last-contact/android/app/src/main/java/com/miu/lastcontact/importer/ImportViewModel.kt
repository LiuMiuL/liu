package com.miu.lastcontact.importer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miu.lastcontact.AppContainer
import com.miu.lastcontact.capture.KakaoNotificationListener
import com.miu.lastcontact.core.Names
import com.miu.lastcontact.data.PersonEntity
import com.miu.lastcontact.domain.ExportSummary
import com.miu.lastcontact.domain.ImportPlanner
import com.miu.lastcontact.domain.MappingKind
import com.miu.lastcontact.domain.ParseResult
import com.miu.lastcontact.domain.SenderMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/** 내보내기 가져오기 진행 상태. 파싱 결과(보낸 사람·날짜)는 메모리에만 있고 저장 전까지 DB에 쓰지 않는다. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Reading : ExportState
    data class Error(val message: String) : ExportState
    data class Review(val summary: ExportSummary, val mappings: List<SenderMapping>, val people: List<PersonEntity>) : ExportState
}

class ImportViewModel(private val c: AppContainer, @Suppress("unused") private val saved: SavedStateHandle) : ViewModel() {
    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    val unmatched = c.repo.unmatchedSummary.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ignored = c.repo.observeIgnored().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val people = c.repo.summaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.miu.lastcontact.settings.AppSettings())

    fun consumeMessage() { _message.value = null }

    fun readExport(open: () -> InputStream?) = viewModelScope.launch {
        _export.value = ExportState.Reading
        val r = withContext(Dispatchers.IO) { ExportReader.read({ open() ?: error("파일을 열 수 없습니다.") }) }
        _export.value = when (r) {
            is ParseResult.Error -> ExportState.Error(r.message)
            is ParseResult.Ok -> review(r.summary)
        }
    }

    fun readExportText(text: String) = readExport { text.byteInputStream() }

    /** 자동 선택: 내 카톡 이름 → 나, 등록된 이름·별칭 → 그 사람, 1:1 방 제목과 같은 이름 → 새 사람, 나머지 → 새 사람(단체방은 무시). */
    private suspend fun review(s: ExportSummary): ExportState.Review {
        val myKeys = Names.splitAliases(c.settings.current().myKakaoNames).map { Names.key(it) }.toSet()
        val people = c.repo.allPeople()
        val group = s.senders.size > 2
        val maps = s.senders.map { st ->
            val k = Names.key(st.name)
            val p = people.firstOrNull { Names.key(it.name) == k || Names.splitAliases(it.aliases).any { a -> Names.key(a) == k } }
            when {
                k in myKeys || st.name == "나" -> SenderMapping(st.name, MappingKind.ME)
                p != null -> SenderMapping(st.name, MappingKind.PERSON, p.id)
                group -> SenderMapping(st.name, MappingKind.IGNORE)
                else -> SenderMapping(st.name, MappingKind.NEW_PERSON)
            }
        }.let { list ->
            // 1:1인데 '나'를 못 찾았으면, 방 제목과 다른 쪽을 나로 추정한다.
            if (s.senders.size == 2 && list.none { it.kind == MappingKind.ME } && s.chatTitle != null) {
                val titleKey = Names.key(s.chatTitle)
                list.map { if (Names.key(it.sender) != titleKey && list.any { o -> Names.key(o.sender) == titleKey }) it.copy(kind = MappingKind.ME, personId = null) else it }
            } else list
        }
        return ExportState.Review(s, maps, people.sortedBy { it.name })
    }

    fun setMapping(sender: String, kind: MappingKind, personId: String? = null) = _export.update { st ->
        if (st !is ExportState.Review) st
        else st.copy(mappings = st.mappings.map { if (it.sender == sender) SenderMapping(sender, kind, personId) else it })
    }

    fun cancelExport() { _export.value = ExportState.Idle }

    fun applyExport() = viewModelScope.launch {
        val st = _export.value as? ExportState.Review ?: return@launch
        val plan = ImportPlanner.plan(st.summary, st.mappings)
        val newPeople = st.mappings.filter { it.kind == MappingKind.NEW_PERSON }.associate { "new:${it.sender}" to it.sender }
        val aliases = st.mappings.filter { it.kind == MappingKind.PERSON || it.kind == MappingKind.NEW_PERSON }
            .groupBy({ if (it.kind == MappingKind.PERSON) it.personId!! else "new:${it.sender}" }, { it.sender })
        val r = runCatching { c.repo.applyImport(plan, newPeople, aliases) }
        // '나'로 고른 이름은 다음 가져오기를 위해 기억한다.
        val me = st.mappings.filter { it.kind == MappingKind.ME && it.sender != "나" }.map { it.sender }
        if (me.isNotEmpty()) c.settings.update { it.copy(myKakaoNames = Names.joinAliases(Names.splitAliases(it.myKakaoNames) + me)) }
        r.onSuccess { res ->
            _export.value = ExportState.Idle
            _message.value = buildString {
                append("가져왔습니다: 새 연락 날짜 ${res.newDays}일")
                if (res.mergedDays > 0) append(", 이미 있던 날 ${res.mergedDays}일")
                if (res.createdPeople > 0) append(", 새 사람 ${res.createdPeople}명")
                if (plan.isEmpty()) append("\n연결한 사람이 없어 저장된 날짜가 없습니다.")
            }
        }.onFailure { _message.value = "저장하지 못했습니다(기존 기록은 그대로): ${it.message}" }
    }

    fun linkUnmatched(nameKey: String, personId: String) = viewModelScope.launch {
        val n = c.repo.linkUnmatched(nameKey, personId)
        _message.value = "연결했습니다. 연락 날짜 ${n}일을 반영했고, 다음부터 이 이름은 자동으로 기록됩니다."
    }

    fun linkUnmatchedAsNew(nameKey: String, display: String) = viewModelScope.launch {
        val r = c.repo.savePerson(null, display, "", "", "", null, "")
        if (r is com.miu.lastcontact.data.SavePersonResult.Saved) linkUnmatched(nameKey, r.person.id)
    }

    fun ignore(nameKey: String, display: String) = viewModelScope.launch { c.repo.ignoreName(nameKey, display); _message.value = "'$display'은(는) 앞으로 기록하지 않습니다." }
    fun unignore(nameKey: String) = viewModelScope.launch { c.repo.unignoreName(nameKey) }

    fun setKakaoCapture(on: Boolean) = viewModelScope.launch { c.settings.update { it.copy(kakaoCaptureOn = on) } }
    fun setCallSms(on: Boolean) = viewModelScope.launch { c.settings.update { it.copy(callSmsOn = on) } }

    fun syncCallSms(full: Boolean = false) = viewModelScope.launch {
        _syncing.value = true
        val r = runCatching { c.callSms.sync(c.clock.nowMillis(), full) }
        _syncing.value = false
        _message.value = r.fold(
            { s ->
                if (!s.callAllowed && !s.smsAllowed) "통화·문자 권한이 없어 가져오지 못했습니다."
                else "통화에서 ${s.callDays}일, 문자에서 ${s.smsDays}일의 새 연락을 찾았습니다(등록한 번호만)."
            },
            { "가져오지 못했습니다: ${it.message}" },
        )
    }

    /**
     * 앱을 열 때: 저장된 설정을 실제로 읽은 뒤(초기값으로 오판하지 않게)
     * - 통화·문자 자동 가져오기가 켜져 있으면 조용히 동기화(새로 찾은 게 있을 때만 안내)
     * - 매일 작업은 알림·통화문자 중 하나라도 켜져 있으면 예약 유지, 둘 다 꺼졌으면 취소
     */
    fun onAppResume(appContext: Context) = viewModelScope.launch {
        val st = c.settings.current()
        if (st.reminderOn || st.callSmsOn) com.miu.lastcontact.capture.Reminders.ensure(appContext, st.reminderHour)
        else com.miu.lastcontact.capture.Reminders.cancel(appContext)
        if (!st.callSmsOn || _syncing.value) return@launch
        val r = runCatching { c.callSms.sync(c.clock.nowMillis()) }.getOrNull() ?: return@launch
        if (r.callDays + r.smsDays > 0) _message.value = "통화·문자에서 새 연락 ${r.callDays + r.smsDays}일을 반영했습니다."
    }

    fun listenerEnabled(context: Context) = KakaoNotificationListener.isEnabled(context)
}
