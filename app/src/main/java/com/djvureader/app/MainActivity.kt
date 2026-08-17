package com.djvureader.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.djvureader.app.ui.theme.DjvuTheme
import com.djvureader.app.ui.theme.Industry
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.IndustryDivider
import com.djvureader.app.ui.theme.IndustryNeutral200
import com.djvureader.app.ui.theme.LightIndustry
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.ink
import com.djvureader.app.ui.theme.onSurfaceAlpha
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Zoom is expressed as a multiple of fit-to-width. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val ZOOM_STEP = 1.5f

/** How long the page must settle before reading progress is written. */
private const val PROGRESS_SAVE_DELAY_MS = 600L

/** How long zoom must settle before pages are re-rendered at the new scale. */
private const val ZOOM_SETTLE_DELAY_MS = 150L

/**
 * Gap between pages. Narrow enough that the backdrop reads as a rule between
 * two sheets rather than as a gutter they float in.
 */
private val PAGE_GAP = 2.dp

/** The design's `transition: transform .22s cubic-bezier(.3,.7,.2,1)`. */
private const val CHROME_SLIDE_MS = 220
private val ChromeEasing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)

/**
 * The caliper marking each end of a selection - the same square mark the page
 * slider rides, rather than the teardrop Android draws.
 */
private val HandleWidth = 3.dp
private val HandleHeight = 14.dp

/**
 * How near an end a finger must land to take hold of it. Generous, because the
 * mark is deliberately small and a fingertip is not.
 */
private val HandleReach = 28.dp

/** The rule drawn under a hyperlink. The design's hairline, in accent. */
private val LinkRule = 1.5.dp

/** The copy block, sized like the go-to sheet's JUMP. */
private val CopyBlockWidth = 92.dp
private val CopyBlockHeight = 34.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars on every version. API 35 enforces this
        // anyway; asking for it explicitly means older devices hide and reveal
        // the bars the same way rather than resizing the window underneath us.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // A document may arrive from another app via ACTION_VIEW.
        val initialUri: Uri? = intent?.data
        setContent {
            DjvuTheme {
                // The clock and the navigation icons have to contrast with
                // whatever the app is drawing behind them, so they follow the
                // palette rather than being fixed dark.
                val darkChrome = Industry.isDark
                LaunchedEffect(darkChrome) {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkChrome
                        isAppearanceLightNavigationBars = !darkChrome
                    }
                }
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
            // Kept here rather than inside the library so it survives that
            // screen's own scrolling and recomposition, and so back can be
            // wired to it.
            var showAbout by rememberSaveable { mutableStateOf(false) }

            LibraryScreen(
                recents = recents,
                thumbnail = viewModel::thumbnail,
                onRequestThumbnail = viewModel::requestThumbnail,
                onPick = openDocument,
                onAbout = { showAbout = true },
                onOpenRecent = viewModel::openRecent,
                onRemoveRecent = viewModel::removeRecent,
            )

            if (showAbout) {
                BackHandler { showAbout = false }
                AboutScreen(onBack = { showAbout = false })
            }
        }

        is ReaderState.Loading -> Centered { CircularProgressIndicator() }

        is ReaderState.Error -> Centered {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                Text(
                    text = "Could not open document:\n${current.message}",
                    textAlign = TextAlign.Center,
                )
                // The same block the library opens files with. Nothing here is
                // a lesser action for being on an error screen.
                PrimaryAction(
                    icon = FolderIcon,
                    label = "CHOOSE ANOTHER FILE",
                    onClick = openDocument,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                )
            }
        }

        is ReaderState.Loaded -> DocumentReader(
            state = current,
            viewModel = viewModel,
            onBack = viewModel::closeDocument,
        )
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
    var showOutline by remember { mutableStateOf(false) }
    val outlineListState = rememberLazyListState()
    val outline by viewModel.outline.collectAsState()

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
    SystemBars(visible = chromeVisible || showSearch || showOutline)

    val selection by viewModel.selection.collectAsState()
    val clipboard = LocalClipboardManager.current
    // A link pressed but not yet followed, held while it is shown to the reader.
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    /** Puts the selection on the clipboard and lets it go. */
    fun copySelection() {
        scope.launch {
            val text = viewModel.selectedText()
            if (text != null) clipboard.setText(AnnotatedString(text))
            viewModel.clearSelection()
        }
    }

    // Whether the page is being shown as a search result rather than plainly:
    // marks on the words, and a bar that says so. Outlives the search screen,
    // because landing on a hit is the point of running the search.
    val searching = searchResults.query.length >= MIN_QUERY_LENGTH

    /** Puts the document back the way it reads without a search over it. */
    fun endSearch() {
        query = ""
        viewModel.clearSearch()
    }

    // Back leaves the selection, then the search screen, then the search, then
    // the document - one layer at a time, and the same order the top bar's
    // arrow goes in.
    BackHandler(enabled = showSearch || showOutline || searching || selection != null) {
        when {
            selection != null -> viewModel.clearSelection()
            showOutline -> showOutline = false
            showSearch -> showSearch = false
            else -> endSearch()
        }
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
        // A selection belongs to the page it was made on, and the reader is
        // leaving it.
        viewModel.clearSelection()
        scope.launch { listState.scrollToItem((page - 1).coerceIn(0, state.pageCount - 1)) }
    }

    /**
     * Follows the link under [point], or runs [otherwise] if there is none.
     *
     * The tap is in viewport coordinates and the pages are items in a list, so
     * finding what was pressed means asking the list which item covers that
     * height and where within it the point falls. Doing it here rather than on
     * each page is what lets a tap on nothing still reach the chrome: the page
     * cannot report a miss without also claiming the press.
     */
    fun followLinkOr(point: Offset, otherwise: () -> Unit) {
        val info = listState.layoutInfo
        // Item offsets are measured from the start of the content, the tap from
        // the top of the viewport, and the list's top padding separates the
        // two. Same correction the zoom anchoring makes.
        val padding = info.beforeContentPadding
        val item = info.visibleItemsInfo.firstOrNull { item ->
            val top = item.offset + padding
            point.y >= top && point.y < top + item.size
        }
        val width = viewport.width * zoom
        if (item == null || item.size <= 0 || width <= 0f) {
            otherwise()
            return
        }

        // Across, the pages sit in a horizontally scrolled strip, so the page's
        // own left edge is however far that strip has been dragged.
        val x = (point.x + horizontalScroll.value) / width
        val y = (point.y - (item.offset + padding)) / item.size
        if (x !in 0f..1f) {
            otherwise()
            return
        }

        scope.launch {
            val link = viewModel.links(item.index).firstOrNull { it.bounds.contains(x, y) }
            when (val target = link?.target) {
                null -> otherwise()
                is LinkTarget.Page -> goToPage(target.pageNumber + 1)
                // Never opened straight away. A link in a document is written
                // by whoever made the document, and where it goes is worth
                // seeing before going there.
                is LinkTarget.Url -> pendingUrl = target.url
            }
        }
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
                    // panel's alone. While text is being picked out the page
                    // belongs to the selection for the same reason.
                    enabled = !showSearch && !showOutline && selection == null,
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
                    onTap = { point -> followLinkOr(point) { chromeVisible = !chromeVisible } },
                )
                // A tap puts the selection down, wherever on the reader it
                // falls - the page it was made on, another page, or the space
                // beside them. Here rather than on the page itself, which
                // would only answer for taps that landed on that one page.
                //
                // The copy block and the selection's own ends are deeper in
                // this tree and claim their presses first, so neither of them
                // reads as a tap out here.
                .pointerInput(selection != null) {
                    if (selection == null) return@pointerInput
                    detectTapGestures { viewModel.clearSelection() }
                },
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
                        // A drag while text is being picked out belongs to the
                        // selection, not to the list. Nothing else can yield
                        // it: scrolling claims a drag the moment it travels.
                        userScrollEnabled = selection == null,
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
                                selection = selection?.takeIf { it.page == index },
                                onCopy = ::copySelection,
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
            hidden = chromeHidden,
            backLabel = if (searching) "Clear search" else "Back to library",
            onBack = if (searching) ({ endSearch() }) else onBack,
            onSearch = {
                viewModel.clearSelection()
                showSearch = true
            },
            onContents = if (outline.isEmpty()) {
                null
            } else {
                {
                    viewModel.clearSelection()
                    showOutline = true
                }
            },
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

        if (showOutline) {
            OutlineScreen(
                outline = outline,
                pageCount = state.pageCount,
                listState = outlineListState,
                onClose = { showOutline = false },
                onOpen = { entry ->
                    val target = entry.target
                    if (target is LinkTarget.Page) {
                        showOutline = false
                        // The chrome comes back with the page, so the reader
                        // can see where they landed.
                        chromeVisible = true
                        goToPage(target.pageNumber + 1)
                    }
                },
            )
        }

        // Covers the reader rather than sitting beside it, as the design draws
        // it - the page is still there underneath when search is dismissed.
        if (showSearch) {
            SearchScreen(
                query = query,
                results = searchResults,
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

    pendingUrl?.let { url ->
        val context = LocalContext.current
        OpenLinkSheet(
            url = url,
            onDismiss = { pendingUrl = null },
            onOpen = {
                pendingUrl = null
                // Nothing guarantees anything is installed that handles the
                // scheme, and a document can carry any scheme it likes.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
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
    selection: TextSelection?,
    onCopy: () -> Unit,
    viewModel: ReaderViewModel,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var pageSize by remember { mutableStateOf(IntSize.Zero) }

    // The marks a page carries, all shades of the one accent. Read here
    // because a draw lambda is not a composable scope.
    val accent = IndustryAccent
    val linkRuleColor = accent.copy(alpha = 0.55f)
    val highlightWash = accent.copy(alpha = 0.32f)
    val selectionWash = accent.copy(alpha = 0.30f)
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

    // A page's links never change while the document is open, so this is asked
    // once per page and answered from the view model's cache thereafter.
    val links by produceState(emptyList<PageLink>(), pageNumber) {
        value = viewModel.links(pageNumber)
    }

    // Where this page's search matches are, asked for only while a search is
    // showing and only for pages actually composed.
    val highlights by produceState(emptyList<PageRect>(), pageNumber, highlightKey) {
        value = if (highlightKey.isEmpty()) emptyList() else viewModel.highlights(pageNumber)
    }

    // Where the selection sits on this page. Recomputed on every change to it,
    // because a drag moves its end continuously.
    val selectionRects by produceState(emptyList<PageRect>(), pageNumber, selection) {
        value = if (selection == null) emptyList() else viewModel.selectionRects(pageNumber)
    }
    // Read inside gestures without restarting them: keying a `pointerInput` on
    // rectangles that change with every drag event would cancel the very drag
    // producing them.
    val currentRects = rememberUpdatedState(selectionRects)

    /** A point on this page, as fractions of it. */
    fun fraction(point: Offset, size: IntSize): Pair<Float, Float>? {
        if (size.width <= 0 || size.height <= 0) return null
        return (point.x / size.width).coerceIn(0f, 1f) to
            (point.y / size.height).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .onSizeChanged { pageSize = it }
            // Press and hold to take a word. The list is still scrollable at
            // this point, but a press that has not travelled is not a scroll,
            // so nothing has to be taken from it.
            .pointerInput(pageNumber) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        val (x, y) = fraction(point, size) ?: return@detectDragGesturesAfterLongPress
                        scope.launch {
                            if (viewModel.selectWordAt(pageNumber, x, y)) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val (x, y) = fraction(change.position, size) ?: return@detectDragGesturesAfterLongPress
                        scope.launch { viewModel.extendSelectionTo(x, y) }
                    },
                )
            }
            // Afterwards, either end can be taken and moved. Both ends are
            // measured from the word the selection started on, so it does not
            // matter which one is held - dragging out widens it and dragging
            // back past the start turns it round.
            .pointerInput(pageNumber, selection != null) {
                if (selection == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val ends = selectionEnds(currentRects.value, size) ?: return@awaitEachGesture
                    val reach = HandleReach.toPx()
                    val grabbed = ends.any { (down.position - it).getDistance() <= reach }
                    if (!grabbed) return@awaitEachGesture
                    down.consume()
                    drag(down.id) { change ->
                        change.consume()
                        val (x, y) = fraction(change.position, size) ?: return@drag
                        scope.launch { viewModel.extendSelectionTo(x, y) }
                    }
                }
            }
            // The design's `box-shadow: var(--shadow-sm)` is dropped: at this
            // gap it only smears into the rule between pages. The hairline edge
            // does the separating, so a scan that is white to its margin still
            // ends visibly.
            .background(Color.White)
            // The paper's own edge, so paper ink again rather than the app's.
            .border(1.dp, LightIndustry.ink(0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current == null) {
            Text(
                text = "${pageNumber + 1}",
                style = MaterialTheme.typography.labelMedium
                    .copy(fontFeatureSettings = TabularNums),
                // On the page's own white, so it takes paper ink rather than
                // the app's, which after dark would be white on white.
                color = LightIndustry.ink(0.45f),
            )
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${pageNumber + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // A link is invisible in the page's own picture, so it is ruled under.
        // A rule rather than a wash: the design marks things with rules, and a
        // link that covers a whole paragraph - which they often do - would
        // otherwise flood it with colour.
        if (links.isNotEmpty()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                links.forEach { link ->
                    val foot = link.bounds.bottom * size.height
                    drawRect(
                        color = linkRuleColor,
                        topLeft = Offset(link.bounds.left * size.width, foot - LinkRule.toPx()),
                        size = Size(
                            (link.bounds.right - link.bounds.left) * size.width,
                            LinkRule.toPx(),
                        ),
                    )
                }
            }
        }

        // Drawn over the page rather than into it, so a mark costs nothing to
        // put up or take down and never touches the cached rendering. The wash
        // is the design's own `<mark>`, which the hit list already uses.
        if (highlights.isNotEmpty()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                highlights.forEach { mark ->
                    drawRect(
                        color = highlightWash,
                        topLeft = Offset(mark.left * size.width, mark.top * size.height),
                        size = Size(
                            (mark.right - mark.left) * size.width,
                            (mark.bottom - mark.top) * size.height,
                        ),
                    )
                }
            }
        }

        // The selection is washed deeper than a search mark, and carries a
        // caliper at each end - the same square marks the page slider uses,
        // rather than the teardrops Android draws.
        if (selectionRects.isNotEmpty()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                selectionRects.forEach { picked ->
                    drawRect(
                        color = selectionWash,
                        topLeft = Offset(picked.left * size.width, picked.top * size.height),
                        size = Size(
                            (picked.right - picked.left) * size.width,
                            (picked.bottom - picked.top) * size.height,
                        ),
                    )
                }
                selectionEnds(selectionRects, IntSize(size.width.toInt(), size.height.toInt()))
                    ?.forEach { end ->
                        drawRect(
                            color = accent,
                            topLeft = Offset(end.x - HandleWidth.toPx() / 2f, end.y),
                            size = Size(HandleWidth.toPx(), HandleHeight.toPx()),
                        )
                    }
            }
        }

        if (selection != null && selectionRects.isNotEmpty() && pageSize.height > 0) {
            CopyBlock(
                onCopy = onCopy,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { copyBlockOffset(selectionRects, pageSize) },
            )
        }
    }
}

/**
 * The design's chrome, shrunk to one action and set down on the page. Its width
 * is fixed, as the go-to sheet's JUMP is, so the page can work out where to put
 * it without measuring it first.
 */
@Composable
private fun CopyBlock(onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .size(width = CopyBlockWidth, height = CopyBlockHeight)
            .background(IndustryBackground.copy(alpha = 0.96f))
            .border(1.dp, IndustryDivider)
            .clickable(onClickLabel = "Copy selected text", onClick = onCopy),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = "COPY", style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Where the copy block goes: under the selection, or over it when the
 * selection runs too near the foot of the page. Kept inside the page either
 * way, so it cannot end up somewhere there is nothing to press.
 */
private fun Density.copyBlockOffset(rects: List<PageRect>, page: IntSize): IntOffset {
    val width = CopyBlockWidth.toPx()
    val height = CopyBlockHeight.toPx()
    val gap = 8.dp.toPx()

    val below = rects.maxOf { it.bottom } * page.height + gap
    val above = rects.minOf { it.top } * page.height - height - gap
    val y = if (below + height <= page.height) below else above

    val centre = (rects.minOf { it.left } + rects.maxOf { it.right }) / 2f * page.width

    return IntOffset(
        x = (centre - width / 2f).coerceIn(0f, (page.width - width).coerceAtLeast(0f)).roundToInt(),
        y = y.coerceIn(0f, (page.height - height).coerceAtLeast(0f)).roundToInt(),
    )
}

/**
 * The two ends of a selection, as points on the page: the foot of its first
 * rectangle and the foot of its last.
 */
private fun selectionEnds(rects: List<PageRect>, size: IntSize): List<Offset>? {
    if (rects.isEmpty() || size.width <= 0 || size.height <= 0) return null
    val first = rects.first()
    val last = rects.last()
    return listOf(
        Offset(first.left * size.width, first.bottom * size.height),
        Offset(last.right * size.width, last.bottom * size.height),
    )
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
 * as a tap, reported with where it landed - the design toggles the chrome on
 * one, and a tap that finds a link follows it instead.
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
    onTap: (Offset) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
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
            !multiTouch && travel < viewConfiguration.touchSlop -> onTap(down.position)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
