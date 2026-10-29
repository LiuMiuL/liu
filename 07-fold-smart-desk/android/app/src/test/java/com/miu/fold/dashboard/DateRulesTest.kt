package com.miu.fold.dashboard

import com.miu.fold.dashboard.calendar.EventInputResult
import com.miu.fold.dashboard.calendar.EventRules
import com.miu.fold.dashboard.core.FakeClock
import com.miu.fold.dashboard.notes.NoteRules
import com.miu.fold.dashboard.todos.TodoBucket
import com.miu.fold.dashboard.todos.TodoFilter
import com.miu.fold.dashboard.todos.TodoRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DateRulesTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val la = ZoneId.of("America/Los_Angeles")

    @Test fun t16_todayRecomputedAfterMidnight() {
        val c = FakeClock(zoneId = seoul)
        // 2027-01-15 23:59 KST
        c.now = LocalDate.of(2027, 1, 15).atTime(23, 59).atZone(seoul).toInstant().toEpochMilli()
        val due = LocalDate.of(2027, 1, 15)
        assertEquals(TodoBucket.TODAY, TodoRules.bucket(due, false, c.today()))
        c.advance(2 * 60_000)
        assertEquals(TodoBucket.OVERDUE, TodoRules.bucket(due, false, c.today()))
    }

    @Test fun dateOnlyTodoIsNotShiftedByTimezone() {
        val c = FakeClock(zoneId = seoul)
        c.now = LocalDate.of(2027, 1, 15).atTime(1, 0).atZone(seoul).toInstant().toEpochMilli()
        val due = LocalDate.of(2027, 1, 15)
        assertEquals(TodoBucket.TODAY, TodoRules.bucket(due, false, c.today()))
        // 같은 순간에 LA로 이동하면 기기 날짜는 1/14, 마감일 값은 그대로 1/15
        c.zoneId = la
        assertEquals(TodoBucket.UPCOMING, TodoRules.bucket(due, false, c.today()))
        assertEquals("2027-01-15", due.toString())
    }

    @Test fun bucketsAndFilters() {
        val today = LocalDate.of(2027, 3, 1)
        assertEquals(TodoBucket.DONE, TodoRules.bucket(today, true, today))
        assertEquals(TodoBucket.NO_DATE, TodoRules.bucket(null, false, today))
        assertTrue(TodoRules.matches(TodoFilter.TODAY, TodoBucket.OVERDUE))
        assertFalse(TodoRules.matches(TodoFilter.TODAY, TodoBucket.UPCOMING))
        assertTrue(TodoRules.onHomeCard(TodoBucket.NO_DATE))
        assertFalse(TodoRules.onHomeCard(TodoBucket.DONE))
        assertEquals(null, TodoRules.parseDate("2027-13-40"))
    }

    @Test fun allDayEventStaysOnDateAcrossZones() {
        val r = EventRules.build(true, LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 5), "", "", seoul) as EventInputResult.Ok
        assertTrue(EventRules.occursOn(r.span, LocalDate.of(2027, 5, 5), seoul))
        assertTrue(EventRules.occursOn(r.span, LocalDate.of(2027, 5, 5), la))
        assertFalse(EventRules.occursOn(r.span, LocalDate.of(2027, 5, 4), la))
        assertEquals(null, r.zoneId)
    }

    @Test fun timedEventUsesInstant() {
        val r = EventRules.build(false, LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 5), "09:00", "10:30", seoul) as EventInputResult.Ok
        assertEquals("Asia/Seoul", r.zoneId)
        assertTrue(EventRules.occursOn(r.span, LocalDate.of(2027, 5, 5), seoul))
        // 서울 5/5 09:00 = LA 5/4 17:00
        assertTrue(EventRules.occursOn(r.span, LocalDate.of(2027, 5, 4), la))
        assertFalse(EventRules.occursOn(r.span, LocalDate.of(2027, 5, 5), la))
    }

    @Test fun endBeforeStartRejected() {
        assertTrue(EventRules.build(false, LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 5), "10:00", "09:00", seoul) is EventInputResult.Error)
        assertTrue(EventRules.build(true, LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 4), "", "", seoul) is EventInputResult.Error)
        assertTrue(EventRules.build(false, LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 5), "25:00", "26:00", seoul) is EventInputResult.Error)
        // 자정을 넘기는 일정은 종료 날짜를 다음 날로 지정하면 허용
        assertTrue(EventRules.build(false, LocalDate.of(2027, 5, 5), LocalDate.of(2027, 5, 6), "23:00", "01:00", seoul) is EventInputResult.Ok)
    }

    @Test fun noteTitleSuggestion() {
        assertEquals("첫 줄", NoteRules.suggestedTitle("", "\n  첫 줄 \n둘째"))
        assertEquals("직접 제목", NoteRules.suggestedTitle(" 직접 제목 ", "본문"))
        assertEquals("제목 없음", NoteRules.suggestedTitle("", "  "))
        assertEquals(NoteRules.TITLE_MAX + 1, NoteRules.suggestedTitle("", "가".repeat(100)).length)
    }
}
