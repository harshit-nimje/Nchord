package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NothingDarkColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingGlyphWhite,
    primaryContainer = NothingRedContainerDark,
    onPrimaryContainer = NothingRedSoft,
    secondary = NothingGlyphWhite,
    onSecondary = NothingOledBlack,
    secondaryContainer = NothingCarbonElevated,
    onSecondaryContainer = NothingGlyphWhite,
    tertiary = SpotifySignalGreen,
    onTertiary = NothingOledBlack,
    background = NothingOledBlack,
    onBackground = NothingGlyphWhite,
    surface = NothingCarbonSurface,
    onSurface = NothingGlyphWhite,
    surfaceVariant = NothingCarbonCard,
    onSurfaceVariant = NothingTextMutedDark,
    outline = NothingBorderDark,
    outlineVariant = NothingCarbonElevated
)

private val NothingLightColorScheme = lightColorScheme(
    primary = NothingRed,
    onPrimary = NothingPaperSurface,
    primaryContainer = NothingRedContainerLight,
    onPrimaryContainer = NothingInkBlack,
    secondary = NothingInkBlack,
    onSecondary = NothingRawWhite,
    secondaryContainer = NothingPaperElevated,
    onSecondaryContainer = NothingInkBlack,
    tertiary = SpotifySignalGreen,
    onTertiary = NothingInkBlack,
    background = NothingRawWhite,
    onBackground = NothingInkBlack,
    surface = NothingPaperSurface,
    onSurface = NothingInkBlack,
    surfaceVariant = NothingPaperElevated,
    onSurfaceVariant = NothingTextMutedLight,
    outline = NothingBorderLight,
    outlineVariant = NothingPaperElevated
)

val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NothingChordTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NothingDarkColorScheme else NothingLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NothingShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NothingChordTheme(darkTheme = darkTheme, content = content)
}
