package com.miu.fold.dashboard.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

object MonthRules {
    /** 일요일 시작 7열 달력 칸. 앞뒤 빈칸은 null. 항상 7의 배수. */
    fun cells(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7 // 일=0, 월=1 … 토=6
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val total = ((lead + days.size + 6) / 7) * 7
        return List(lead) { null } + days + List(total - lead - days.size) { null }
    }
}

private val weekLabels = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * 월 달력. 일정이 있는 날은 점(•)과 '일정 있음' 설명으로 표시한다(색만으로 구분하지 않음).
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    hasEvents: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달") }
            Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달") }
        }
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEachIndexed { i, w ->
                Text(
                    w, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                    color = when (i) { 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                )
            }
        }
        MonthRules.cells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).aspectRatio(1f, matchHeightConstraintsFirst = false), contentAlignment = Alignment.Center) {
                        if (d != null) DayCell(d, d == selected, d == today, hasEvents(d), onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, isSelected: Boolean, isToday: Boolean, events: Boolean, onSelect: (LocalDate) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val desc = buildString {
        append("${d.monthValue}월 ${d.dayOfMonth}일")
        if (isToday) append(", 오늘")
        if (events) append(", 일정 있음")
    }
    Box(
        Modifier.padding(2.dp).fillMaxWidth().aspectRatio(1f)
            .then(if (isSelected) Modifier.background(cs.primaryContainer, CircleShape) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, cs.primary, CircleShape) else Modifier)
            .clickable { onSelect(d) }
            .semantics { contentDescription = desc; selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${d.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = if (d.dayOfWeek == DayOfWeek.SUNDAY) cs.error else cs.onSurface,
            )
            Text(if (events) "•" else " ", style = MaterialTheme.typography.labelSmall, color = cs.primary)
        }
    }
}
