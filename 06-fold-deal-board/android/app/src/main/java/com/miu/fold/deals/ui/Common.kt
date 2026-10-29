package com.miu.fold.deals.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** UTC로 저장한 시각을 기기 시간대로 표시한다. */
fun formatTime(ms: Long): String = dateTimeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** 라벨-값 한 줄. 값이 없으면 "미확인"을 기울임꼴로 표시해 색상 없이도 구분한다. */
@Composable
fun FieldRow(label: String, value: String?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(label, Modifier.widthIn(min = 88.dp, max = 120.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value.isNullOrBlank()) {
            Text("? 미확인", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.tertiary)
        } else {
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = Space.l, bottom = Space.xs), style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
}
