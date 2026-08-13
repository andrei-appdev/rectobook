package com.djvureader.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SamplePage()
                }
            }
        }
    }
}

/** Result of decoding the bundled sample document. */
private sealed interface PageState {
    data object Loading : PageState
    data class Failed(val reason: String) : PageState
    data class Loaded(val bitmap: Bitmap, val pageCount: Int, val size: PageSize?) : PageState
}

/**
 * Renders page 0 of the bundled sample document. This exists to prove the
 * asset -> DjVuLibre -> Bitmap -> Compose pipeline end to end; real navigation
 * and zoom come later.
 */
@Composable
fun SamplePage() {
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val targetWidth = constraints.maxWidth
        val targetHeight = constraints.maxHeight
        var state: PageState by remember { mutableStateOf(PageState.Loading) }

        LaunchedEffect(targetWidth, targetHeight) {
            // Decoding blocks, so keep it off the main thread.
            state = withContext(Dispatchers.IO) {
                loadSamplePage(context, targetWidth, targetHeight)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val current = state) {
                is PageState.Loading -> CircularProgressIndicator()

                is PageState.Failed -> Text(
                    text = "Failed to render sample:\n${current.reason}",
                    textAlign = TextAlign.Center,
                )

                is PageState.Loaded -> {
                    Text(
                        text = "${DjvuDocument.libraryVersion}  •  " +
                            "${current.pageCount} pages  •  " +
                            (current.size?.let { "${it.width}x${it.height} @ ${it.dpi}dpi" }
                                ?: "size unknown"),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Image(
                        bitmap = current.bitmap.asImageBitmap(),
                        contentDescription = "Page 1 of the sample document",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/**
 * Copies the bundled sample out of assets (DjVuLibre needs a real filesystem
 * path) and renders its first page scaled to fit.
 */
private fun loadSamplePage(context: Context, width: Int, height: Int): PageState {
    if (width <= 0 || height <= 0) {
        return PageState.Loading
    }
    return try {
        val file = File(context.cacheDir, SAMPLE_ASSET)
        if (!file.exists()) {
            context.assets.open(SAMPLE_ASSET).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val document = DjvuDocument.open(file)
            ?: return PageState.Failed("DjVuLibre could not open $SAMPLE_ASSET")
        document.use { doc ->
            val bitmap = doc.renderPageToFit(0, width, height)
                ?: return PageState.Failed("page 0 did not render")
            PageState.Loaded(bitmap = bitmap, pageCount = doc.pageCount, size = doc.pageSize(0))
        }
    } catch (error: Throwable) {
        PageState.Failed(error.toString())
    }
}

private const val SAMPLE_ASSET = "sample.djvu"
