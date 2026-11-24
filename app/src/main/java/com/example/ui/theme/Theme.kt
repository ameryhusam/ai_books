package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EduMiniLightColorScheme = lightColorScheme(
    primary = EduGreen,
    onPrimary = EduWhite,
    primaryContainer = EduMint,
    onPrimaryContainer = EduGreenDark,
    secondary = EduPurple,
    onSecondary = EduWhite,
    secondaryContainer = EduLilac,
    onSecondaryContainer = EduPurple,
    tertiary = EduYellow,
    onTertiary = EduInk,
    background = EduBg,
    onBackground = EduInk,
    surface = EduWhite,
    onSurface = EduInk,
    surfaceVariant = EduBg,
    onSurfaceVariant = EduMuted,
    outline = EduLine,
    error = EduRed,
    onError = EduWhite
)

private val EduMiniShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun EduMiniTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EduMiniLightColorScheme,
        typography = EduMiniTypography,
        shapes = EduMiniShapes,
        content = content
    )
}
