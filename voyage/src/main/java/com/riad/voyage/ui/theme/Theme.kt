package com.riad.voyage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Couleur des « meilleurs prix ». */
val Deal = Color(0xFF1B9E55)
val DealDark = Color(0xFF4CD08A)

private val Light = lightColorScheme(
    primary = Color(0xFF1E6FD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E6FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFFFF7A3D),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF151A21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A21),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF5A6472),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB8FF),
    onPrimary = Color(0xFF002F66),
    primaryContainer = Color(0xFF16457F),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = Color(0xFFFFA273),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE3E7ED),
    surface = Color(0xFF181E25),
    onSurface = Color(0xFFE3E7ED),
    surfaceVariant = Color(0xFF242B34),
    onSurfaceVariant = Color(0xFFA2ABB7),
)

@Composable
fun VoyageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@Composable
fun dealColor(): Color = if (isSystemInDarkTheme()) DealDark else Deal
