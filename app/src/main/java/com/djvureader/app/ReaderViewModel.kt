package com.djvureader.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the reader screen should be showing. */
sealed interface ReaderState {
    data object Empty : ReaderState

    data object Loading : ReaderState

    data class Error(val message: String) : ReaderState

    data class Loaded(
        val title: String,
        val pageCount: Int,
        /** width / height of the first page, used to size pages not yet measured. */
        val defaultAspectRatio: Float,
    ) : ReaderState
}

/**
 * Owns the open [DjvuDocument] across configuration changes and closes it in
 * [onCleared] - the native handle is not garbage collected.
 *
 * All document access is serialised through [documentLock], because DjVuLibre
 * calls block and a render in flight must not race the document being closed.
 */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Empty)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    /**
     * Page geometry, observable so the list can reserve the right height as
     * soon as a page is measured. Pages start at the document's default aspect
     * ratio and settle once their real size is known, which keeps scrolling
     * stable instead of reflowing under the user.
     */
    private val _pageSizes = mutableStateMapOf<Int, PageSize>()
    val pageSizes: Map<Int, PageSize> get() = _pageSizes

    private val documentLock = Mutex()
    private var document: DjvuDocument? = null
    private var title: String = ""
    private var openJob: Job? = null

    /**
     * Rendered pages, keyed by page and render width so a viewport change does
     * not serve stale resolutions.
     *
     * Evicted bitmaps are deliberately *not* recycled: a page can still be on
     * screen when it falls out of the cache, and drawing a recycled bitmap
     * crashes. Since API 26 the pixels live on the native heap and are
     * reclaimed by GC, so dropping the reference is enough.
     */
    private val pageCache = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun openUri(uri: Uri) = openWith { DocumentImporter.import(getApplication(), uri) }

    fun openBundledSample() =
        openWith { DocumentImporter.importSample(getApplication(), SAMPLE_ASSET) }

    private fun openWith(load: () -> LocalDocument) {
        openJob?.cancel()
        _state.value = ReaderState.Loading
        openJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val local = load()
                    documentLock.withLock {
                        document?.close()
                        document = null
                        pageCache.evictAll()
                        val opened = DjvuDocument.open(local.file)
                            ?: error("DjVuLibre could not open ${local.displayName}")
                        document = opened
                        title = local.displayName
                        val firstPage = opened.pageSize(0)
                        Triple(opened.pageCount, firstPage, local.displayName)
                    }
                }
            }
            result.fold(
                onSuccess = { (pageCount, firstPage, name) ->
                    _pageSizes.clear()
                    firstPage?.let { _pageSizes[0] = it }
                    _state.value = ReaderState.Loaded(
                        title = name,
                        pageCount = pageCount,
                        defaultAspectRatio = firstPage?.aspectRatio() ?: DEFAULT_ASPECT_RATIO,
                    )
                },
                onFailure = { error ->
                    _state.value = ReaderState.Error(error.message ?: error.toString())
                },
            )
        }
    }

    /**
     * Renders [pageNumber] at [width] pixels wide, preserving its aspect ratio.
     * Cancelling the caller (scrolling the page off screen) abandons the result.
     */
    suspend fun renderPage(pageNumber: Int, width: Int): Bitmap? {
        if (width <= 0) return null
        val key = cacheKey(pageNumber, width)
        pageCache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            documentLock.withLock {
                val doc = document ?: return@withLock null
                val size = doc.pageSize(pageNumber) ?: return@withLock null
                val height = (width.toLong() * size.height / size.width)
                    .toInt()
                    .coerceAtLeast(1)
                size to doc.renderPage(pageNumber, width, height)
            }
        } ?: return null

        val (size, rendered) = bitmap
        _pageSizes[pageNumber] = size
        if (rendered != null) {
            pageCache.put(key, rendered)
        }
        return rendered
    }

    override fun onCleared() {
        super.onCleared()
        openJob?.cancel()
        val closing = document
        document = null
        pageCache.evictAll()
        // viewModelScope is cancelled by now, so close on a detached scope -
        // still under the lock, so an in-flight render finishes first.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            documentLock.withLock { closing?.close() }
        }
    }

    private fun cacheKey(pageNumber: Int, width: Int) = "$pageNumber@$width"

    companion object {
        const val SAMPLE_ASSET = "sample.djvu"

        /** Fallback shape (roughly A4 portrait) for pages not yet measured. */
        private const val DEFAULT_ASPECT_RATIO = 0.72f

        /**
         * A page rendered to a phone's width runs to several megabytes, so the
         * cache is sized off the heap rather than a fixed page count.
         */
        private fun cacheSizeBytes(): Int {
            val quarterHeap = Runtime.getRuntime().maxMemory() / 4
            return quarterHeap.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}

fun PageSize.aspectRatio(): Float =
    if (height > 0) width.toFloat() / height.toFloat() else 1f
