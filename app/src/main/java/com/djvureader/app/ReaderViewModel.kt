package com.djvureader.app

import android.app.Application
import android.content.Intent
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

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
        /** Identity of the open document, used to key saved reading progress. */
        val documentKey: String,
        /** Page this document was last left on. */
        val initialPage: Int,
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

    /** Most recent cache key per page, so any rendering of a page can be found. */
    private val latestKeyByPage = ConcurrentHashMap<Int, String>()

    /**
     * Whatever rendering of [pageNumber] is already in memory, at any width, or
     * null if the page has never been rendered.
     *
     * Deliberately not a suspend function: [renderPage] has to queue behind
     * [documentLock], so waiting on it - even for a cache hit - leaves the page
     * blank for as long as other renders are in flight. The UI calls this to
     * show the previous rendering immediately and swap in the sharper one when
     * it arrives.
     */
    fun cachedPage(pageNumber: Int): Bitmap? =
        latestKeyByPage[pageNumber]?.let { pageCache.get(it) }

    private val library = LibraryStore(application)

    val recents: StateFlow<List<RecentDocument>> = library.recents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun openUri(uri: Uri) {
        // Without a persisted grant the URI stops working once this process
        // dies, which would leave every recent entry unopenable.
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        openWith(key = uri.toString()) { DocumentImporter.import(getApplication(), uri) }
    }

    fun openBundledSample() =
        openWith(key = "asset:$SAMPLE_ASSET") {
            DocumentImporter.importSample(getApplication(), SAMPLE_ASSET)
        }

    fun openRecent(recent: RecentDocument) {
        if (recent.key.startsWith("asset:")) openBundledSample() else openUri(Uri.parse(recent.key))
    }

    /** Remembers how far through the open document the reader has got. */
    fun saveProgress(page: Int) {
        val loaded = _state.value as? ReaderState.Loaded ?: return
        viewModelScope.launch { library.saveProgress(loaded.documentKey, page) }
    }

    private fun openWith(key: String, load: () -> LocalDocument) {
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
                        latestKeyByPage.clear()
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
                    val resumeAt = library.lastPage(key).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    library.recordOpened(key, name)
                    _state.value = ReaderState.Loaded(
                        title = name,
                        pageCount = pageCount,
                        defaultAspectRatio = firstPage?.aspectRatio() ?: DEFAULT_ASPECT_RATIO,
                        documentKey = key,
                        initialPage = resumeAt,
                    )
                },
                onFailure = { error ->
                    _state.value = ReaderState.Error(error.message ?: error.toString())
                },
            )
        }
    }

    /**
     * Renders [pageNumber] at up to [width] pixels wide, preserving its aspect
     * ratio. Cancelling the caller (scrolling the page off screen) abandons the
     * result.
     *
     * The width is capped so a single page cannot exceed [MAX_RENDER_PIXELS];
     * at high zoom the caller displays the result upscaled rather than the app
     * allocating an unbounded bitmap.
     */
    suspend fun renderPage(pageNumber: Int, width: Int): Bitmap? {
        if (width <= 0) return null

        // Fast path: geometry already known, so the capped width is too.
        _pageSizes[pageNumber]?.let { known ->
            pageCache.get(cacheKey(pageNumber, cappedWidth(width, known)))?.let { return it }
        }

        val result = withContext(Dispatchers.IO) {
            documentLock.withLock {
                val doc = document ?: return@withLock null
                val size = doc.pageSize(pageNumber) ?: return@withLock null
                val renderWidth = cappedWidth(width, size)
                pageCache.get(cacheKey(pageNumber, renderWidth))?.let { return@withLock size to it }
                val height = (renderWidth.toLong() * size.height / size.width)
                    .toInt()
                    .coerceAtLeast(1)
                size to doc.renderPage(pageNumber, renderWidth, height)
            }
        } ?: return null

        val (size, rendered) = result
        _pageSizes[pageNumber] = size
        if (rendered != null) {
            val key = cacheKey(pageNumber, cappedWidth(width, size))
            pageCache.put(key, rendered)
            latestKeyByPage[pageNumber] = key
        }
        return rendered
    }

    /**
     * Largest width whose rendered page stays within [MAX_RENDER_PIXELS].
     * Given height = width / aspect, area = width^2 / aspect.
     */
    private fun cappedWidth(requested: Int, size: PageSize): Int {
        val aspect = size.aspectRatio()
        val limit = sqrt(MAX_RENDER_PIXELS.toDouble() * aspect).toInt()
        return requested.coerceAtMost(limit).coerceAtLeast(1)
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
         * Ceiling on a single rendered page, about 24 MB at ARGB_8888. Without
         * it, zooming a 400dpi scan would ask for bitmaps of hundreds of
         * megabytes. Region rendering (see DjvuDocument.renderRegion) is the
         * real fix and would let this go away.
         */
        private const val MAX_RENDER_PIXELS = 6_000_000

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
