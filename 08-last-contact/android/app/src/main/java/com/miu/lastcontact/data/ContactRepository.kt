package com.miu.lastcontact.data

import androidx.room.withTransaction
import com.miu.lastcontact.core.AppClock
import com.miu.lastcontact.core.Ids
import com.miu.lastcontact.core.Names
import com.miu.lastcontact.core.Phones
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.ContactRules
import com.miu.lastcontact.domain.Direction
import com.miu.lastcontact.domain.LastContact
import com.miu.lastcontact.domain.PlannedDay
import com.miu.lastcontact.domain.Sighting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate

data class PersonSummary(val person: PersonEntity, val last: LastContact?)

enum class SightingResult { RECORDED, UNMATCHED, IGNORED }

data class ImportResult(val newDays: Int, val mergedDays: Int, val createdPeople: Int)

sealed interface SavePersonResult {
    data class Saved(val person: PersonEntity) : SavePersonResult
    data class Invalid(val reason: String) : SavePersonResult
}

class ContactRepository(private val db: AppDatabase, private val clock: AppClock) {
    private val people = db.personDao()
    private val events = db.eventDao()
    private val unmatched = db.unmatchedDao()

    val summaries: Flow<List<PersonSummary>> = combine(people.observeAll(), events.observeLatest()) { ps, latest ->
        val byPerson = latest.groupBy { it.personId }
        ps.map { p ->
            val rows = byPerson[p.id].orEmpty()
            // 같은 날 여러 경로면 시각이 있는 가장 늦은 것, 없으면 직접 기록 우선
            val pick = rows.maxWithOrNull(compareBy<LatestRow>({ it.atMillis ?: -1L }, { it.channel == Channel.MANUAL.name }))
            PersonSummary(p, pick?.let { r -> runCatching { LastContact(LocalDate.parse(r.day), Channel.of(r.channel) ?: Channel.MANUAL, r.atMillis) }.getOrNull() })
        }
    }

    val unmatchedSummary = unmatched.observeSummary()

    fun observePerson(id: String) = people.observe(id)
    fun observeEvents(personId: String) = events.observeForPerson(personId)
    fun observeEventCount(personId: String) = events.observeCount(personId)
    fun observeChannelLatest(personId: String) = events.observeChannelLatest(personId)

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(clock.zone()).toLocalDate()

    suspend fun savePerson(
        id: String?, name: String, aliases: String, phones: String, group: String, goalDays: Int?, memo: String,
    ): SavePersonResult {
        val n = name.trim().take(60)
        if (n.isEmpty()) return SavePersonResult.Invalid("이름을 입력하세요.")
        if (goalDays != null && goalDays !in 1..3650) return SavePersonResult.Invalid("목표 주기는 1~3650일로 입력하세요.")
        val now = clock.nowMillis()
        val old = id?.let { people.get(it) }
        val p = PersonEntity(
            id = old?.id ?: Ids.newId(), name = n,
            aliases = Names.joinAliases(Names.splitAliases(aliases).map { it.take(60) }.filter { Names.key(it) != Names.key(n) }),
            phones = Phones.split(phones).joinToString(","),
            groupName = group.trim().take(20), goalDays = goalDays, memo = memo.take(2000),
            createdAt = old?.createdAt ?: now, updatedAt = now,
        )
        people.upsert(p)
        return SavePersonResult.Saved(p)
    }

    suspend fun deletePerson(id: String) = people.delete(id)

    /**
     * 연락 1건 기록. 같은 사람·날·경로가 이미 있으면 새로 만들지 않고 합친다(방향 합치기, 시각은 늦은 쪽, 메모는 새 메모가 있으면 교체).
     * @return 새 날짜로 추가됐으면 true, 기존 기록에 합쳐졌으면 false
     */
    suspend fun record(personId: String, day: LocalDate, channel: Channel, direction: Direction, atMillis: Long? = null, note: String = ""): Boolean =
        db.withTransaction { recordInTx(personId, day, channel, direction, atMillis, note) }

    private suspend fun recordInTx(personId: String, day: LocalDate, channel: Channel, direction: Direction, atMillis: Long?, note: String): Boolean {
        val key = ContactRules.dedupeKey(personId, day, channel)
        val old = events.byKey(key)
        if (old == null) {
            events.insert(
                ContactEventEntity(Ids.newId(), personId, day.toString(), atMillis, channel.name, direction.name, note.take(500), key, clock.nowMillis()),
            )
            return true
        }
        val merged = old.copy(
            direction = Direction.of(old.direction).merge(direction).name,
            atMillis = listOfNotNull(old.atMillis, atMillis).maxOrNull(),
            note = note.takeIf { it.isNotBlank() }?.take(500) ?: old.note,
        )
        if (merged != old) events.upsert(merged)
        return false
    }

    suspend fun deleteEvent(id: String) = events.delete(id)

    suspend fun findByName(name: String): PersonEntity? {
        val k = Names.key(name)
        if (k.isEmpty()) return null
        val all = people.all()
        return all.firstOrNull { Names.key(it.name) == k } ?: all.firstOrNull { p -> Names.splitAliases(p.aliases).any { Names.key(it) == k } }
    }

    suspend fun phoneIndex(): Map<String, String> =
        people.all().flatMap { p -> p.phones.split(',').filter { it.isNotBlank() }.map { it to p.id } }.toMap()

    /** 카톡 알림 등에서 본 이름 처리: 등록된 사람이면 기록, 무시 목록이면 버림, 아니면 미확인 목록에 날짜만 남김. */
    suspend fun onSighting(s: Sighting, channel: Channel): SightingResult = db.withTransaction {
        val key = Names.key(s.sender)
        if (key.isEmpty()) return@withTransaction SightingResult.IGNORED
        if (unmatched.isIgnored(key) > 0) return@withTransaction SightingResult.IGNORED
        val day = dayOf(s.atMillis)
        val p = findByName(s.sender)
        if (p != null) {
            recordInTx(p.id, day, channel, Direction.IN, s.atMillis, "")
            SightingResult.RECORDED
        } else {
            val old = unmatched.get(key, day.toString(), channel.name)
            unmatched.upsert(
                UnmatchedSightingEntity(
                    key, s.sender.trim().take(60), day.toString(), channel.name,
                    maxOf(old?.lastAt ?: 0, s.atMillis), (old?.count ?: 0) + 1, s.roomName?.take(60) ?: old?.roomName,
                ),
            )
            SightingResult.UNMATCHED
        }
    }

    /** 미확인 이름을 사람에 연결: 그 이름을 별칭으로 추가하고, 쌓인 날짜를 기록으로 옮긴다. */
    suspend fun linkUnmatched(nameKey: String, personId: String): Int = db.withTransaction {
        val p = people.get(personId) ?: return@withTransaction 0
        val sightings = unmatched.forName(nameKey)
        val display = sightings.firstOrNull()?.displayName ?: return@withTransaction 0
        if (Names.key(p.name) != nameKey) {
            people.upsert(p.copy(aliases = Names.joinAliases(Names.splitAliases(p.aliases) + display), updatedAt = clock.nowMillis()))
        }
        var added = 0
        for (s in sightings) {
            val ch = Channel.of(s.channel) ?: Channel.KAKAO_NOTIFICATION
            if (recordInTx(personId, LocalDate.parse(s.day), ch, Direction.IN, s.lastAt, "")) added++
        }
        unmatched.deleteName(nameKey)
        added
    }

    suspend fun ignoreName(nameKey: String, display: String) = db.withTransaction {
        unmatched.ignore(IgnoredNameEntity(nameKey, display.take(60)))
        unmatched.deleteName(nameKey)
    }

    suspend fun unignoreName(nameKey: String) = unmatched.unignore(nameKey)
    fun observeIgnored() = unmatched.observeIgnored()

    /**
     * 카톡 내보내기 가져오기 결과 반영(한 트랜잭션).
     * newPeople: "new:보낸사람" → 새 사람 이름. aliases: personKey → 보낸 사람 이름(사람 이름과 다르면 별칭으로 추가).
     */
    suspend fun applyImport(plan: List<PlannedDay>, newPeople: Map<String, String>, aliases: Map<String, List<String>>): ImportResult = db.withTransaction {
        val now = clock.nowMillis()
        val created = HashMap<String, String>()
        for ((key, name) in newPeople) {
            if (plan.none { it.personKey == key }) continue
            val existing = findByName(name)
            created[key] = existing?.id ?: PersonEntity(Ids.newId(), name.trim().take(60), createdAt = now, updatedAt = now).also { people.upsert(it) }.id
        }
        for ((key, names) in aliases) {
            val id = created[key] ?: key
            val p = people.get(id) ?: continue
            val extra = names.filter { Names.key(it) != Names.key(p.name) }
            if (extra.isNotEmpty()) people.upsert(p.copy(aliases = Names.joinAliases(Names.splitAliases(p.aliases) + extra), updatedAt = now))
        }
        var fresh = 0
        var merged = 0
        for (d in plan) {
            val id = created[d.personKey] ?: d.personKey
            if (people.get(id) == null) continue
            if (recordInTx(id, d.day, Channel.KAKAO_EXPORT, d.direction, null, "")) fresh++ else merged++
        }
        ImportResult(fresh, merged, created.size)
    }

    suspend fun allPeople() = people.all()
}
