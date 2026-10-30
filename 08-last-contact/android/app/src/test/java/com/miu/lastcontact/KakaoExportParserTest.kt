package com.miu.lastcontact

import com.miu.lastcontact.domain.Direction
import com.miu.lastcontact.domain.ImportPlanner
import com.miu.lastcontact.domain.KakaoExportParser
import com.miu.lastcontact.domain.MappingKind
import com.miu.lastcontact.domain.ParseResult
import com.miu.lastcontact.domain.SenderMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class KakaoExportParserTest {
    private fun ok(text: String) = (KakaoExportParser.parse(text) as ParseResult.Ok).summary

    @Test fun androidBracketFormat() {
        val s = ok(
            """
            엄마 님과 카카오톡 대화
            저장한 날짜 : 2026-10-29 21:10:02

            --------------- 2026년 10월 3일 토요일 ---------------
            [엄마] [오전 12:05] 자니?
            [나] [오전 12:06] 아직요
            여러 줄 메시지의 둘째 줄
            --------------- 2026년 10월 20일 화요일 ---------------
            [엄마] [오후 12:30] 점심 먹었어?
            [엄마] [오후 9:41] 사진
            """.trimIndent(),
        )
        assertEquals("엄마", s.chatTitle)
        assertEquals(4, s.messageCount)
        assertEquals(LocalDate.of(2026, 10, 3), s.firstDay)
        assertEquals(LocalDate.of(2026, 10, 20), s.lastDay)
        val mom = s.senders.first { it.name == "엄마" }
        assertEquals(3, mom.count)
        assertEquals(LocalDateTime.of(2026, 10, 20, 21, 41), mom.last)
        // 오전 12시 = 0시, 오후 12시 = 12시
        assertEquals(setOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 20)), mom.days)
        assertEquals(LocalDateTime.of(2026, 10, 3, 0, 6), s.senders.first { it.name == "나" }.last)
    }

    @Test fun olderAndroidKoreanFullFormat() {
        val s = ok(
            """
            민수 님과 카카오톡 대화
            2025년 12월 31일 수요일
            2025년 12월 31일 오후 11:59, 민수 : 새해 복 많이 받아
            2026년 1월 1일 오전 12:01, 지우 : 너도!
            2026년 1월 1일 오전 12:02, 민수님이 나갔습니다.
            """.trimIndent(),
        )
        assertEquals(2, s.messageCount) // 나갔습니다 안내는 ' : '가 없어 제외
        assertEquals(LocalDate.of(2025, 12, 31), s.firstDay)
        assertEquals(LocalDate.of(2026, 1, 1), s.lastDay)
    }

    @Test fun pcAndIosDotFormats12hAnd24h() {
        val s = ok(
            """
            가족방 님과 카카오톡 대화
            2026. 9. 1. 오후 3:12, 아빠 : 다들 잘 지내?
            2026. 9. 2. 15:40, 누나 : 응
            2026.9.3. 오전 8:00, 아빠 : 출근
            """.trimIndent(),
        )
        assertEquals(3, s.messageCount)
        assertEquals(LocalDateTime.of(2026, 9, 3, 8, 0), s.senders.first { it.name == "아빠" }.last)
        assertEquals(LocalDateTime.of(2026, 9, 2, 15, 40), s.senders.first { it.name == "누나" }.last) // 24시간제
        assertEquals(setOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)), s.senders.first { it.name == "아빠" }.days) // 오후 3:12 → 9/1
        assertEquals(LocalDate.of(2026, 9, 3), s.lastDay)
        assertEquals(setOf("아빠", "누나"), s.senders.map { it.name }.toSet())
    }

    @Test fun isoFormat() {
        val s = ok("2026-02-03 07:05:00, 친구 : hi\n2026-02-04 23:00, 나 : yo")
        assertEquals(LocalDate.of(2026, 2, 4), s.lastDay)
        assertEquals(2, s.senders.size)
    }

    @Test fun rejectsNonKakaoFiles() {
        assertTrue(KakaoExportParser.parse("그냥 메모 파일입니다\n아무 형식 없음") is ParseResult.Error)
        assertTrue(KakaoExportParser.parse("") is ParseResult.Error)
        // 날짜 헤더 없이 대괄호 줄만 있으면 날짜를 알 수 없어 무시
        assertTrue(KakaoExportParser.parse("[홍길동] [오후 3:12] 안녕") is ParseResult.Error)
    }

    @Test fun ignoresOverlongLinesAndInvalidDates() {
        val s = ok("2026년 2월 30일 오후 3:12, 가짜 : 날짜 오류\n" + "x".repeat(30_000) + "\n2026년 2월 28일 오후 3:12, 진짜 : 정상")
        assertEquals(listOf("진짜"), s.senders.map { it.name })
    }

    @Test fun planOneToOneUsesAllDaysWithDirection() {
        val s = ok(
            """
            --------------- 2026년 10월 1일 목요일 ---------------
            [엄마] [오후 1:00] a
            --------------- 2026년 10월 2일 금요일 ---------------
            [나] [오후 1:00] b
            --------------- 2026년 10월 3일 토요일 ---------------
            [엄마] [오후 1:00] c
            [나] [오후 1:01] d
            """.trimIndent(),
        )
        val plan = ImportPlanner.plan(s, listOf(SenderMapping("나", MappingKind.ME), SenderMapping("엄마", MappingKind.PERSON, "p-mom")))
        assertEquals(listOf(Direction.IN, Direction.OUT, Direction.BOTH), plan.map { it.direction })
        assertTrue(plan.all { it.personKey == "p-mom" })
    }

    @Test fun planGroupCountsOnlyTheirOwnDays() {
        val s = ok(
            """
            2026. 9. 1. 오후 3:12, 아빠 : a
            2026. 9. 2. 오후 3:12, 누나 : b
            2026. 9. 3. 오후 3:12, 나 : c
            2026. 9. 4. 오후 3:12, 광고봇 : d
            """.trimIndent(),
        )
        val plan = ImportPlanner.plan(
            s,
            listOf(
                SenderMapping("나", MappingKind.ME), SenderMapping("아빠", MappingKind.PERSON, "p-dad"),
                SenderMapping("누나", MappingKind.NEW_PERSON), SenderMapping("광고봇", MappingKind.IGNORE),
            ),
        )
        assertEquals(listOf("new:누나" to LocalDate.of(2026, 9, 2), "p-dad" to LocalDate.of(2026, 9, 1)), plan.map { it.personKey to it.day })
        assertTrue(plan.all { it.direction == Direction.IN })
    }

    @Test fun planMergesTwoNamesForSamePerson() {
        val s = ok("2026. 9. 1. 오후 3:12, 엄마 : a\n2026. 9. 1. 오후 4:00, 엄마(새폰) : b\n2026. 9. 2. 오후 1:00, 아빠 : c")
        val plan = ImportPlanner.plan(
            s,
            listOf(SenderMapping("엄마", MappingKind.PERSON, "m"), SenderMapping("엄마(새폰)", MappingKind.PERSON, "m"), SenderMapping("아빠", MappingKind.PERSON, "d")),
        )
        assertEquals(1, plan.count { it.personKey == "m" })
    }
}
