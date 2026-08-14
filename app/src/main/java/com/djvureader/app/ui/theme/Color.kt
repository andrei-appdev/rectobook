package com.djvureader.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The "Industry" palette, taken from the design system's styles.css :root
 * block. Colours the design expresses as `color-mix(… N%, transparent)` are
 * plain alpha here, so use [onSurfaceAlpha] rather than inventing new hues.
 */
val IndustryBackground = Color(0xFFF2F2F3)
val IndustrySurface = Color(0xFFE9E9EA)
val IndustryText = Color(0xFF1D1F20)

val IndustryAccent = Color(0xFF5980A6)
val IndustryAccent100 = Color(0xFFEEF6FF)
val IndustryAccent600 = Color(0xFF597EA3)
val IndustryAccent700 = Color(0xFF416180)
val IndustryAccent800 = Color(0xFF2C455D)

val IndustryNeutral100 = Color(0xFFF5F5F8)
val IndustryNeutral200 = Color(0xFFE7E7EA)
val IndustryNeutral300 = Color(0xFFD4D4D7)
val IndustryNeutral700 = Color(0xFF5D5D60)
val IndustryNeutral900 = Color(0xFF2B2B2D)

/** `--color-divider`: the text colour at 16%. */
val IndustryDivider = IndustryText.copy(alpha = 0.16f)

/** Text at a given opacity, the design's `color-mix(… N%, transparent)`. */
fun onSurfaceAlpha(fraction: Float): Color = IndustryText.copy(alpha = fraction)
