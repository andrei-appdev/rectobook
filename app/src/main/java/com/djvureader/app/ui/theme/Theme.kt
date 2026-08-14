package com.djvureader.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IndustryColors = lightColorScheme(
    primary = IndustryAccent,
    onPrimary = IndustryBackground,
    primaryContainer = IndustryAccent100,
    onPrimaryContainer = IndustryAccent800,
    secondary = IndustryAccent700,
    onSecondary = IndustryBackground,
    background = IndustryBackground,
    onBackground = IndustryText,
    surface = IndustryBackground,
    onSurface = IndustryText,
    surfaceVariant = IndustrySurface,
    onSurfaceVariant = IndustryNeutral700,
    surfaceContainer = IndustrySurface,
    surfaceContainerHigh = IndustryNeutral200,
    outline = IndustryDivider,
    outlineVariant = IndustryDivider,
    scrim = IndustryNeutral900,
)

/**
 * Nothing in this design is rounded: styles.css squares off every card,
 * button, input, tag and dialog explicitly.
 */
private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

/**
 * The design is drawn light-only, so this does not follow the system dark
 * setting. A dark variant would need its own palette rather than an inversion:
 * the accent and the paper-white page surfaces do not survive one.
 */
@Composable
fun DjvuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustryColors,
        typography = IndustryTypography,
        shapes = SquareShapes,
        content = content,
    )
}
