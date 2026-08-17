package com.djvureader.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.djvureader.app.ui.theme.HeadingFont
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.IndustryDivider
import com.djvureader.app.ui.theme.IndustryNeutral900
import com.djvureader.app.ui.theme.IndustryText
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.onSurfaceAlpha
import kotlin.math.roundToInt

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

/**
 * The design's about mark: a 9-unit ring around a lowercase i.
 *
 * The tittle is a filled dot in the design, and everything here is drawn with
 * one stroked pen. A circle small enough that the stroke closes over its middle
 * comes out as that dot.
 */
internal val InfoIcon: Icon24 = { u ->
    addOval(Rect(3 * u, 3 * u, 21 * u, 21 * u))
    moveTo(12 * u, 11 * u); lineTo(12 * u, 16.5f * u)
    addOval(Rect(11.7f * u, 7.7f * u, 12.3f * u, 8.3f * u))
}

/**
 * Contents: three ruled entries, each with a short lead, in the manner of an
 * indented list.
 */
internal val ContentsIcon: Icon24 = { u ->
    for (y in intArrayOf(7, 12, 17)) {
        moveTo(4 * u, y * u); lineTo(6 * u, y * u)
        moveTo(9 * u, y * u); lineTo(20 * u, y * u)
    }
}

/** A 6.5-unit lens with its handle running out to (20.5, 20.5). */
internal val SearchIcon: Icon24 = { u ->
    addOval(Rect(4.5f * u, 4.5f * u, 17.5f * u, 17.5f * u))
    moveTo(16 * u, 16 * u); lineTo(20.5f * u, 20.5f * u)
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
internal fun IconAction(
    icon: Icon24,
    label: String,
    onClick: () -> Unit,
    size: Dp = 19.dp,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StrokeIcon(icon, size = size)
    }
}

/**
 * The design's `.btn-primary`: a square block of accent, 46dp tall, with its
 * mark and label centred together.
 *
 * Written out rather than dressed up from Material's `Button`, which brings a
 * pill of its own and a minimum touch width, and which has to be argued out of
 * both everywhere it is used. There is one primary action per screen and it
 * looks the same on each.
 */
@Composable
internal fun PrimaryAction(
    icon: Icon24,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .background(IndustryAccent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        StrokeIcon(icon, size = 17.dp, color = IndustryBackground)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = IndustryBackground,
        )
    }
}

/**
 * How far off screen a hidden bar sits, as a fraction of its own height. The
 * design's `translateY(±102%)` clears the bar plus its hairline border.
 */
private const val HIDDEN_SHIFT = 1.02f

/**
 * Reader top bar: back, what is being shown, and search.
 *
 * One line, and the caller says what goes on it. Normally that is the
 * document's name; while matches are marked on the page it names the search
 * instead, since the page is no longer being shown plainly. [backLabel] goes
 * with it, because back then means leaving the search rather than the document.
 *
 * The bar carries nothing about the file itself. Its format and length are
 * facts about a document you have already chosen to read, and the page is
 * worth more of the screen than they are.
 *
 * [hidden] runs 0..1 and is animated by the caller, so the bar slides rather
 * than appearing.
 */
@Composable
fun ReaderTopBar(
    title: String,
    hidden: Float,
    backLabel: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    // Only for documents that carry one. A button that opens an empty screen
    // teaches the reader to stop pressing it.
    onContents: (() -> Unit)? = null,
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
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconAction(BackIcon, backLabel, onBack)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            onContents?.let { IconAction(ContentsIcon, "Contents", it) }
            IconAction(SearchIcon, "Search in document", onSearch)
        }
        Hairline()
    }
}

/**
 * Reader bottom bar: the page indicator, a slider across the document, and the
 * zoom segment.
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
    onSeekToPage: (Int) -> Unit,
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
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
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

            PageSlider(
                currentPage = currentPage,
                pageCount = pageCount,
                onSeek = onSeekToPage,
                modifier = Modifier.weight(1f),
            )

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
                            fontFamily = HeadingFont,
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

/** The slider's own dimensions. The row is 20dp tall, as the design's is. */
private val TrackThickness = 2.dp
private val ThumbWidth = 4.dp
private val ThumbHeight = 16.dp

/**
 * The design's page slider, drawn as this system draws things rather than as
 * the browser's `<input type="range">`: nothing here is rounded, so the handle
 * is a caliper mark riding the same 2dp rule the library cards use for
 * progress.
 *
 * Seeking is live - the design's range jumps on every input event, not on
 * release - but the handle follows the finger rather than the list, so it
 * cannot stutter behind pages that are still measuring.
 */
@Composable
private fun PageSlider(
    currentPage: Int,
    pageCount: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragPage by remember { mutableStateOf<Int?>(null) }
    val page = (dragPage ?: currentPage).coerceIn(1, pageCount.coerceAtLeast(1))

    // The handle's centre travels between its own half-widths so it never
    // overhangs the row; page 1 sits at one end of that span, the last page at
    // the other.
    val fraction = if (pageCount > 1) (page - 1).toFloat() / (pageCount - 1) else 0f

    Canvas(
        modifier = modifier
            .height(20.dp)
            .semantics {
                contentDescription = "Page slider"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = page.toFloat(),
                    range = 1f..pageCount.toFloat().coerceAtLeast(1f),
                    steps = (pageCount - 2).coerceAtLeast(0),
                )
                setProgress { target ->
                    onSeek(target.roundToInt().coerceIn(1, pageCount))
                    true
                }
            }
            .pointerInput(pageCount) {
                // Page under x, where x is measured in this slider's own
                // coordinates. Inverse of the `fraction` above.
                fun pageAt(x: Float): Int {
                    val span = size.width - ThumbWidth.toPx()
                    if (span <= 0f || pageCount <= 1) return 1
                    val f = ((x - ThumbWidth.toPx() / 2f) / span).coerceIn(0f, 1f)
                    return (f * (pageCount - 1)).roundToInt() + 1
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Claim the press so the bar keeps it: a touch anywhere on
                    // the track seeks there, as it does on a range input.
                    down.consume()
                    var target = pageAt(down.position.x)
                    dragPage = target
                    onSeek(target)

                    horizontalDrag(down.id) { change ->
                        change.consume()
                        val next = pageAt(change.position.x)
                        if (next != target) {
                            target = next
                            dragPage = next
                            onSeek(next)
                        }
                    }
                    dragPage = null
                }
            },
    ) {
        val track = TrackThickness.toPx()
        val thumbW = ThumbWidth.toPx()
        val thumbH = ThumbHeight.toPx()
        val trackTop = (size.height - track) / 2f
        val centre = thumbW / 2f + (size.width - thumbW) * fraction

        drawRect(
            color = onSurfaceAlpha(0.14f),
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, track),
        )
        drawRect(
            color = IndustryAccent,
            topLeft = Offset(0f, trackTop),
            size = Size(centre, track),
        )
        drawRect(
            color = IndustryAccent,
            topLeft = Offset(centre - thumbW / 2f, (size.height - thumbH) / 2f),
            size = Size(thumbW, thumbH),
        )
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

/** The design's 1px divider, drawn as a rule across whatever it is given. */
@Composable
internal fun Hairline() {
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
                fontFamily = HeadingFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = 0.06.em,
                fontFeatureSettings = TabularNums,
            ),
            color = IndustryBackground,
        )
    }
}
