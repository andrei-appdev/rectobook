package com.djvureader.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.djvureader.app.ui.theme.BarlowCondensed
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.IndustryDivider
import com.djvureader.app.ui.theme.IndustryNeutral900
import com.djvureader.app.ui.theme.IndustryText
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.onSurfaceAlpha

/**
 * One of the design's icons, described in the 24-unit space of its SVG
 * `viewBox`. [unit] is one of those units in pixels, so a path can be written
 * with the same numbers the design uses.
 */
internal typealias Icon24 = Path.(unit: Float) -> Unit

internal val BackIcon: Icon24 = { u ->
    moveTo(19 * u, 12 * u); lineTo(5 * u, 12 * u)
    moveTo(5 * u, 12 * u); lineTo(11 * u, 6 * u)
    moveTo(5 * u, 12 * u); lineTo(11 * u, 18 * u)
}

internal val FolderIcon: Icon24 = { u ->
    // Bounding box of the 1.5-unit corner arc centred on (cx, cy).
    fun corner(cx: Float, cy: Float) =
        Rect(cx * u - 1.5f * u, cy * u - 1.5f * u, cx * u + 1.5f * u, cy * u + 1.5f * u)

    moveTo(3 * u, 7.5f * u)
    arcTo(corner(4.5f, 7.5f), 180f, 90f, false)
    lineTo(8.5f * u, 6 * u)
    lineTo(10.5f * u, 8.5f * u)
    lineTo(17.5f * u, 8.5f * u)
    arcTo(corner(17.5f, 10f), 270f, 90f, false)
    lineTo(19 * u, 17.5f * u)
    arcTo(corner(17.5f, 17.5f), 0f, 90f, false)
    lineTo(4.5f * u, 19 * u)
    arcTo(corner(4.5f, 17.5f), 90f, 90f, false)
    close()
}

/** The design's go-to-page mark: ruled lines crossed by two slanted strokes. */
internal val PagesIcon: Icon24 = { u ->
    moveTo(4 * u, 9 * u); lineTo(20 * u, 9 * u)
    moveTo(4 * u, 15 * u); lineTo(20 * u, 15 * u)
    moveTo(10 * u, 4 * u); lineTo(8 * u, 20 * u)
    moveTo(16 * u, 4 * u); lineTo(14 * u, 20 * u)
}

internal val MinusIcon: Icon24 = { u ->
    moveTo(5 * u, 12 * u); lineTo(19 * u, 12 * u)
}

internal val PlusIcon: Icon24 = { u ->
    moveTo(12 * u, 5 * u); lineTo(12 * u, 19 * u)
    moveTo(5 * u, 12 * u); lineTo(19 * u, 12 * u)
}

/** Draws an [Icon24] as the design does: stroked, 1.5 units wide, unfilled. */
@Composable
internal fun StrokeIcon(
    icon: Icon24,
    modifier: Modifier = Modifier,
    size: Dp = 19.dp,
    color: Color = IndustryText,
) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.width / 24f
        drawPath(
            path = Path().apply { icon(unit) },
            color = color,
            style = Stroke(width = 1.5f * unit),
        )
    }
}

/** The design's `.btn-icon`: a 36dp square with no border of its own. */
@Composable
private fun IconAction(
    icon: Icon24,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StrokeIcon(icon)
    }
}

/**
 * How far off screen a hidden bar sits, as a fraction of its own height. The
 * design's `translateY(±102%)` clears the bar plus its hairline border.
 */
private const val HIDDEN_SHIFT = 1.02f

/**
 * Reader top bar: back to the library, the document's identity, and room for
 * the search and thumbnail actions that arrive with later steps.
 *
 * [hidden] runs 0..1 and is animated by the caller, so the bar slides rather
 * than appearing.
 */
@Composable
fun ReaderTopBar(
    title: String,
    pageCount: Int,
    hidden: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -HIDDEN_SHIFT * size.height * hidden }
            .background(IndustryBackground.copy(alpha = 0.94f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconAction(BackIcon, "Back to library", onBack)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "DJVU · $pageCount PP",
                    style = MaterialTheme.typography.labelSmall
                        .copy(fontFeatureSettings = TabularNums),
                    color = onSurfaceAlpha(0.45f),
                )
            }
        }
        Hairline()
    }
}

/**
 * Reader bottom bar: the page indicator, how far through the document it sits,
 * and the zoom segment.
 */
@Composable
fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    zoomLabel: String,
    canZoomOut: Boolean,
    canZoomIn: Boolean,
    hidden: Float,
    onGoToPage: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = HIDDEN_SHIFT * size.height * hidden }
            .background(IndustryBackground.copy(alpha = 0.94f)),
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // `.btn`: square, hairline-bordered, transparent.
            Row(
                modifier = Modifier
                    .border(1.dp, IndustryDivider)
                    .clickable(onClick = onGoToPage)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StrokeIcon(PagesIcon, size = 15.dp)
                Text(
                    text = "$currentPage / $pageCount",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 0.04.em,
                        fontFeatureSettings = TabularNums,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(onSurfaceAlpha(0.14f)),
            ) {
                val progress =
                    if (pageCount > 0) currentPage.toFloat() / pageCount else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(IndustryAccent),
                )
            }

            // `.seg`: one bordered box, its options divided by hairlines.
            Row(
                modifier = Modifier.border(1.dp, IndustryDivider),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegButton(MinusIcon, "Zoom out", enabled = canZoomOut, onClick = onZoomOut)
                SegDivider()
                Box(
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .height(30.dp)
                        .clickable(onClick = onZoomReset),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = zoomLabel,
                        style = TextStyle(
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            letterSpacing = 0.04.em,
                            fontFeatureSettings = TabularNums,
                        ),
                    )
                }
                SegDivider()
                SegButton(PlusIcon, "Zoom in", enabled = canZoomIn, onClick = onZoomIn)
            }
        }
    }
}

@Composable
private fun SegButton(
    icon: Icon24,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 30.dp)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // `.btn:disabled { opacity: 0.45 }`
        StrokeIcon(icon, size = 15.dp, color = IndustryText.copy(alpha = if (enabled) 1f else 0.45f))
    }
}

@Composable
private fun SegDivider() {
    Box(modifier = Modifier.width(1.dp).height(30.dp).background(IndustryDivider))
}

@Composable
private fun Hairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IndustryDivider))
}

/**
 * The zoom readout the design flashes over the middle of the page while the
 * scale is changing.
 */
@Composable
fun ZoomFlash(zoomLabel: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(IndustryNeutral900.copy(alpha = 0.82f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = zoomLabel,
            style = TextStyle(
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = 0.06.em,
                fontFeatureSettings = TabularNums,
            ),
            color = IndustryBackground,
        )
    }
}
