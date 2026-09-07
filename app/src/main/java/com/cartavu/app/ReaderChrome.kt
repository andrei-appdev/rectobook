package com.cartavu.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cartavu.app.ui.theme.IndustryAccent
import com.cartavu.app.ui.theme.IndustryBackground
import com.cartavu.app.ui.theme.IndustryDivider
import com.cartavu.app.ui.theme.IndustryText
import com.cartavu.app.ui.theme.TabularNums
import com.cartavu.app.ui.theme.onSurfaceAlpha
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

/**
 * The way in to everything that is true of the app rather than of a document:
 * three plain rules.
 *
 * [ContentsIcon] is the same three rules with leaders, which is what tells the
 * two apart - one lists a document, the other opens the app's own drawer. They
 * are never on the same screen.
 */
internal val MenuIcon: Icon24 = { u ->
    for (y in intArrayOf(7, 12, 17)) {
        moveTo(4 * u, y * u); lineTo(20 * u, y * u)
    }
}

/** The mark on a row that leads somewhere else: [BackIcon]'s head, reversed. */
internal val ForwardIcon: Icon24 = { u ->
    moveTo(5 * u, 12 * u); lineTo(19 * u, 12 * u)
    moveTo(19 * u, 12 * u); lineTo(13 * u, 6 * u)
    moveTo(19 * u, 12 * u); lineTo(13 * u, 18 * u)
}

/** A 6.5-unit lens with its handle running out to (20.5, 20.5). */
internal val SearchIcon: Icon24 = { u ->
    addOval(Rect(4.5f * u, 4.5f * u, 17.5f * u, 17.5f * u))
    moveTo(16 * u, 16 * u); lineTo(20.5f * u, 20.5f * u)
}

/**
 * The marks on the page stepper: a bare chevron each way.
 *
 * Not [BackIcon] and [ForwardIcon], which carry a shaft and are spoken for -
 * on the top bar that arrow means leaving what is on screen, and one page
 * further on is not that.
 */
internal val ChevronLeftIcon: Icon24 = { u ->
    moveTo(15 * u, 5 * u); lineTo(8 * u, 12 * u); lineTo(15 * u, 19 * u)
}

internal val ChevronRightIcon: Icon24 = { u ->
    moveTo(9 * u, 5 * u); lineTo(16 * u, 12 * u); lineTo(9 * u, 19 * u)
}

/**
 * A cross, for the one thing in the app that closes rather than goes back: the
 * settings panel, which was never a screen you arrived at from anywhere.
 */
internal val CloseIcon: Icon24 = { u ->
    moveTo(6 * u, 6 * u); lineTo(18 * u, 18 * u)
    moveTo(18 * u, 6 * u); lineTo(6 * u, 18 * u)
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

/**
 * The design's `.btn-icon`: a 36dp square with no border of its own.
 *
 * Its states are drawn here rather than left to Material's ripple. The mark
 * sits alone in an unclipped 36dp box, and the ripple fills that box: a
 * focused one - which is what the first control on a screen becomes as soon as
 * a keyboard is attached - reads as a mis-coloured tile behind the icon rather
 * than as a control. A wash while pressed and a rule while focused say the same
 * thing in the design's own terms.
 */
@Composable
internal fun IconAction(
    icon: Icon24,
    label: String,
    onClick: () -> Unit,
    size: Dp = 19.dp,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(36.dp)
            .background(if (pressed) onSurfaceAlpha(0.10f) else Color.Transparent)
            .then(if (focused) Modifier.border(1.dp, IndustryAccent) else Modifier)
            .clickable(
                interactionSource = interactions,
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            ),
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

/** The one size the app waits at. */
private val WaitRingSize = 22.dp
private val WaitRingStroke = 2.dp

/**
 * The app's one way of saying *working*.
 *
 * Small and grey rather than the platform's full-size accent ring: nothing
 * here can be measured - neither opening a document nor decoding a page
 * reports progress - so this marks that something is happening rather than
 * offering something to watch. It is the same ring in the same place wherever
 * the app waits.
 *
 * [color] is the ink of whatever it is drawn on - the app's own on the loading
 * screen, the page's paper ink in the reader, which is fixed light because a
 * page is white in both palettes.
 */
@Composable
fun WaitRing(color: Color, modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(WaitRingSize),
        color = color,
        strokeWidth = WaitRingStroke,
    )
}

/**
 * Keeps content clear of the system bars.
 *
 * The window itself reserves nothing: the page fills it, and the system bars
 * and the reader's own bars are drawn over the page rather than beside it.
 * Everything that is not a page - the library, the panels, the bars' own
 * controls - still has to stay out from under them.
 *
 * All three read the insets *ignoring visibility*, so the space a bar occupies
 * stays reserved while it is hidden. A screen that laid itself out into that
 * space and back again as the chrome came and went is the resize this whole
 * arrangement exists to avoid: the page behind it would be re-rendered at a new
 * width for the sake of a strip with nothing in it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.clearOfSystemBars(): Modifier =
    windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)

/** Clear of the navigation bar alone, on whichever edge it is on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.clearOfNavigationBar(
    sides: WindowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
): Modifier = windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(sides))

/** Clear of the status bar alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.clearOfStatusBar(): Modifier =
    windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)

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
@OptIn(ExperimentalLayoutApi::class)
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
    // False when the reader has been told never to show that bar, in which
    // case the bar has no strip to stand clear of.
    reserveStatusBar: Boolean = true,
    reserveNavigationBar: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -HIDDEN_SHIFT * size.height * hidden }
            .background(IndustryBackground.copy(alpha = 0.94f)),
    ) {
        Row(
            // The bar reaches the top edge so that the page can too - in
            // landscape, where the status bar goes with the chrome, the page
            // is the whole height of the screen and the clock sits on this
            // bar's background while the bar is there.
            modifier = Modifier
                .fillMaxWidth()
                .then(if (reserveStatusBar) Modifier.clearOfStatusBar() else Modifier)
                .then(
                    if (reserveNavigationBar) {
                        Modifier.clearOfNavigationBar(WindowInsetsSides.Horizontal)
                    } else {
                        Modifier
                    },
                )
                // The 36dp mark squares set the bar's height; the padding is
                // only what keeps them off its rule.
                .padding(horizontal = 8.dp, vertical = 2.dp),
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
            onContents?.let { IconAction(ContentsIcon, stringResource(R.string.reader_contents), it) }
            IconAction(SearchIcon, stringResource(R.string.search_in_document), onSearch)
        }
        Hairline()
    }
}

/**
 * Reader bottom bar: where in the document you are, and the two ways of
 * getting somewhere else.
 *
 * Two lines rather than one, and the two ways of getting somewhere are one to
 * a line. The arrows step from the page the counter names, so they stand
 * either side of it and the three read as one control. The slider travels the
 * whole document, and gets a line to travel across. Zoom is not here at all: a
 * pinch already does it, and the segment that repeated it was taking the width
 * the slider wanted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    hidden: Float,
    onGoToPage: () -> Unit,
    onSeekToPage: (Int) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
    // False when the reader has been told never to show the buttons, in which
    // case there is no strip under this bar for them to stand on.
    reserveNavigationBar: Boolean = true,
) {
    val navigationStrip = if (reserveNavigationBar) {
        WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = HIDDEN_SHIFT * size.height * hidden }
            .background(IndustryBackground.copy(alpha = 0.94f)),
    ) {
        Hairline()
        Column(
            // The bar reaches the bottom edge so that the page can too - its
            // controls sit above the navigation bar's strip, its background
            // fills it.
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (reserveNavigationBar) {
                        Modifier.clearOfNavigationBar(WindowInsetsSides.Horizontal)
                    } else {
                        Modifier
                    },
                )
                // As on the top bar, the controls set the height - and it is
                // the arrows that set it now, being taller than the digits
                // they stand beside, so the upper edge adds nothing of its
                // own. The lower keeps 4dp: below it is the strip the system's
                // own buttons stand on, and they are not this bar's controls.
                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The row is only as wide as the three controls on it, and the
            // column centres it. The gaps between them are the counter's own
            // padding, so the digits keep the same room to be aimed at that
            // they had when they stood alone.
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(
                    ChevronLeftIcon,
                    stringResource(R.string.reader_previous_page),
                    enabled = currentPage > 1,
                    onClick = onPreviousPage,
                )
                // A label that happens to be pressable rather than a button:
                // it says where the document is open, which is not a thing to
                // be operated, and a bordered block between the arrows would
                // read as a third one of them.
                Text(
                    text = stringResource(R.string.reader_page_of, currentPage, pageCount),
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 0.04.em,
                        fontFeatureSettings = TabularNums,
                    ),
                    modifier = Modifier
                        .clickable(
                            onClickLabel = stringResource(R.string.reader_go_to_page),
                            onClick = onGoToPage,
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
                StepButton(
                    ChevronRightIcon,
                    stringResource(R.string.reader_next_page),
                    enabled = currentPage < pageCount,
                    onClick = onNextPage,
                )
            }

            // The whole width, now that nothing shares the line with it. A
            // document of any length is a thumb's travel across the screen,
            // and every page in it is that travel divided by the count.
            PageSlider(
                currentPage = currentPage,
                pageCount = pageCount,
                onSeek = onSeekToPage,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The strip the navigation buttons stand on, painted solid. The bar
        // itself is translucent so the page can be seen running under it, but
        // a button read against a page it half shows through is a button on
        // nothing; the buttons get the app's own colour behind them. In
        // landscape the bar is on a side rather than the bottom, this is
        // nothing high, and they stand on the page instead.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(navigationStrip)
                .background(IndustryBackground),
        )
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
    // Read here rather than in the draw lambda, which is not a composable.
    val trackColor = onSurfaceAlpha(0.14f)
    val markColor = IndustryAccent
    val page = (dragPage ?: currentPage).coerceIn(1, pageCount.coerceAtLeast(1))

    // The handle's centre travels between its own half-widths so it never
    // overhangs the row; page 1 sits at one end of that span, the last page at
    // the other.
    val fraction = if (pageCount > 1) (page - 1).toFloat() / (pageCount - 1) else 0f

    // Read out here: the semantics block is not a composable scope.
    val sliderLabel = stringResource(R.string.reader_page_slider)

    Canvas(
        modifier = modifier
            .height(20.dp)
            .semantics {
                contentDescription = sliderLabel
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

/**
 * A 0f..1f slider, drawn the way this design draws a range: the same 2dp rule
 * with a caliper mark on it that [PageSlider] rides, nothing rounded.
 *
 * Live - the value follows the finger rather than waiting for it to lift, so
 * whatever it is set against moves while it is being judged.
 */
@Composable
internal fun ValueSlider(
    value: Float,
    label: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read here rather than in the draw lambda, which is not a composable.
    val trackColor = onSurfaceAlpha(0.14f)
    val markColor = IndustryAccent
    val fraction = value.coerceIn(0f, 1f)

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

/** The height of a row of segments carrying words rather than marks. */
private val LabelSegHeight = 38.dp

/**
 * The design's `.seg` carrying a choice: one bordered box, its options divided
 * by hairlines, the chosen one filled with the accent.
 *
 * A row rather than a list of radio buttons because the options are few, short
 * and mutually exclusive, and because the design already draws a segment this
 * way for zoom.
 */
@Composable
internal fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(LabelSegHeight).border(1.dp, IndustryDivider),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) SegDivider(height = LabelSegHeight)
            val chosen = option == selected
            val text = label(option)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(LabelSegHeight)
                    .background(if (chosen) IndustryAccent else Color.Transparent)
                    .selectable(
                        selected = chosen,
                        onClick = { onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.04.em),
                    // On the accent, the app's ground is the ink - the same
                    // pairing `PrimaryAction` uses.
                    color = if (chosen) IndustryBackground else IndustryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A bare mark that steps something on: no border of its own, because the two
 * of them bracket the slider rather than standing as a pair of buttons.
 */
@Composable
private fun StepButton(
    icon: Icon24,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 32.dp)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // `.btn:disabled { opacity: 0.45 }`
        StrokeIcon(icon, size = 15.dp, color = IndustryText.copy(alpha = if (enabled) 1f else 0.45f))
    }
}

@Composable
private fun SegDivider(height: Dp = 30.dp) {
    Box(modifier = Modifier.width(1.dp).height(height).background(IndustryDivider))
}

/** The design's 1px divider, drawn as a rule across whatever it is given. */
@Composable
internal fun Hairline() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IndustryDivider))
}

