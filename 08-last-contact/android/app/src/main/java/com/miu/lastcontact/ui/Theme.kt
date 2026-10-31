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

private val Light = lightColorScheme(primary = Color(0xFF2E7D5B), secondary = Color(0xFF4E6357), tertiary = Color(0xFF8A5A00))
private val Dark = darkColorScheme(primary = Color(0xFF8FD8B1), secondary = Color(0xFFB4CCBD), tertiary = Color(0xFFF2BE6B))

@Composable
fun LastContactTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
