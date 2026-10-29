package com.miu.fold.deals.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 간격 토큰(dp). */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val Light = lightColorScheme(
    primary = Color(0xFF1F5FAD),
    secondary = Color(0xFF52606D),
    tertiary = Color(0xFF8A4B00),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    secondary = Color(0xFFB9C7D6),
    tertiary = Color(0xFFFFB870),
)

@Composable
fun DealTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
