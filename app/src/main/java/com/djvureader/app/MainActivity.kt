package com.djvureader.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A document may arrive from another app via ACTION_VIEW.
        val initialUri: Uri? = intent?.data
        setContent {
            MaterialTheme {
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
            EmptyState(
                recents = recents,
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
            onPick = openDocument,
        )
    }
}

@Composable
private fun EmptyState(
    recents: List<RecentDocument>,
    onPick: () -> Unit,
    onSample: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No document open", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onPick, modifier = Modifier.padding(top = 8.dp)) {
            Text("Open document…")
        }
        TextButton(onClick = onSample) { Text("Open bundled sample") }

        if (recents.isNotEmpty()) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(count = recents.size, key = { recents[it].key }) { index ->
                    val document = recents[index]
                    RecentRow(document = document, onClick = { onOpenRecent(document) })
                }
            }
        }
    }
}

@Composable
private fun RecentRow(document: RecentDocument, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = document.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
        Text(
            text = "Page ${document.lastPage + 1}",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun DocumentReader(
    state: ReaderState.Loaded,
    viewModel: ReaderViewModel,
    onPick: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showJumpDialog by remember { mutableStateOf(false) }

    // The topmost visible item is the page the reader considers themselves on.
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    // Resume where this document was left, then keep saving as it is read.
    // Both live in one effect so the restore completes before the first save,
    // otherwise the initial position would overwrite the stored page with 0.
    LaunchedEffect(state.documentKey) {
        if (state.initialPage > 0) {
            listState.scrollToItem(state.initialPage)
        }
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { page ->
                delay(PROGRESS_SAVE_DELAY_MS)
                viewModel.saveProgress(page)
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ReaderBar(
            title = state.title,
            currentPage = currentPage,
            pageCount = state.pageCount,
            onPick = onPick,
            onJump = { showJumpDialog = true },
        )

        // Zooming widens the content rather than scaling the list: pages are
        // re-rendered at the new width, so they stay sharp and the list keeps
        // correct scroll extents.
        var zoom by remember { mutableFloatStateOf(MIN_ZOOM) }
        // Pages lay out at `zoom` immediately but are re-rendered only at
        // `renderZoom`. A pinch changes zoom every frame, and re-rendering on
        // each one would cancel the previous decode over and over.
        var renderZoom by remember { mutableFloatStateOf(MIN_ZOOM) }
        // Live pinch state: scale applied on the GPU until the fingers lift.
        var gestureScale by remember { mutableFloatStateOf(1f) }
        var gestureFocus by remember { mutableStateOf(Offset.Zero) }
        val horizontalScroll = rememberScrollState()

        LaunchedEffect(zoom) {
            if (zoom == MIN_ZOOM) horizontalScroll.scrollTo(0)
            delay(ZOOM_SETTLE_DELAY_MS)
            renderZoom = zoom
        }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val contentWidth = (constraints.maxWidth * zoom).toInt()
            val renderWidth = (constraints.maxWidth * renderZoom).toInt()
            val contentWidthDp = with(LocalDensity.current) { contentWidth.toDp() }

            /**
             * Zooms by [scale] about [focus], given in viewport coordinates.
             *
             * Both scroll positions are absolute pixel offsets, so growing the
             * content without adjusting them slides the page towards its top
             * left corner. These corrections keep whatever sits under [focus]
             * where it is.
             */
            fun zoomAbout(scale: Float, focus: Offset) {
                val target = (zoom * scale).coerceIn(MIN_ZOOM, MAX_ZOOM)
                val factor = target / zoom
                if (factor == 1f) return

                // Horizontal: content point under the focus is scrollX + focus.x.
                val targetScrollX = (factor * (horizontalScroll.value + focus.x) - focus.x)
                    .roundToInt()

                // Vertical: LazyListState is item-relative, so anchor to the
                // fraction of the item the focus falls in - that fraction is
                // scale-independent.
                val visible = listState.layoutInfo.visibleItemsInfo
                val anchorItem = visible.firstOrNull {
                    focus.y >= it.offset && focus.y < it.offset + it.size
                } ?: visible.firstOrNull()
                val verticalAnchor = anchorItem?.takeIf { it.size > 0 }?.let { item ->
                    val fraction = ((focus.y - item.offset) / item.size).coerceIn(0f, 1f)
                    item.index to (fraction * item.size * factor - focus.y).roundToInt()
                }

                zoom = target
                scope.launch {
                    // Let the list re-measure at the new width before scrolling,
                    // otherwise the scroll clamps against stale bounds.
                    withFrameNanos { }
                    verticalAnchor?.let { (index, offset) ->
                        listState.scrollToItem(index, offset.coerceAtLeast(0))
                    }
                    horizontalScroll.scrollTo(
                        targetScrollX.coerceIn(0, horizontalScroll.maxValue),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The gesture sits outside the scrolling box so its centroid
                    // arrives in viewport coordinates rather than content ones.
                    .pinchToZoom(
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.width(contentWidthDp).fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(count = state.pageCount, key = { it }) { index ->
                            // Falls back to the document's shape until this page
                            // is measured, so the list does not reflow while
                            // scrolling.
                            val aspectRatio = viewModel.pageSizes[index]?.aspectRatio()
                                ?: state.defaultAspectRatio
                            PageView(
                                pageNumber = index,
                                renderWidth = renderWidth,
                                aspectRatio = aspectRatio,
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }

            // Buttons zoom about the middle of the viewport, so they anchor the
            // same way a pinch does.
            val viewportCentre = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
            ZoomControls(
                zoom = zoom,
                onZoomIn = { zoomAbout(ZOOM_STEP, viewportCentre) },
                onZoomOut = { zoomAbout(1f / ZOOM_STEP, viewportCentre) },
                onReset = { zoomAbout(MIN_ZOOM / zoom, viewportCentre) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            pageCount = state.pageCount,
            onDismiss = { showJumpDialog = false },
            onJump = { page ->
                showJumpDialog = false
                scope.launch { listState.scrollToItem(page - 1) }
            },
        )
    }
}

@Composable
private fun ReaderBar(
    title: String,
    currentPage: Int,
    pageCount: Int,
    onPick: () -> Unit,
    onJump: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = "Page $currentPage of $pageCount",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextButton(onClick = onJump) { Text("Go to…") }
        TextButton(onClick = onPick) { Text("Open") }
    }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current == null) {
            Text(
                text = "${pageNumber + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
            )
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${pageNumber + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                label = { Text("1 – $pageCount") },
            )
        },
        confirmButton = {
            TextButton(onClick = { page?.let(onJump) }, enabled = page != null) {
                Text("Go")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ZoomControls(
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 3.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onZoomOut, enabled = zoom > MIN_ZOOM) { Text("−") }
            TextButton(onClick = onReset) { Text("${(zoom * 100).roundToInt()}%") }
            TextButton(onClick = onZoomIn, enabled = zoom < MAX_ZOOM) { Text("+") }
        }
    }
}

/**
 * Reports pinch scale changes and the point they are centred on, without
 * consuming single-pointer drags, so the page list keeps scrolling vertically
 * while pinch still zooms.
 */
private fun Modifier.pinchToZoom(
    onZoom: (scale: Float, focus: Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoomed = false
        var hasPointers = true
        while (hasPointers) {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                val scale = event.calculateZoom()
                val focus = event.calculateCentroid()
                if (scale != 1f && focus != Offset.Unspecified) {
                    onZoom(scale, focus)
                    zoomed = true
                    event.changes.forEach { it.consume() }
                }
            }
            hasPointers = event.changes.any { it.pressed }
        }
        if (zoomed) onEnd()
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
