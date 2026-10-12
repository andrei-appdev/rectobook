package com.rectobook.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours the app draws with, as roles rather than as fixed values.
 *
 * The design ships one palette, for light. The dark one is not an inversion of
 * it - a flat design leans on a handful of very close greys, and negating those
 * gives muddy mid-tones and an accent too dim to read. It is built instead by
 * keeping each colour's *job*: the ground the app sits on, the tone a card
 * sits on, the ink, and one accent that has to carry against the ground either
 * way.
 */
@Immutable
data class IndustryPalette(
    /** The app's ground. */
    val background: Color,
    /** One step off the ground: inputs, and the panels that sit on it. */
    val surface: Color,
    /** Ink. Every muted tone in the app is this at some opacity. */
    val text: Color,
    /** The one accent, which must read against [background]. */
    val accent: Color,
    /** A wash of accent, for something tinted rather than filled. */
    val accentWash: Color,
    /** Accent one step towards the ink, for small type on the ground. */
    val accentDeep: Color,
    /** Accent two steps, for type on [accentWash]. */
    val accentDeepest: Color,
    /** The desk a page is laid on, always darker than the page itself. */
    val desk: Color,
    /** Secondary type that is not simply ink at an opacity. */
    val mutedText: Color,
    /** What a modal darkens the screen behind it with. */
    val scrim: Color,
    val isDark: Boolean,
)

/** The design system's own palette, from its `styles.css` `:root` block. */
val LightIndustry = IndustryPalette(
    background = Color(0xFFF2F2F3),
    surface = Color(0xFFE9E9EA),
    text = Color(0xFF1D1F20),
    accent = Color(0xFF5980A6),
    accentWash = Color(0xFFEEF6FF),
    accentDeep = Color(0xFF416180),
    accentDeepest = Color(0xFF2C455D),
    desk = Color(0xFFE7E7EA),
    mutedText = Color(0xFF5D5D60),
    scrim = Color(0xFF2B2B2D),
    isDark = false,
)

/**
 * The same design after dark.
 *
 * The accent is lifted rather than kept: 0xFF5980A6 is chosen to sit against
 * near-white and goes muddy against near-black, so the dark palette takes the
 * same hue several steps lighter. Its deeper steps run the other way too -
 * "deep" means towards the ink, and the ink has changed ends.
 *
 * The desk stays darker than the ground rather than lighter, because a page is
 * white in both palettes and has to read as a sheet laid on something.
 */
val DarkIndustry = IndustryPalette(
    background = Color(0xFF1B1C1D),
    surface = Color(0xFF232426),
    text = Color(0xFFE8E8EA),
    accent = Color(0xFF7FA6CC),
    accentWash = Color(0xFF21303D),
    accentDeep = Color(0xFF9CC0E0),
    accentDeepest = Color(0xFFBDD6EC),
    desk = Color(0xFF121314),
    mutedText = Color(0xFFA8A8AC),
    scrim = Color(0xFF000000),
    isDark = true,
)

val LocalIndustryPalette = staticCompositionLocalOf { LightIndustry }

/**
 * The palette in force. Prefer the named colours below; this is for the few
 * places that need a colour outside a composable scope and must read one out
 * first, and for anything that has to know which way round the app is.
 */
val Industry: IndustryPalette
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current

val IndustryBackground: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.background

val IndustrySurface: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.surface

val IndustryText: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.text

val IndustryAccent: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.accent

val IndustryAccent100: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.accentWash

val IndustryAccent700: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.accentDeep

val IndustryAccent800: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.accentDeepest

/** The backdrop the reader lays pages on. */
val IndustryNeutral200: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.desk

val IndustryNeutral700: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.mutedText

val IndustryNeutral900: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.scrim

/** `--color-divider`: the ink at 16%, whichever end of the ramp the ink is. */
val IndustryDivider: Color
    @Composable @ReadOnlyComposable get() = LocalIndustryPalette.current.text.copy(alpha = 0.16f)

/** Ink at a given opacity, the design's `color-mix(… N%, transparent)`. */
@Composable
@ReadOnlyComposable
fun onSurfaceAlpha(fraction: Float): Color =
    LocalIndustryPalette.current.text.copy(alpha = fraction)

/** As [onSurfaceAlpha], for a palette already read out of the composition. */
fun IndustryPalette.ink(fraction: Float): Color = text.copy(alpha = fraction)
