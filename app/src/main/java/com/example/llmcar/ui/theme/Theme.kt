package com.example.llmcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    secondary = Color(0xFF4CAF50),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFE0E0E0),
    background = Color(0xFF1A1A2E),
    onBackground = Color(0xFFE0E0E0),
    error = Color(0xFFF44336)
)

@Composable
fun LlmCarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}