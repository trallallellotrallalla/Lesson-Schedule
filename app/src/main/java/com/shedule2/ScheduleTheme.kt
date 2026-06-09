package com.shedule2

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlueScheduleColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF082B5F),
    secondary = Color(0xFF1976D2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E9FF),
    onSecondaryContainer = Color(0xFF0B2F57),
    tertiary = Color(0xFF0D47A1),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF102A43),
    surface = Color.White,
    onSurface = Color(0xFF102A43),
    surfaceVariant = Color(0xFFD7E9FF),
    onSurfaceVariant = Color(0xFF274B72),
    outline = Color(0xFF90CAF9),
    outlineVariant = Color(0xFFBBDEFB),
    error = Color(0xFFD32F2F),
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF5F1111)
)

@Composable
fun ScheduleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlueScheduleColorScheme,
        content = content
    )
}
