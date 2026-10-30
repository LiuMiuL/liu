package com.miu.lastcontact.domain

import java.io.BufferedReader
import java.time.LocalDate
import java.time.LocalDateTime

data class SenderStats(
    val name: String,
    val count: Int,
    val days: Set<LocalDate>,
    val last: LocalDateTime,
)

data class ExportSummary(
    val chatTitle: String?,
    val senders: List<SenderStats>,
    /** 어떤 보낸 사람이든 메시지가 있었던 날과 그날 보낸 사람 이름들 */
    val dayToSenders: Map<LocalDate, Set<String>>,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val messageCount: Int,
)

sealed interface ParseResult {
    data class Ok(val summary: ExportSummary) : ParseResult
    data class Error(val message: String) : ParseResult
}

/**
 * 카카오톡 '대화 내보내기' 텍스트에서 보낸 사람·날짜만 뽑는다. 메시지 본문은 보관하지 않는다.
 * 지원 형식(카톡 버전·기기별):
 *  A. 안드로이드: `--------------- 2024년 1월 3일 수요일 ---------------` 다음 줄들 `[홍길동] [오후 3:12] 내용`
 *  B. 안드로이드(구): `2024년 1월 3일 오후 3:12, 홍길동 : 내용`
 *  C. PC·iOS: `2024. 1. 3. 오후 3:12, 홍길동 : 내용` (24시간제 `2024. 1. 3. 15:12, …`도)
 *  D. ISO: `2024-01-03 15:12:00, 홍길동 : 내용`
 * 형식에 맞지 않는 줄(여러 줄 메시지의 이어지는 줄, 입장·퇴장 안내 등)은 무시한다.
 */
object KakaoExportParser {
    const val MAX_LINE = 20_000
    const val MAX_CHARS = 100L * 1024 * 1024

    private val title = Regex("""^\s*(.+?)\s*님과 카카오톡 대화\s*$""")
    private val titleEn = Regex("""^\s*Talk_.*|^\s*KakaoTalk Chats with (.+?)\s*$""")
    private val dateHeader = Regex("""^-+\s*(\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일.*$""")
    private val bracket = Regex("""^\[(.+?)\] \[(오전|오후) (\d{1,2}):(\d{2})\] """)
    private val bracket24 = Regex("""^\[(.+?)\] \[(\d{1,2}):(\d{2})\] """)
    private val krFull = Regex("""^(\d{4})년 (\d{1,2})월 (\d{1,2})일 (오전|오후) (\d{1,2}):(\d{2}), (.+?) : """)
    private val dots = Regex("""^(\d{4})\. ?(\d{1,2})\. ?(\d{1,2})\.? (오전|오후) (\d{1,2}):(\d{2}), (.+?) : """)
    private val dots24 = Regex("""^(\d{4})\. ?(\d{1,2})\. ?(\d{1,2})\.? (\d{1,2}):(\d{2}), (.+?) : """)
    private val iso = Regex("""^(\d{4})-(\d{2})-(\d{2}) (\d{1,2}):(\d{2})(?::\d{2})?, (.+?) : """)

    fun parse(text: String): ParseResult = parse(text.reader().buffered())

    fun parse(reader: BufferedReader): ParseResult {
        var chatTitle: String? = null
        var current: LocalDate? = null
        val count = HashMap<String, Int>()
        val days = HashMap<String, MutableSet<LocalDate>>()
        val last = HashMap<String, LocalDateTime>()
        val dayToSenders = java.util.TreeMap<LocalDate, MutableSet<String>>()
        var total = 0
        var chars = 0L
        var lineNo = 0
        reader.useLines { lines ->
            for (raw in lines) {
                lineNo++
                chars += raw.length + 1
                if (chars > MAX_CHARS) return ParseResult.Error("파일이 너무 큽니다(텍스트 100MB 초과).")
                if (raw.length > MAX_LINE) continue
                val line = raw.trimStart('﻿')
                if (lineNo <= 3 && chatTitle == null) {
                    title.find(line)?.let { chatTitle = it.groupValues[1]; continue }
                    titleEn.find(line)?.let { m -> m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { chatTitle = it } }
                }
                dateHeader.find(line)?.let { m ->
                    current = date(m.groupValues[1], m.groupValues[2], m.groupValues[3]); continue
                }
                val hit: Pair<String, LocalDateTime>? = bracket.find(line)?.let { m ->
                    current?.let { d -> m.groupValues[1] to d.atTime(hour12(m.groupValues[2], m.groupValues[3]), m.groupValues[4].toInt()) }
                } ?: bracket24.find(line)?.let { m ->
                    current?.let { d -> m.groupValues[1] to d.atTime(m.groupValues[2].toInt().coerceIn(0, 23), m.groupValues[3].toInt()) }
                } ?: krFull.find(line)?.let { m ->
                    date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { d ->
                        m.groupValues[7] to d.atTime(hour12(m.groupValues[4], m.groupValues[5]), m.groupValues[6].toInt())
                    }
                } ?: dots.find(line)?.let { m ->
                    date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { d ->
                        m.groupValues[7] to d.atTime(hour12(m.groupValues[4], m.groupValues[5]), m.groupValues[6].toInt())
                    }
                } ?: dots24.find(line)?.let { m ->
                    date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { d ->
                        m.groupValues[6] to d.atTime(m.groupValues[4].toInt().coerceIn(0, 23), m.groupValues[5].toInt())
                    }
                } ?: iso.find(line)?.let { m ->
                    date(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { d ->
                        m.groupValues[6] to d.atTime(m.groupValues[4].toInt().coerceIn(0, 23), m.groupValues[5].toInt())
                    }
                }
                if (hit != null) {
                    val (sender, at) = hit
                    val name = sender.trim()
                    if (name.isEmpty() || name.length > 100) continue
                    total++
                    count[name] = (count[name] ?: 0) + 1
                    days.getOrPut(name) { HashSet() } += at.toLocalDate()
                    if (last[name]?.isBefore(at) != false) last[name] = at
                    dayToSenders.getOrPut(at.toLocalDate()) { HashSet() } += name
                }
            }
        }
        if (total == 0) return ParseResult.Error("카카오톡 대화 내보내기 파일 형식이 아니거나, 대화가 없습니다.")
        val senders = count.keys.map { SenderStats(it, count.getValue(it), days.getValue(it), last.getValue(it)) }
            .sortedByDescending { it.count }
        return ParseResult.Ok(ExportSummary(chatTitle, senders, dayToSenders, dayToSenders.firstKey(), dayToSenders.lastKey(), total))
    }

    private fun date(y: String, m: String, d: String): LocalDate? = runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()

    private fun hour12(ampm: String, h: String): Int {
        val hour = h.toInt().coerceIn(0, 12) % 12
        return if (ampm == "오후") hour + 12 else hour
    }
}

enum class MappingKind { ME, PERSON, NEW_PERSON, IGNORE }

data class SenderMapping(val sender: String, val kind: MappingKind, val personId: String? = null)

data class PlannedDay(val personKey: String, val day: LocalDate, val direction: Direction)

/**
 * 가져오기 규칙.
 * - 1:1 방(나를 뺀 사람이 1명): 그 방의 모든 대화 날짜를 그 사람과의 연락으로. 그날 나만 말했으면 OUT, 상대만이면 IN, 둘 다면 BOTH.
 * - 단체방(나를 뺀 사람이 2명 이상): 각 사람이 말한 날짜만(IN). 나만 말한 날은 누구에게도 넣지 않는다.
 * personKey: PERSON이면 personId, NEW_PERSON이면 "new:보낸사람이름".
 */
object ImportPlanner {
    fun plan(summary: ExportSummary, mappings: List<SenderMapping>): List<PlannedDay> {
        val map = mappings.associateBy { it.sender }
        val me = mappings.filter { it.kind == MappingKind.ME }.map { it.sender }.toSet()
        val others = summary.senders.map { it.name }.filter { it !in me }
        val keyOf = { s: String ->
            val m = map[s]
            when (m?.kind) {
                MappingKind.PERSON -> m.personId
                MappingKind.NEW_PERSON -> "new:$s"
                else -> null
            }
        }
        val oneToOne = others.size == 1
        val out = mutableListOf<PlannedDay>()
        if (oneToOne) {
            val other = others.single()
            val key = keyOf(other) ?: return emptyList()
            for ((day, senders) in summary.dayToSenders) {
                val them = other in senders
                val mine = senders.any { it in me }
                val dir = when {
                    them && mine -> Direction.BOTH
                    them -> Direction.IN
                    mine -> Direction.OUT
                    else -> continue
                }
                out += PlannedDay(key, day, dir)
            }
        } else {
            for (s in summary.senders) {
                val key = keyOf(s.name) ?: continue
                s.days.forEach { out += PlannedDay(key, it, Direction.IN) }
            }
        }
        // 같은 사람에 여러 보낸 이름이 연결된 경우 날짜별로 합친다.
        return out.groupBy { it.personKey to it.day }.map { (k, v) ->
            PlannedDay(k.first, k.second, v.map { it.direction }.reduce { a, b -> a.merge(b) })
        }.sortedWith(compareBy({ it.personKey }, { it.day }))
    }
}
