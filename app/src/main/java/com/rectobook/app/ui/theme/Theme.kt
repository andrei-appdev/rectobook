package com.rectobook.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * Material's scheme, filled in from the palette.
 *
 * Almost nothing in this app asks Material for a colour - the design is drawn
 * with the palette directly - but the few Material parts that remain, the
 * modal sheets and the progress spinner, take theirs from here.
 */
private fun schemeFor(palette: IndustryPalette) = with(palette) {
    // Started from Material's own light or dark scheme so that the roles this
    // design never names still land the right way up.
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = accent,
        onPrimary = background,
        primaryContainer = accentWash,
        onPrimaryContainer = accentDeepest,
        secondary = accentDeep,
        onSecondary = background,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = mutedText,
        surfaceContainer = surface,
        surfaceContainerHigh = desk,
        outline = text.copy(alpha = 0.16f),
        outlineVariant = text.copy(alpha = 0.16f),
        scrim = scrim,
    )
}

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
 * The design ships light only; [DarkIndustry] is the same design worked out
 * for the other end. Which one is in force follows the system setting, with no
 * switch of its own - a reader that is dark everywhere else should not have to
 * be told again here.
 */
@Composable
fun RectobookTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkIndustry else LightIndustry
    CompositionLocalProvider(LocalIndustryPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = IndustryTypography,
            shapes = SquareShapes,
            content = content,
        )
    }
}
