package com.cartavu.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cartavu.app.ui.theme.IndustryAccent
import com.cartavu.app.ui.theme.IndustryBackground
import com.cartavu.app.ui.theme.TabularNums
import com.cartavu.app.ui.theme.onSurfaceAlpha
import kotlin.math.roundToInt

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

/**
 * The filter a page is drawn through, or null at [strength] 0 - where the scan
 * is shown as it is and the draw costs nothing at all.
 *
 * This is a levels stretch, the same one a scanning tool offers: the black
 * point comes up, the white point comes down, and what lies between them is
 * spread back across the full range. Done at draw time rather than in the
 * decoders because it is then one GPU shader over a bitmap already in hand -
 * it applies to DjVu and PDF alike, costs no memory, and moves the page on the
 * same frame as the control instead of after a re-decode.
 */
internal fun pageContrastFilter(strength: Float): ColorFilter? {
    val amount = strength.coerceIn(0f, 1f)
    if (amount <= 0f) return null

    // Colour matrices work in 0..255, so the points do too.
    val black = amount * BLACK_POINT_TRAVEL * 255f
    val white = 255f - amount * WHITE_POINT_TRAVEL * 255f
    val scale = 255f / (white - black)
    val translate = -black * scale

    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                // Alpha is left alone: the paper behind a page is ours to
                // draw, and a page that stopped being opaque would show it.
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/**
 * The contrast panel: one control, over the page it is adjusting.
 *
 * A sheet rather than a screen precisely so the page stays in view - the
 * setting is judged against the scan rather than described in words, and there
 * is nothing to decide once you can watch it take effect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContrastSheet(
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = IndustryBackground,
        dragHandle = null,
        // The page is the whole point of this panel, so nothing is drawn over
        // it - the other sheets dim it because they have taken the reader away
        // from it, and this one has not.
        scrimColor = Color.Transparent,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Hairline()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = upperStringResource(R.string.reader_contrast),
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.06.em),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (contrast <= 0f) {
                            stringResource(R.string.contrast_as_scanned)
                        } else {
                            stringResource(R.string.contrast_amount, (contrast * 100).roundToInt())
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 0.04.em,
                            fontFeatureSettings = TabularNums,
                        ),
                        textAlign = TextAlign.End,
                    )
                }
                ContrastSlider(
                    value = contrast,
                    onValueChange = onContrastChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The slider's dimensions, the same caliper mark the page slider rides. */
private val TrackThickness = 2.dp
private val ThumbWidth = 4.dp
private val ThumbHeight = 16.dp

/**
 * A 0f..1f slider, drawn the way this design draws a range: a 2dp rule with a
 * caliper mark on it, nothing rounded.
 *
 * Live, as the page slider is - the value is judged by watching the page
 * behind the sheet change, which only works if it changes while the finger is
 * still down.
 */
@Composable
private fun ContrastSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read here rather than in the draw lambda, which is not a composable.
    val trackColor = onSurfaceAlpha(0.14f)
    val markColor = IndustryAccent
    val fraction = value.coerceIn(0f, 1f)
    val label = stringResource(R.string.reader_contrast)

    Canvas(
        modifier = modifier
            .height(28.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(current = fraction, range = 0f..1f)
                setProgress { target ->
                    onValueChange(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                // Value under x, in this slider's own coordinates. Inverse of
                // the centre the draw below computes.
                fun valueAt(x: Float): Float {
                    val span = size.width - ThumbWidth.toPx()
                    if (span <= 0f) return 0f
                    return ((x - ThumbWidth.toPx() / 2f) / span).coerceIn(0f, 1f)
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Claimed, or the sheet reads the press as a drag of
                    // itself and slides away from under the finger.
                    down.consume()
                    onValueChange(valueAt(down.position.x))
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        onValueChange(valueAt(change.position.x))
                    }
                }
            },
    ) {
        val track = TrackThickness.toPx()
        val thumbW = ThumbWidth.toPx()
        val thumbH = ThumbHeight.toPx()
        val trackTop = (size.height - track) / 2f
        val centre = thumbW / 2f + (size.width - thumbW) * fraction

        drawRect(
            color = trackColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, track),
        )
        drawRect(
            color = markColor,
            topLeft = Offset(0f, trackTop),
            size = Size(centre, track),
        )
        drawRect(
            color = markColor,
            topLeft = Offset(centre - thumbW / 2f, (size.height - thumbH) / 2f),
            size = Size(thumbW, thumbH),
        )
    }
}
