package com.djvureader.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A document may arrive from another app via ACTION_VIEW.
        val initialUri: Uri? = intent?.data
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReaderScreen(initialUri = initialUri)
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

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state) {
                is ReaderState.Empty -> EmptyState(
                    onPick = { picker.launch(arrayOf("*/*")) },
                    onSample = viewModel::openBundledSample,
                )

                is ReaderState.Loading -> Centered { CircularProgressIndicator() }

                is ReaderState.Error -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Could not open document:\n${current.message}",
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("Choose another file") }
                    }
                }

                is ReaderState.Loaded -> LoadedDocument(
                    state = current,
                    onPick = { picker.launch(arrayOf("*/*")) },
                    onViewportChanged = viewModel::onViewportChanged,
                )
            }
        }
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
private fun LoadedDocument(
    state: ReaderState.Loaded,
    onPick: () -> Unit,
    onViewportChanged: (Int, Int) -> Unit,
) {
    val details = buildString {
        append(state.title)
        append("  •  ")
        append("${state.pageCount} pages")
        state.pageSize?.let { append("  •  ${it.width}x${it.height} @ ${it.dpi}dpi") }
    }
    Text(
        text = details,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = onPick) { Text("Open another…") }

    // The page area's measured size decides render resolution.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { onViewportChanged(it.width, it.height) },
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = state.bitmap
        if (bitmap == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${state.pageIndex + 1} of ${state.pageCount}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}