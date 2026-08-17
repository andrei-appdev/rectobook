package com.djvureader.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.djvureader.app.ui.theme.DjvuTheme
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryNeutral200
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.onSurfaceAlpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Zoom is expressed as a multiple of fit-to-width. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val ZOOM_STEP = 1.5f

/** How long the page must settle before reading progress is written. */
private const val PROGRESS_SAVE_DELAY_MS = 600L

/** How long zoom must settle before pages are re-rendered at the new scale. */
private const val ZOOM_SETTLE_DELAY_MS = 150L

/** How long the zoom readout lingers after a step, matching the design's 700ms. */
private const val ZOOM_FLASH_MS = 700L

/**
 * Gap between pages. Narrow enough that the backdrop reads as a rule between
 * two sheets rather than as a gutter they float in.
 */
private val PAGE_GAP = 2.dp

/** The design's `transition: transform .22s cubic-bezier(.3,.7,.2,1)`. */
private const val CHROME_SLIDE_MS = 220
private val ChromeEasing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars on every version. API 35 enforces this
        // anyway; asking for it explicitly means older devices hide and reveal
        // the bars the same way rather than resizing the window underneath us.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // The design is light throughout, so the clock and the navigation
            // icons have to be dark to be seen against it.
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        // A document may arrive from another app via ACTION_VIEW.
        val initialUri: Uri? = intent?.data
        setContent {
            DjvuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Keep the bar and page list clear of the status and
                    // navigation bars.
                    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        ReaderScreen(initialUri = initialUri)
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderScreen(initialUri: Uri?, viewModel: ReaderViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(initialUri) {
        if (initialUri != null) viewModel.openUri(initialUri)
    }

    // Many providers do not tag .djvu with a useful MIME type, so the picker
    // stays open to any file rather than silently hiding valid documents.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.openUri(uri) }
    val openDocument = { picker.launch(arrayOf("*/*")) }

    when (val current = state) {
        is ReaderState.Empty -> {
            val recents by viewModel.recents.collectAsState()
            LibraryScreen(
                recents = recents,
                thumbnail = viewModel::thumbnail,
                onRequestThumbnail = viewModel::requestThumbnail,
                onPick = openDocument,
                onSample = viewModel::openBundledSample,
                onOpenRecent = viewModel::openRecent,
            )
        }

        is ReaderState.Loading -> Centered { CircularProgressIndicator() }

        is ReaderState.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Could not open document:\n${current.message}",
                    textAlign = TextAlign.Center,
                )
                Button(onClick = openDocument, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Choose another file")
                }
            }
        }

        is ReaderState.Loaded -> DocumentReader(
            state = current,
            viewModel = viewModel,
            onBack = viewModel::closeDocument,
        )
    }
}

/**
 * The top bar's line while a search is showing, in the place the document's
 * format and length would be. Kept to the count: the query itself is a tap on
 * the magnifier away, and the bar has one line's room.
 */
private fun matchSummary(results: SearchResults): String {
    // A scan publishes as it goes, so a count shown mid-scan is a floor rather
    // than an answer, and says so.
    val soFar = if (results.running) " SO FAR" else ""
    return when {
        results.hits.isEmpty() && results.running -> "SEARCHING"
        results.hits.isEmpty() -> "NO MATCHES"
        results.hits.size == 1 -> "1 MATCH$soFar"
        results.truncated -> "${results.hits.size}+ MATCHES"
        else -> "${results.hits.size} MATCHES$soFar"
    }
}

@Composable
private fun DocumentReader(
    state: ReaderState.Loaded,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showGoToSheet by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchResults by viewModel.search.collectAsState()
    // Held out here so it survives the panel being closed and opened again.
    val searchListState = rememberLazyListState()

    // The design's chrome is transient: it starts visible and a tap on the page
    // takes it away, so a document can be read edge to edge.
    var chromeVisible by remember { mutableStateOf(true) }
    val chromeHidden by animateFloatAsState(
        targetValue = if (chromeVisible) 0f else 1f,
        animationSpec = tween(CHROME_SLIDE_MS, easing = ChromeEasing),
        label = "chrome",
    )

    // The system bars go with the reader's own, so one tap clears everything
    // off the page. Search is a screen in its own right rather than a page to
    // read, so it always gets them back.
    SystemBars(visible = chromeVisible || showSearch)

    // Whether the page is being shown as a search result rather than plainly:
    // marks on the words, and a bar that says so. Outlives the search screen,
    // because landing on a hit is the point of running the search.
    val searching = searchResults.query.length >= MIN_QUERY_LENGTH

    /** Puts the document back the way it reads without a search over it. */
    fun endSearch() {
        query = ""
        viewModel.clearSearch()
    }

    // Back leaves the search screen, then the search, then the document - one
    // layer at a time, and the same order the top bar's arrow goes in.
    BackHandler(enabled = showSearch || searching) {
        if (showSearch) showSearch = false else endSearch()
    }

    // The page the reader considers themselves on.
    val currentPage by remember {
        derivedStateOf { listState.mostVisiblePage() + 1 }
    }

    // Resume where this document was left, then keep saving as it is read.
    // Both live in one effect so the restore completes before the first save,
    // otherwise the initial position would overwrite the stored page with 0.
    LaunchedEffect(state.documentKey) {
        // Claimed rather than simply done, because this effect restarts with
        // every new composition - including the one a rotation brings - while
        // the page to resume at does not change. The list carries its own
        // position across a rotation; scrolling it back to the resume point
        // would throw away everything read since.
        if (state.initialPage > 0 && viewModel.claimPositionRestore(state.documentKey)) {
            // Not before the list has been laid out. A scroll against a list
            // with no measured pages is clamped to whatever its content height
            // is at the time, and lands short.
            snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            listState.scrollToItem(state.initialPage)
        }
        // Saved against the same page the counter shows, or the library card
        // and the reader would disagree about where the document was left.
        snapshotFlow { listState.mostVisiblePage() }
            .distinctUntilChanged()
            .collectLatest { page ->
                delay(PROGRESS_SAVE_DELAY_MS)
                viewModel.saveProgress(page)
            }
    }

    // Zooming widens the content rather than scaling the list: pages are
    // re-rendered at the new width, so they stay sharp and the list keeps
    // correct scroll extents.
    //
    // Saved across a configuration change, because the scale is the reader's
    // choice rather than a property of the window. It is a multiple of
    // fit-to-width, so the same number goes on meaning the same thing when the
    // screen turns and the width it multiplies changes.
    var zoom by rememberSaveable { mutableStateOf(MIN_ZOOM) }
    // Pages lay out at `zoom` immediately but are re-rendered only at
    // `renderZoom`. A pinch changes zoom every frame, and re-rendering on
    // each one would cancel the previous decode over and over.
    //
    // Saved alongside `zoom`, so a rotation re-renders pages straight at the
    // restored scale instead of showing them at fit-width until the settle
    // delay catches up.
    var renderZoom by rememberSaveable { mutableStateOf(MIN_ZOOM) }
    // Live pinch state: scale applied on the GPU until the fingers lift.
    var gestureScale by remember { mutableFloatStateOf(1f) }
    var gestureFocus by remember { mutableStateOf(Offset.Zero) }
    val horizontalScroll = rememberScrollState()
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(zoom) {
        if (zoom == MIN_ZOOM) horizontalScroll.scrollTo(0)
        delay(ZOOM_SETTLE_DELAY_MS)
        renderZoom = zoom
    }

    // The design flashes the scale over the middle of the page as it changes:
    // live while the fingers are down, then lingering briefly after a step.
    var zoomFlashes by remember { mutableIntStateOf(0) }
    var flashLingering by remember { mutableStateOf(false) }
    LaunchedEffect(zoomFlashes) {
        if (zoomFlashes == 0) return@LaunchedEffect
        flashLingering = true
        delay(ZOOM_FLASH_MS)
        flashLingering = false
    }

    val contentWidthDp = with(LocalDensity.current) { (viewport.width * zoom).toInt().toDp() }
    val renderWidth = (viewport.width * renderZoom).toInt()

    // Identity of the marks a page should be showing. The hit count is in it
    // because a scan still running keeps finding more on pages already drawn.
    val highlightKey = if (searching) {
        "${searchResults.query}/${searchResults.hits.size}"
    } else {
        ""
    }

    /**
     * Zooms by [scale] about [focus], given in viewport coordinates.
     *
     * Both scroll positions are absolute pixel offsets, so growing the content
     * without adjusting them slides the page towards its top left corner. These
     * corrections keep whatever sits under [focus] where it is.
     */
    fun zoomAbout(scale: Float, focus: Offset) {
        val target = (zoom * scale).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val factor = target / zoom
        if (factor == 1f) return

        // Horizontal: content point under the focus is scrollX + focus.x.
        val targetScrollX = (factor * (horizontalScroll.value + focus.x) - focus.x)
            .roundToInt()

        // Vertical: LazyListState is item-relative, so anchor to the fraction of
        // the item the focus falls in - that fraction is scale-independent.
        //
        // The two live in different coordinate spaces, and mixing them was an
        // error of exactly the top content padding: item offsets are measured
        // from the top of the list's content, `focus` from the top of the
        // viewport, and the pages start a bar's height below that.
        val info = listState.layoutInfo
        val padding = info.beforeContentPadding
        val focusInContent = focus.y - padding

        val visible = info.visibleItemsInfo
        val anchorItem = visible.firstOrNull {
            focusInContent >= it.offset && focusInContent < it.offset + it.size
        } ?: visible.firstOrNull()
        val verticalAnchor = anchorItem?.takeIf { it.size > 0 }?.let { item ->
            val fraction = ((focusInContent - item.offset) / item.size).coerceIn(0f, 1f)
            // Where the anchor sits inside the page once the page has grown,
            // less where it has to come back to on screen.
            item.index to (padding + fraction * item.size * factor - focus.y).roundToInt()
        }

        zoom = target
        zoomFlashes++

        // Requested rather than scrolled, so that the list takes its new
        // position in the same remeasure that lays the pages out at their new
        // width. Scrolling a frame later drew the pages once grown but not yet
        // anchored, which is what made the page jump as a pinch ended.
        verticalAnchor?.let { (index, offset) ->
            listState.requestScrollToItem(index, offset.coerceAtLeast(0))
        }

        scope.launch {
            // The horizontal scroller has no equivalent request, and clamps
            // against bounds it only learns at the remeasure, so this one still
            // has to wait a frame.
            withFrameNanos { }
            horizontalScroll.scrollTo(
                targetScrollX.coerceIn(0, horizontalScroll.maxValue),
            )
        }
    }

    // Buttons zoom about the middle of the viewport, so they anchor the same
    // way a pinch does.
    val viewportCentre = Offset(viewport.width / 2f, viewport.height / 2f)

    // Pages are numbered from 1 wherever the reader shows them; the list counts
    // from 0. Overlapping calls are safe - scrolling is mutually exclusive, so
    // dragging the slider leaves the last position asked for.
    fun goToPage(page: Int) {
        scope.launch { listState.scrollToItem((page - 1).coerceIn(0, state.pageCount - 1)) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // The design lays the pages on a tone darker than the app
                // background, so a white page reads as paper on a desk.
                .background(IndustryNeutral200)
                .onSizeChanged { viewport = it }
                // The gesture sits outside the scrolling box so its centroid
                // arrives in viewport coordinates rather than content ones.
                .pinchToZoom(
                    // The search panel covers the page; touches on it are the
                    // panel's alone.
                    enabled = !showSearch,
                    onZoom = { scale, focus ->
                        gestureFocus = focus
                        gestureScale = (gestureScale * scale)
                            .coerceIn(MIN_ZOOM / zoom, MAX_ZOOM / zoom)
                    },
                    onEnd = {
                        val committed = gestureScale
                        gestureScale = 1f
                        zoomAbout(committed, gestureFocus)
                    },
                    onTap = { chromeVisible = !chromeVisible },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // While fingers are down the page is scaled on the GPU
                    // instead of being re-laid out. Re-laying out per frame
                    // and correcting the scroll a frame later made the page
                    // tremble; this commits one layout pass on release.
                    .graphicsLayer {
                        if (gestureScale != 1f && size.width > 0f && size.height > 0f) {
                            scaleX = gestureScale
                            scaleY = gestureScale
                            transformOrigin = TransformOrigin(
                                (gestureFocus.x / size.width).coerceIn(0f, 1f),
                                (gestureFocus.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                    }
                    // Panning is only meaningful once pages overflow the viewport.
                    .horizontalScroll(horizontalScroll, enabled = zoom > MIN_ZOOM),
            ) {
                // Nothing is laid out until the viewport has been measured.
                //
                // There is no width to fall back on in the meantime: this list
                // sits inside a horizontal scroller, which measures its child
                // against unbounded width, so `fillMaxWidth` has nothing to
                // fill and the list collapses to its minimum intrinsic width -
                // about 32px. At that width every page is a 35px sliver, the
                // whole document fits one screen, and a restored reading
                // position is clamped to the top. Composing a frame later
                // costs nothing; opening on the wrong page does not.
                if (viewport.width > 0) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .width(contentWidthDp)
                            .fillMaxHeight(),
                        // Pages run under the bars, which are translucent, so
                        // the list reserves their height rather than being
                        // inset by it.
                        contentPadding = PaddingValues(top = 60.dp, bottom = 66.dp),
                        verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
                    ) {
                        items(count = state.pageCount, key = { it }) { index ->
                            // Falls back to the document's shape until this
                            // page is measured, so the list does not reflow
                            // while scrolling.
                            val aspectRatio = viewModel.pageSizes[index]?.aspectRatio()
                                ?: state.defaultAspectRatio
                            PageView(
                                pageNumber = index,
                                renderWidth = renderWidth,
                                aspectRatio = aspectRatio,
                                // Changes when the search does, which is what
                                // sends a page to look up its marks again.
                                highlightKey = highlightKey,
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }
        }

        // Live during a pinch, so the readout tracks the fingers rather than
        // jumping when they lift.
        val zoomLabel = "${(zoom * gestureScale * 100).roundToInt()}%"

        ReaderTopBar(
            // The term goes in the title, where the document's name would be:
            // what is on screen is this search, and the page is full of marks
            // that only mean anything once you know what was looked for.
            title = if (searching) "Search “${searchResults.query}”" else state.title,
            // The document's own line, or the search's. Nothing about the
            // document while its pages are marked: the bar describes what is
            // on screen, and what is on screen is a result.
            subtitle = if (searching) {
                matchSummary(searchResults)
            } else {
                "${state.format.label} · ${state.pageCount} PP"
            },
            hidden = chromeHidden,
            backLabel = if (searching) "Clear search" else "Back to library",
            onBack = if (searching) ({ endSearch() }) else onBack,
            onSearch = { showSearch = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        ReaderBottomBar(
            currentPage = currentPage,
            pageCount = state.pageCount,
            zoomLabel = zoomLabel,
            canZoomOut = zoom > MIN_ZOOM,
            canZoomIn = zoom < MAX_ZOOM,
            hidden = chromeHidden,
            onGoToPage = { showGoToSheet = true },
            onSeekToPage = ::goToPage,
            onZoomOut = { zoomAbout(1f / ZOOM_STEP, viewportCentre) },
            onZoomIn = { zoomAbout(ZOOM_STEP, viewportCentre) },
            onZoomReset = { zoomAbout(MIN_ZOOM / zoom, viewportCentre) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (flashLingering || gestureScale != 1f) {
            ZoomFlash(zoomLabel = zoomLabel, modifier = Modifier.align(Alignment.Center))
        }

        // Covers the reader rather than sitting beside it, as the design draws
        // it - the page is still there underneath when search is dismissed.
        if (showSearch) {
            SearchScreen(
                query = query,
                results = searchResults,
                textLayerLabel = state.format.textLayerLabel,
                listState = searchListState,
                onQueryChange = {
                    query = it
                    viewModel.search(it)
                    // A new term means a new list, so it starts at the top.
                    // Only here: the list is kept exactly where it was when the
                    // panel merely closes and opens again.
                    scope.launch { searchListState.scrollToItem(0) }
                },
                onClose = { showSearch = false },
                onOpenHit = { hit ->
                    showSearch = false
                    // The chrome comes back with the page, so the reader can
                    // see which page they landed on.
                    chromeVisible = true
                    goToPage(hit.page + 1)
                },
            )
        }
    }

    if (showGoToSheet) {
        GoToPageSheet(
            pageCount = state.pageCount,
            onDismiss = { showGoToSheet = false },
            onJump = { page ->
                showGoToSheet = false
                goToPage(page)
            },
        )
    }
}

/**
 * Index of the page filling most of the viewport - which is the one being
 * read, and not always the topmost one to intrude into it.
 *
 * Counting the topmost was off by one at both ends of any long scroll: at the
 * bottom of a document the last page is what fills the screen while the one
 * before it still clips the top edge, so a 57-page document reported 56 of 57
 * however far down it went.
 */
private fun LazyListState.mostVisiblePage(): Int {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return firstVisibleItemIndex
    return visible.maxByOrNull { page ->
        // How much of this page is inside the viewport. Both offsets are in
        // the same space as the items', so content padding is already in them.
        val top = maxOf(page.offset, info.viewportStartOffset)
        val bottom = minOf(page.offset + page.size, info.viewportEndOffset)
        bottom - top
    }?.index ?: firstVisibleItemIndex
}

/**
 * One page of the document. Rendering is tied to the composable's lifetime, so
 * scrolling a page off screen cancels its render.
 */
@Composable
private fun PageView(
    pageNumber: Int,
    renderWidth: Int,
    aspectRatio: Float,
    highlightKey: String,
    viewModel: ReaderViewModel,
) {
    // Seeded from whatever is already in memory, and deliberately not keyed on
    // renderWidth, so a page never blanks: zooming keeps showing the previous
    // rendering scaled until a sharper one arrives, and an item recomposed by
    // the list picks its bitmap straight back up.
    var bitmap by remember(pageNumber) { mutableStateOf(viewModel.cachedPage(pageNumber)) }

    LaunchedEffect(pageNumber, renderWidth) {
        // Re-seed in case this page was rendered while the item was away.
        viewModel.cachedPage(pageNumber)?.let { if (bitmap == null) bitmap = it }
        val rendered = viewModel.renderPage(pageNumber, renderWidth)
        if (rendered != null) bitmap = rendered
    }

    // Where this page's search matches are, asked for only while a search is
    // showing and only for pages actually composed.
    val highlights by produceState(emptyList<PageRect>(), pageNumber, highlightKey) {
        value = if (highlightKey.isEmpty()) emptyList() else viewModel.highlights(pageNumber)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            // The design's `box-shadow: var(--shadow-sm)` is dropped: at this
            // gap it only smears into the rule between pages. The hairline edge
            // does the separating, so a scan that is white to its margin still
            // ends visibly.
            .background(Color.White)
            .border(1.dp, onSurfaceAlpha(0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current == null) {
            Text(
                text = "${pageNumber + 1}",
                style = MaterialTheme.typography.labelMedium
                    .copy(fontFeatureSettings = TabularNums),
                color = onSurfaceAlpha(0.45f),
            )
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${pageNumber + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Drawn over the page rather than into it, so a mark costs nothing to
        // put up or take down and never touches the cached rendering. The wash
        // is the design's own `<mark>`, which the hit list already uses.
        if (highlights.isNotEmpty()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                highlights.forEach { mark ->
                    drawRect(
                        color = IndustryAccent.copy(alpha = 0.32f),
                        topLeft = Offset(mark.left * size.width, mark.top * size.height),
                        size = Size(
                            (mark.right - mark.left) * size.width,
                            (mark.bottom - mark.top) * size.height,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Shows or hides the status and navigation bars.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` keeps the bars reachable while they
 * are hidden: a swipe from an edge brings them back for a moment, so the clock
 * and the back gesture are never stranded behind a document.
 *
 * The bars are always restored when this leaves composition - a reader that
 * ate the system bars and did not give them back on the way out would follow
 * the user to the library screen.
 */
@Composable
private fun SystemBars(visible: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return

    LaunchedEffect(visible) {
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) show(WindowInsetsCompat.Type.systemBars())
            else hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            WindowCompat.getInsetsController(window, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** The activity behind a composable's context, however it is wrapped. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reports pinch scale changes and the point they are centred on, without
 * consuming single-pointer drags, so the page list keeps scrolling vertically
 * while pinch still zooms. A press that neither zooms nor travels is reported
 * as a tap, which is how the design toggles the chrome.
 *
 * The tap is recognised here rather than in a second `pointerInput` so that a
 * pinch, or a scroll that happens to end where it began, cannot also read as
 * one.
 *
 * [enabled] is how a panel laid over the page keeps the page out of its
 * gestures. Nothing else can do it: this reads presses whether or not anything
 * above has claimed them, which is what lets a page scroll and pinch at once,
 * and means a tap on a panel would otherwise land on the page as well.
 */
private fun Modifier.pinchToZoom(
    enabled: Boolean,
    onZoom: (scale: Float, focus: Offset) -> Unit,
    onEnd: () -> Unit,
    onTap: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoomed = false
        var multiTouch = false
        var travel = 0f
        var hasPointers = true
        while (hasPointers) {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                multiTouch = true
                val scale = event.calculateZoom()
                val focus = event.calculateCentroid()
                if (scale != 1f && focus != Offset.Unspecified) {
                    onZoom(scale, focus)
                    zoomed = true
                    event.changes.forEach { it.consume() }
                }
            } else {
                event.changes.forEach { travel += (it.position - it.previousPosition).getDistance() }
            }
            hasPointers = event.changes.any { it.pressed }
        }
        when {
            zoomed -> onEnd()
            !multiTouch && travel < viewConfiguration.touchSlop -> onTap()
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
