package com.rectobook.app

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.rectobook.app.ui.theme.DarkIndustry
import com.rectobook.app.ui.theme.LightIndustry

/**
 * What a page is toned to as it is drawn.
 *
 * A reading setting rather than a theme: the app's own chrome follows the
 * system, and this changes the sheet in front of you. Held as a name rather
 * than as a matrix so what is persisted survives any retuning of the matrices.
 */
enum class PageColorMode { NORMAL, INVERTED, SEPIA }

/**
 * How far the black and white points travel at full strength, as fractions of
 * the full range.
 *
 * A faded scan is not evenly grey: its ink has lifted towards the paper far
 * further than its paper has dropped towards the ink, so the black point has
 * most of the distance to make up and the white point only needs taking off
 * its cream. Moving both equally would blow the text out before the page
 * looked clean.
 */
private const val BLACK_POINT_TRAVEL = 0.38f
private const val WHITE_POINT_TRAVEL = 0.08f

/** Sepia's two ends: what the ink becomes, and what the paper becomes. */
private val SepiaInk = floatArrayOf(0x4A.toFloat(), 0x3B.toFloat(), 0x2A.toFloat())
private val SepiaPaper = floatArrayOf(0xF2.toFloat(), 0xE4.toFloat(), 0xC9.toFloat())

/** Rec. 601 luma, the weights a colour matrix is conventionally written with. */
private const val LUMA_R = 0.299f
private const val LUMA_G = 0.587f
private const val LUMA_B = 0.114f

/**
 * Everything the reader needs to draw a page in one tone: the filter itself,
 * and the colours around the page that have to go with it.
 *
 * The surrounding colours are *computed from the same matrix* rather than
 * picked to match it by eye, so the paper behind a page can never drift from
 * what the filter actually does to a white pixel.
 *
 * Done at draw time rather than in the decoders because it is then one GPU
 * shader over a bitmap already in hand - it applies to DjVu and PDF alike,
 * costs no memory, needs no cache thrown away when it changes, and moves the
 * page on the same frame as the control instead of after a re-decode.
 */
@Immutable
class PageTone(val mode: PageColorMode, val contrast: Float) {

    private val matrix: FloatArray = concat(modeMatrix(mode), contrastMatrix(contrast))

    /**
     * The filter a page is drawn through, or null where the scan is shown as it
     * is and the draw costs nothing at all.
     */
    val filter: ColorFilter? =
        if (mode == PageColorMode.NORMAL && contrast <= 0f) {
            null
        } else {
            ColorFilter.colorMatrix(ColorMatrix(matrix.copyOf()))
        }

    /** A 0f..1f grey put through the same transform the page goes through. */
    fun grey(level: Float): Color {
        val v = level.coerceIn(0f, 1f) * 255f
        fun channel(row: Int): Float {
            val i = row * 5
            val out = matrix[i] * v + matrix[i + 1] * v + matrix[i + 2] * v +
                matrix[i + 3] * 255f + matrix[i + 4]
            return out.coerceIn(0f, 255f) / 255f
        }
        return Color(channel(0), channel(1), channel(2), 1f)
    }

    /** What the page's white becomes: the paper it is drawn on. */
    val paper: Color = grey(1f)

    /** What its black becomes: the page's own edge, and the ring it waits with. */
    val ink: Color = grey(0f)

    /**
     * The desk to lay the page on, or null to keep the app's own.
     *
     * Normal keeps it, because a white page is white in both palettes and the
     * theme's desk is already worked out for each. A toned page brings its own,
     * one step off its paper, so the sheet still reads as laid on something.
     */
    val desk: Color? =
        if (mode == PageColorMode.NORMAL) null else lerp(paper, ink, 0.10f)

    /**
     * The accent the page's own marks are drawn in - links, search hits, the
     * selection wash.
     *
     * The one accent is chosen to read against a ground, and under Inverted the
     * ground has changed ends, so the palette that carries it changes with it.
     */
    val markAccent: Color =
        if (paper.luminance() < 0.5f) DarkIndustry.accent else LightIndustry.accent
}

/**
 * A levels stretch, the same one a scanning tool offers: the black point comes
 * up, the white point comes down, and what lies between them is spread back
 * across the full range.
 */
private fun contrastMatrix(strength: Float): FloatArray {
    val amount = strength.coerceIn(0f, 1f)
    if (amount <= 0f) return identity()

    // Colour matrices work in 0..255, so the points do too.
    val black = amount * BLACK_POINT_TRAVEL * 255f
    val white = 255f - amount * WHITE_POINT_TRAVEL * 255f
    val scale = 255f / (white - black)
    val translate = -black * scale

    return floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        // Alpha is left alone: the paper behind a page is ours to draw, and a
        // page that stopped being opaque would show it.
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun modeMatrix(mode: PageColorMode): FloatArray = when (mode) {
    PageColorMode.NORMAL -> identity()

    PageColorMode.INVERTED -> floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    // Luminance mapped onto an ink-to-paper ramp, rather than the photographic
    // sepia matrix, which only darkens: a scan is read for its ink, and what
    // makes it easier on the eye is warm paper under dark brown text, not a
    // dimmer copy of the same page.
    PageColorMode.SEPIA -> {
        val rows = FloatArray(20)
        for (c in 0 until 3) {
            val span = (SepiaPaper[c] - SepiaInk[c]) / 255f
            rows[c * 5] = span * LUMA_R
            rows[c * 5 + 1] = span * LUMA_G
            rows[c * 5 + 2] = span * LUMA_B
            rows[c * 5 + 4] = SepiaInk[c]
        }
        rows[18] = 1f
        rows
    }
}

private fun identity() = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/**
 * The 4x5 matrix that applies [before] and then [after].
 *
 * Written out rather than left to [ColorMatrix.timesAssign] because the order
 * is the whole of the correctness here: the contrast stretch is tuned for ink
 * on paper, so it has to run in the scan's own polarity, with the mode applied
 * to what it produces. The other way round, a negated page would have its
 * black point pulled up at the end the paper is now at.
 */
private fun concat(after: FloatArray, before: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += after[row * 5 + k] * before[k * 5 + col]
            out[row * 5 + col] = sum
        }
        var offset = after[row * 5 + 4]
        for (k in 0 until 4) offset += after[row * 5 + k] * before[k * 5 + 4]
        out[row * 5 + 4] = offset
    }
    return out
}
