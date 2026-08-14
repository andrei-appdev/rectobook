package com.djvureader.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Zoom is expressed as a multiple of fit-to-width. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val ZOOM_STEP = 1.5f

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
        is ReaderState.Empty -> EmptyState(
            onPick = openDocument,
            onSample = viewModel::openBundledSample,
        )

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
private fun EmptyState(onPick: () -> Unit, onSample: () -> Unit) {
    Centered {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No document open", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onPick) { Text("Open document…") }
            TextButton(onClick = onSample) { Text("Open bundled sample") }
        }
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
        val horizontalScroll = rememberScrollState()

        LaunchedEffect(zoom) {
            if (zoom == MIN_ZOOM) horizontalScroll.scrollTo(0)
        }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val contentWidth = (constraints.maxWidth * zoom).toInt()
            val contentWidthDp = with(LocalDensity.current) { contentWidth.toDp() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Panning is only meaningful once pages overflow the viewport.
                    .horizontalScroll(horizontalScroll, enabled = zoom > MIN_ZOOM)
                    .pinchToZoom { scale ->
                        zoom = (zoom * scale).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(contentWidthDp).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = state.pageCount, key = { it }) { index ->
                        // Falls back to the document's shape until this page is
                        // measured, so the list does not reflow while scrolling.
                        val aspectRatio = viewModel.pageSizes[index]?.aspectRatio()
                            ?: state.defaultAspectRatio
                        PageView(
                            pageNumber = index,
                            renderWidth = contentWidth,
                            aspectRatio = aspectRatio,
                            viewModel = viewModel,
                        )
                    }
                }
            }

            ZoomControls(
                zoom = zoom,
                onZoomIn = { zoom = (zoom * ZOOM_STEP).coerceAtMost(MAX_ZOOM) },
                onZoomOut = { zoom = (zoom / ZOOM_STEP).coerceAtLeast(MIN_ZOOM) },
                onReset = { zoom = MIN_ZOOM },
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
    var bitmap by remember(pageNumber, renderWidth) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageNumber, renderWidth) {
        bitmap = viewModel.renderPage(pageNumber, renderWidth)
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
    val page = text.toIntOrNull()
    val valid = page != null && page in 1..pageCount

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
            TextButton(onClick = { if (valid) onJump(page!!) }, enabled = valid) {
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
 * Reports pinch scale changes without consuming single-pointer drags, so the
 * page list keeps scrolling vertically while pinch still zooms.
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var hasPointers = true
        while (hasPointers) {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                val scale = event.calculateZoom()
                if (scale != 1f) {
                    onZoom(scale)
                    event.changes.forEach { it.consume() }
                }
            }
            hasPointers = event.changes.any { it.pressed }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
