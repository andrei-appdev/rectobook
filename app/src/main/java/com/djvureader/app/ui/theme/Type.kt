package com.djvureader.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.djvureader.app.R

/**
 * Body face.
 *
 * The design specifies Barlow, which has no Cyrillic at all - and Android falls
 * back a glyph at a time, so a Russian document title came out half in Barlow
 * and half in the system font. These families are named for their role rather
 * than their face, since the face is what had to change.
 */
val BodyFont = FontFamily(
    Font(R.font.fira_sans_regular, FontWeight.Normal),
    Font(R.font.fira_sans_medium, FontWeight.Medium),
    Font(R.font.fira_sans_bold, FontWeight.Bold),
)

/** Heading face - the design sets every heading and button in this. */
val HeadingFont = FontFamily(
    Font(R.font.fira_sans_condensed_regular, FontWeight.Normal),
    Font(R.font.fira_sans_condensed_semibold, FontWeight.SemiBold),
)

/**
 * Headings and controls are condensed, running text is not. Sizes follow the
 * design's own scale; the design states many sizes inline, so those are set at
 * the call site rather than guessed at here.
 */
val IndustryTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 25.sp, // the stylesheet's 1.12 heading leading
        letterSpacing = (-0.01).em,
    ),
    titleLarge = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 19.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
    ),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontFamily = HeadingFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.06.em,
    ),
    labelMedium = TextStyle(fontFamily = BodyFont, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontSize = 10.sp, letterSpacing = 0.06.em),
)

/** The design's uppercase, wide-tracked section label (`h6`). */
val SectionLabel = TextStyle(
    fontFamily = HeadingFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    letterSpacing = 0.08.em,
)

/**
 * Page counts and percentages use tabular figures so the numbers do not jitter
 * as they change. Pass as `fontFeatureSettings`.
 */
const val TabularNums = "tnum"
