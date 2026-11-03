package com.riad.guitartuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val InTune = Color(0xFF3DDC84)
val Close = Color(0xFFFFC94D)
val Far = Color(0xFFFF5A5F)

private val Colors = darkColorScheme(
    primary = InTune,
    onPrimary = Color(0xFF00210E),
    secondary = Color(0xFF8AB4F8),
    background = Color(0xFF101215),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF262A30),
    onSurfaceVariant = Color(0xFF9AA0A8),
)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
