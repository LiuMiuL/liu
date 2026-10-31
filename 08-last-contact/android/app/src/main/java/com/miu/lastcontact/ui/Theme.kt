package com.miu.lastcontact.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
}

// 초록 계열로 통일(기본 보라색 칩·배경이 섞이지 않게 컨테이너·표면 색까지 지정)
private val Light = lightColorScheme(
    primary = Color(0xFF2E7D5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1D2), onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4E6357), secondaryContainer = Color(0xFFD0E8D9), onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF6FBF6), surface = Color(0xFFF6FBF6),
    surfaceVariant = Color(0xFFDCE5DD), surfaceContainer = Color(0xFFEAF0EA),
    surfaceContainerHighest = Color(0xFFDFE5DF), surfaceContainerHigh = Color(0xFFE5EBE5), surfaceContainerLow = Color(0xFFF0F5F0),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FD8B1), onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF115139), onPrimaryContainer = Color(0xFFB4F1D2),
    secondary = Color(0xFFB4CCBD), secondaryContainer = Color(0xFF364B3F), onSecondaryContainer = Color(0xFFD0E8D9),
    tertiary = Color(0xFFF2BE6B),
    background = Color(0xFF0F1511), surface = Color(0xFF0F1511),
    surfaceVariant = Color(0xFF404943), surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHighest = Color(0xFF303632), surfaceContainerHigh = Color(0xFF252B27), surfaceContainerLow = Color(0xFF171D19),
)

@Composable
fun LastContactTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
