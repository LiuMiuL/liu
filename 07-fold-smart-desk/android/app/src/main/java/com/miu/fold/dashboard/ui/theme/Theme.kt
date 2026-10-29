package com.miu.fold.dashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.miu.fold.dashboard.settings.ThemeMode

/** 간격 토큰 4·8·12·16·24·32dp */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val touch = 48.dp
}

private val Light = lightColorScheme(
    primary = Color(0xFF3557A6),
    secondary = Color(0xFF4F6070),
    tertiary = Color(0xFF7A5A00),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    secondary = Color(0xFFB7C8DA),
    tertiary = Color(0xFFF0C35A),
)

@Composable
fun FoldDeskTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
