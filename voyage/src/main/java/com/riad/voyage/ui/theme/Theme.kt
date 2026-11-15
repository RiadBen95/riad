package com.riad.voyage.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.riad.voyage.R

/** Couleurs de la marque Envolée : les mêmes que la page de présentation. */
val BrandNavy = Color(0xFF173F94)
val BrandNight = Color(0xFF071A3D)
val BrandCoral = Color(0xFFFF7A59)
private val OnCoral = Color(0xFF2A0E05)

/** Couleur des « meilleurs prix ». */
val Deal = Color(0xFF1B9E55)
val DealDark = Color(0xFF4CD08A)

/** Titres en Plus Jakarta Sans (comme la page), texte en Inter. */
val Display = FontFamily(Font(R.font.jakarta_bold, FontWeight.Bold), Font(R.font.jakarta_extrabold, FontWeight.ExtraBold))
private val Body = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold),
)

// Clair : fond #F5F7FB, cartes blanches, texte bleu nuit, accents bleu marine.
private val Light = lightColorScheme(
    primary = BrandNavy, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = BrandNight,
    secondary = Color(0xFFD9502C), onSecondary = Color.White,   // corail foncé du logo : lisible sur fond blanc
    secondaryContainer = Color(0xFFDCE6FF), onSecondaryContainer = BrandNight,
    tertiary = Color(0xFFC2410C), tertiaryContainer = Color(0xFFFFE8E0), onTertiaryContainer = Color(0xFF3B1306),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF0F1B33),
    surface = Color.White, onSurface = Color(0xFF0F1B33),
    surfaceVariant = Color(0xFFEDF1F8), onSurfaceVariant = Color(0xFF55627A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
    outline = Color(0xFFB9C3D6), outlineVariant = Color(0xFFDDE3EE), surfaceTint = Color.White,
)

// Sombre : bleu nuit #0B1426, cartes #121E36, accents corail.
private val Dark = darkColorScheme(
    primary = BrandCoral, onPrimary = OnCoral,
    primaryContainer = BrandNavy, onPrimaryContainer = Color.White,
    secondary = BrandCoral, onSecondary = OnCoral,
    secondaryContainer = Color(0xFF1C2B4D), onSecondaryContainer = Color(0xFFE6ECF7),
    tertiary = Color(0xFFFFB199), tertiaryContainer = Color(0xFF1C2B4D), onTertiaryContainer = Color(0xFFE6ECF7),
    background = Color(0xFF0B1426), onBackground = Color(0xFFE6ECF7),
    surface = Color(0xFF121E36), onSurface = Color(0xFFE6ECF7),
    surfaceVariant = Color(0xFF1A2846), onSurfaceVariant = Color(0xFFA1AEC6),
    surfaceContainerLowest = Color(0xFF0B1426), surfaceContainerLow = Color(0xFF101B31), surfaceContainer = Color(0xFF121E36),
    surfaceContainerHigh = Color(0xFF16233F), surfaceContainerHighest = Color(0xFF16233F),
    outline = Color(0xFF3A4A6B), outlineVariant = Color(0xFF22304D), surfaceTint = Color(0xFF121E36),
)

private val Base = Typography()
private fun TextStyle.display() = copy(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)

private val Type = Typography(
    displayLarge = Base.displayLarge.display(), displayMedium = Base.displayMedium.display(), displaySmall = Base.displaySmall.display(),
    headlineLarge = Base.headlineLarge.display(), headlineMedium = Base.headlineMedium.display(), headlineSmall = Base.headlineSmall.display(),
    titleLarge = Base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    titleSmall = Base.titleSmall.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Body), bodyMedium = Base.bodyMedium.copy(fontFamily = Body),
    bodySmall = Base.bodySmall.copy(fontFamily = Body),
    labelLarge = Base.labelLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
)

/** Arrondis de la page : cartes 18, grands blocs 24. */
private val Corners = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
)

@Composable
fun VoyageTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Corners, content = content)
}

/** Bouton d'action principal : corail, comme « Télécharger » sur la page. */
@Composable
fun ctaColors() = ButtonDefaults.buttonColors(containerColor = BrandCoral, contentColor = OnCoral)

/** Bandeau bleu marine → nuit de la marque ; son contenu s'affiche toujours en thème sombre (texte clair, accents corail). */
@Composable
fun BrandBand(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    VoyageTheme(dark = true) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(modifier.background(Brush.verticalGradient(listOf(BrandNavy, BrandNight))), content = content)
        }
    }
}

@Composable
fun dealColor(): Color = if (isSystemInDarkTheme()) DealDark else Deal
