package com.miu.lastcontact

import com.miu.lastcontact.capture.ReminderText
import com.miu.lastcontact.core.Names
import com.miu.lastcontact.core.Phones
import com.miu.lastcontact.domain.CallSmsRules
import com.miu.lastcontact.domain.Channel
import com.miu.lastcontact.domain.ContactRules
import com.miu.lastcontact.domain.Direction
import com.miu.lastcontact.domain.KakaoNotificationRules
import com.miu.lastcontact.domain.LogRow
import com.miu.lastcontact.domain.NotificationInfo
import com.miu.lastcontact.domain.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test fun phonesNormalize() {
        assertEquals("01012345678", Phones.normalize("010-1234-5678"))
        assertEquals("01012345678", Phones.normalize("+82 10-1234-5678"))
        assertEquals("01012345678", Phones.normalize("821012345678"))
        assertEquals("01012345678", Phones.normalize("0082 10 1234 5678"))
        assertEquals("0212345678", Phones.normalize("02-1234-5678"))
        assertNull(Phones.normalize("1588"))
        assertNull(Phones.normalize(null))
        assertEquals("010-1234-5678", Phones.display("01012345678"))
        assertEquals(listOf("01011112222", "0212345678"), Phones.split("010-1111-2222, 02 1234 5678,abc"))
    }

    @Test fun namesKeyIgnoresSpaceAndCase() {
        assertEquals(Names.key("김 민수"), Names.key("김민수 "))
        assertEquals(Names.key("Mom"), Names.key("mom"))
        assertEquals(listOf("엄마", "어머니"), Names.splitAliases("엄마\n어머니, 엄마 ,"))
    }

    @Test fun daysAndOverdue() {
        assertEquals(0L, ContactRules.daysSince(today, today))
        assertEquals(29L, ContactRules.daysSince(LocalDate.of(2026, 10, 1), today))
        assertNull(ContactRules.daysSince(null, today))
        assertEquals(15L, ContactRules.overdueBy(LocalDate.of(2026, 10, 1), 14, today))
        assertNull(ContactRules.overdueBy(LocalDate.of(2026, 10, 1), null, today))
        assertEquals(Long.MAX_VALUE, ContactRules.overdueBy(null, 7, today))
        assertTrue(ContactRules.isOverdue(LocalDate.of(2026, 10, 1), 14, today))
        assertFalse(ContactRules.isOverdue(LocalDate.of(2026, 10, 25), 14, today))
        assertEquals("오늘", ContactRules.relative(today, today))
        assertEquals("어제", ContactRules.relative(today.minusDays(1), today))
        assertEquals("연락 기록 없음", ContactRules.relative(null, today))
        assertFalse(ContactRules.validManualDay(today.plusDays(1), today))
    }

    @Test fun sortDueFirst() {
        data class P(val n: String, val last: LocalDate?, val goal: Int?)
        val list = listOf(
            P("가", today.minusDays(3), 14), P("나", today.minusDays(40), 30), P("다", null, 7), P("라", today.minusDays(100), null),
        )
        val due = ContactRules.sort(list, SortMode.DUE, today, { it.n }, { it.last }, { it.goal }).map { it.n }
        assertEquals(listOf("다", "나", "가", "라"), due) // 기록 없음+목표 → 초과 10일 → 목표 미달 → 목표 없음
        val oldest = ContactRules.sort(list, SortMode.OLDEST, today, { it.n }, { it.last }, { it.goal }).map { it.n }
        assertEquals(listOf("다", "라", "나", "가"), oldest)
    }

    @Test fun kakaoNotificationRules() {
        fun info(
            pkg: String = "com.kakao.talk", summary: Boolean = false, style: Boolean = true, category: String? = "msg",
            title: String? = "민수", conv: String? = null, group: Boolean = false, msgs: List<Pair<String?, Long>> = listOf("민수" to 1000L),
        ) = NotificationInfo(pkg, summary, false, category, style, title, conv, group, msgs, 999L)

        assertEquals(listOf("민수"), KakaoNotificationRules.extract(info()).map { it.sender })
        assertTrue(KakaoNotificationRules.extract(info(pkg = "com.other")).isEmpty())
        assertTrue(KakaoNotificationRules.extract(info(summary = true)).isEmpty())
        // 광고·시스템: 대화형도 아니고 msg 분류도 아님
        assertTrue(KakaoNotificationRules.extract(info(style = false, category = null, msgs = emptyList())).isEmpty())
        // 단체방: 메시지별 보낸 사람, 방 이름은 참고
        val g = KakaoNotificationRules.extract(info(title = "가족방", conv = "가족방", group = true, msgs = listOf("아빠" to 1L, "누나" to 2L, null to 3L)))
        assertEquals(listOf("아빠", "누나"), g.map { it.sender })
        assertEquals("가족방", g.first().roomName)
        // 내가 보낸 것만 있으면 기록 없음
        assertTrue(KakaoNotificationRules.extract(info(msgs = listOf(null to 5L))).isEmpty())
        // 대화형이 없고 msg 분류만 있는 1:1 → 제목을 보낸 사람으로, 단체방이면 기록 안 함
        assertEquals(listOf("지우"), KakaoNotificationRules.extract(info(style = false, title = "지우", msgs = emptyList())).map { it.sender })
        assertTrue(KakaoNotificationRules.extract(info(style = false, title = "가족방", group = true, msgs = emptyList())).isEmpty())
    }

    @Test fun callSmsRules() {
        val idx = mapOf("01012345678" to "mom")
        val rows = listOf(
            LogRow("+82 10-1234-5678", 1, LogRow.Kind.CALL_IN, 30),
            LogRow("010-1234-5678", 2, LogRow.Kind.CALL_OUT, 0), // 연결 안 됨
            LogRow("01012345678", 3, LogRow.Kind.CALL_MISSED, 0),
            LogRow("01012345678", 4, LogRow.Kind.SMS_OUT),
            LogRow("01099998888", 5, LogRow.Kind.SMS_IN), // 미등록
            LogRow(null, 6, LogRow.Kind.SMS_IN),
        )
        val m = CallSmsRules.match(rows, idx)
        assertEquals(listOf(Channel.CALL to Direction.IN, Channel.SMS to Direction.OUT), m.map { it.channel to it.direction })
    }

    @Test fun reminderText() {
        assertNull(ReminderText.build(emptyList()))
        assertEquals("엄마(3일 초과)", ReminderText.build(listOf("엄마" to 3L)))
        assertEquals("민수(기록 없음), 아빠(10일 초과), 엄마(3일 초과) 외 1명",
            ReminderText.build(listOf("엄마" to 3L, "아빠" to 10L, "누나" to 1L, "민수" to Long.MAX_VALUE)))
    }
}
