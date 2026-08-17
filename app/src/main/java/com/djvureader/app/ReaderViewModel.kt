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
import kotlinx.coroutines.delay
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
        val format: DocumentFormat,
        val pageCount: Int,
        /** width / height of the first page, used to size pages not yet measured. */
        val defaultAspectRatio: Float,
        /** Identity of the open document, used to key saved reading progress. */
        val documentKey: String,
        /** Page this document was last left on. */
        val initialPage: Int,
    ) : ReaderState
}

/** What opening a document yielded, before it becomes [ReaderState.Loaded]. */
private data class OpenedDocument(
    val format: DocumentFormat,
    val pageCount: Int,
    val firstPageSize: PageSize?,
    val displayName: String,
    val sizeBytes: Long,
)

/**
 * Owns the open [PagedDocument] across configuration changes and closes it in
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
    private var document: PagedDocument? = null
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

    private val thumbnailStore = ThumbnailStore(application)
    private val thumbnails = mutableStateMapOf<String, Bitmap>()

    /** Whether a thumbnail has been asked for, whatever came of it. */
    private val thumbnailsRequested = mutableSetOf<String>()

    /** First page of [document], or null until one has been rendered. */
    fun thumbnail(document: RecentDocument): Bitmap? = thumbnails[document.thumbnailKey]

    /**
     * Renders the first page of [document] at up to [width] x [height] pixels,
     * unless that has already been asked for.
     *
     * A document that fails stays in [thumbnailsRequested] rather than being
     * retried: the usual reason is that its permission has lapsed, which will
     * not fix itself, and the card falls back to the drawn glyph.
     */
    fun requestThumbnail(document: RecentDocument, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val key = document.thumbnailKey
        if (!thumbnailsRequested.add(key)) return
        viewModelScope.launch {
            thumbnailStore.thumbnail(document, width, height)?.let { thumbnails[key] = it }
        }
    }

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

    fun openRecent(recent: RecentDocument) = openUri(Uri.parse(recent.key))

    /**
     * Closes the open document and returns to the library. The native handle is
     * released under [documentLock] so a render still in flight finishes first.
     */
    fun closeDocument() {
        openJob?.cancel()
        clearSearch()
        val closing = document
        document = null
        title = ""
        _pageSizes.clear()
        pageTextCache.clear()
        linkCache.clear()
        clearSelection()
        _outline.value = emptyList()
        _state.value = ReaderState.Empty
        viewModelScope.launch(Dispatchers.IO) {
            documentLock.withLock {
                closing?.close()
                pageCache.evictAll()
                latestKeyByPage.clear()
            }
        }
    }

    /** Document whose saved position has already been restored. */
    private var restoredKey: String? = null

    /**
     * Answers true the first time it is asked about a document, and false
     * afterwards.
     *
     * Resuming belongs to opening a document, not to composing the reader.
     * Rotating recreates the activity and everything in it, but not this - so
     * without a claim the restore would run again on every rotation and haul
     * the reader back to wherever they resumed, discarding what they had read
     * since. The list keeps its own position across a configuration change, so
     * there is nothing to redo.
     */
    fun claimPositionRestore(documentKey: String): Boolean {
        if (restoredKey == documentKey) return false
        restoredKey = documentKey
        return true
    }

    /** Remembers how far through the open document the reader has got. */
    fun saveProgress(page: Int) {
        val loaded = _state.value as? ReaderState.Loaded ?: return
        viewModelScope.launch { library.saveProgress(loaded.documentKey, page) }
    }

    private fun openWith(key: String, load: () -> LocalDocument) {
        openJob?.cancel()
        clearSearch()
        clearSelection()
        pageTextCache.clear()
        linkCache.clear()
        _outline.value = emptyList()
        // Opening a document again - even the same one - resumes it again.
        restoredKey = null
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
                        val opened = openDocument(getApplication(), local.file)
                        document = opened
                        title = local.displayName
                        val firstPage = opened.pageSize(0)
                        OpenedDocument(
                            format = opened.format,
                            pageCount = opened.pageCount,
                            firstPageSize = firstPage,
                            displayName = local.displayName,
                            sizeBytes = local.file.length(),
                        )
                    }
                }
            }
            result.fold(
                onSuccess = { opened ->
                    _pageSizes.clear()
                    opened.firstPageSize?.let { _pageSizes[0] = it }
                    val lastIndex = (opened.pageCount - 1).coerceAtLeast(0)
                    val resumeAt = library.lastPage(key).coerceIn(0, lastIndex)
                    library.recordOpened(
                        key = key,
                        displayName = opened.displayName,
                        pageCount = opened.pageCount,
                        sizeBytes = opened.sizeBytes,
                    )
                    // After the state, not with it: a long outline should
                    // not hold the first page off the screen.
                    launch {
                        _outline.value = withContext(Dispatchers.IO) {
                            documentLock.withLock { document?.outline().orEmpty() }
                        }
                    }
                    _state.value = ReaderState.Loaded(
                        title = opened.displayName,
                        format = opened.format,
                        pageCount = opened.pageCount,
                        defaultAspectRatio = opened.firstPageSize?.aspectRatio()
                            ?: DEFAULT_ASPECT_RATIO,
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

    private val _search = MutableStateFlow(SearchResults())
    val search: StateFlow<SearchResults> = _search.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Page text already extracted, so a second search does not decode the
     * document again. Cleared with the document, since it belongs to it.
     */
    private val pageTextCache = ConcurrentHashMap<Int, String>()

    /**
     * Searches the open document for [query], publishing hits to [search] as
     * they are found rather than at the end - a long scan takes seconds, and
     * the first hits are usually the ones wanted.
     *
     * Each call cancels the one before it, so typing produces one search rather
     * than one per keystroke; the leading delay is what makes that a debounce.
     */
    fun search(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        val loaded = _state.value as? ReaderState.Loaded
        if (loaded == null || trimmed.length < MIN_QUERY_LENGTH) {
            _search.value = SearchResults(query = trimmed)
            return
        }

        highlightCache.clear()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val hits = mutableListOf<SearchHit>()
            var hasText = false
            var truncated = false

            fun publish(scanned: Int, running: Boolean) {
                _search.value = SearchResults(
                    query = trimmed,
                    hits = hits.toList(),
                    scannedPages = scanned,
                    pageCount = loaded.pageCount,
                    running = running,
                    hasTextLayer = hasText,
                    truncated = truncated,
                )
            }
            publish(scanned = 0, running = true)

            for (page in 0 until loaded.pageCount) {
                val text = pageText(page) ?: ""
                if (text.isNotBlank()) hasText = true
                hits += findHits(text, trimmed, page, limit = MAX_HITS - hits.size)
                if (hits.size >= MAX_HITS) {
                    truncated = true
                    publish(scanned = page + 1, running = false)
                    return@launch
                }
                publish(scanned = page + 1, running = true)
            }
            publish(scanned = loaded.pageCount, running = false)
        }
    }

    /** Drops the current search and stops any scan still running. */
    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        _search.value = SearchResults()
        highlightCache.clear()
    }

    /** Marks already worked out, per page, for the search now showing. */
    private val highlightCache = ConcurrentHashMap<Int, List<PageRect>>()

    /**
     * Where the current search's matches sit on [pageNumber], as fractions of
     * the page, or empty if nothing matches there.
     *
     * Worked out only for pages actually on screen: a match's position is not
     * in the text the scan already read, so answering for every page would mean
     * decoding the whole document again to mark the handful being looked at.
     */
    suspend fun highlights(pageNumber: Int): List<PageRect> {
        val results = _search.value
        if (results.query.length < MIN_QUERY_LENGTH) return emptyList()
        val onPage = results.hits.filter { it.page == pageNumber }
        if (onPage.isEmpty()) return emptyList()
        highlightCache[pageNumber]?.let { return it }

        val rects = withContext(Dispatchers.IO) {
            documentLock.withLock {
                val open = document ?: return@withLock emptyList()
                onPage.flatMap { open.textRects(pageNumber, it.start, it.end) }
            }
        }
        // Not while the scan is still running: this page may yet gain matches.
        if (!results.running) highlightCache[pageNumber] = rects
        return rects
    }

    /**
     * A page's links, worked out once and kept.
     *
     * Unlike selection marks, these never change while a document is open, and
     * every page that is drawn asks for them - so they are cached, and pages
     * with none cache the empty answer rather than asking again on each scroll.
     */
    private val linkCache = ConcurrentHashMap<Int, List<PageLink>>()

    suspend fun links(pageNumber: Int): List<PageLink> {
        linkCache[pageNumber]?.let { return it }
        val links = withContext(Dispatchers.IO) {
            documentLock.withLock { document?.links(pageNumber).orEmpty() }
        }
        linkCache[pageNumber] = links
        return links
    }

    private val _outline = MutableStateFlow<List<OutlineEntry>>(emptyList())

    /**
     * The open document's table of contents, empty until it has been read and
     * for documents that carry none.
     *
     * Observable rather than fetched on demand, because the reader's chrome
     * shows a way in only when there is something to show.
     */
    val outline: StateFlow<List<OutlineEntry>> = _outline.asStateFlow()

    private val _selection = MutableStateFlow<TextSelection?>(null)

    /** The text currently picked out on a page, or null if none is. */
    val selection: StateFlow<TextSelection?> = _selection.asStateFlow()

    /**
     * Selects the word at ([x], [y]) on [pageNumber], both fractions of the
     * page. Returns whether anything was there to select.
     */
    suspend fun selectWordAt(pageNumber: Int, x: Float, y: Float): Boolean {
        val picked = wordAt(pageNumber, x, y) ?: return false
        _selection.value = TextSelection(
            page = pageNumber,
            anchor = picked,
            start = picked.first,
            end = picked.last + 1,
        )
        return true
    }

    /**
     * Drags one end of the selection to ([x], [y]).
     *
     * The other end stays where the selection was first made, so dragging back
     * past it turns the selection round rather than emptying it - which is what
     * a hand does when it overshoots.
     */
    suspend fun extendSelectionTo(x: Float, y: Float) {
        val current = _selection.value ?: return
        val picked = wordAt(current.page, x, y) ?: return
        val anchor = current.anchor
        _selection.value = current.copy(
            start = minOf(anchor.first, picked.first),
            end = maxOf(anchor.last, picked.last) + 1,
        )
    }

    fun clearSelection() {
        _selection.value = null
    }

    /** The word at a point, as a range into the page's text. */
    private suspend fun wordAt(pageNumber: Int, x: Float, y: Float): IntRange? {
        val text = pageText(pageNumber) ?: return null
        if (text.isEmpty()) return null
        val index = withContext(Dispatchers.IO) {
            documentLock.withLock { document?.charAt(pageNumber, x, y) }
        } ?: return null
        return wordAround(text, index.coerceIn(0, text.length - 1))
    }

    /**
     * Where the selection sits on [pageNumber], as fractions of the page.
     *
     * Not cached, unlike search marks: a selection changes with every drag
     * event, so there would never be a second reader for a cached answer.
     */
    suspend fun selectionRects(pageNumber: Int): List<PageRect> {
        val current = _selection.value ?: return emptyList()
        if (current.page != pageNumber || current.isEmpty) return emptyList()
        return withContext(Dispatchers.IO) {
            documentLock.withLock {
                document?.textRects(pageNumber, current.start, current.end).orEmpty()
            }
        }
    }

    /** What is selected, ready for the clipboard, or null if nothing is. */
    suspend fun selectedText(): String? {
        val current = _selection.value ?: return null
        val text = pageText(current.page) ?: return null
        if (current.start >= text.length) return null
        return text.substring(current.start, current.end.coerceAtMost(text.length))
            .takeIf { it.isNotBlank() }
    }

    /**
     * A page's hidden text, from the cache when possible.
     *
     * Held under [documentLock] like every other document call, but taken per
     * page rather than for the whole search, so a scan does not stop the pages
     * behind it from rendering.
     */
    private suspend fun pageText(pageNumber: Int): String? {
        pageTextCache[pageNumber]?.let { return it }
        val text = withContext(Dispatchers.IO) {
            documentLock.withLock { document?.pageText(pageNumber) }
        }
        if (text != null) pageTextCache[pageNumber] = text
        return text
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
        /** Fallback shape (roughly A4 portrait) for pages not yet measured. */
        private const val DEFAULT_ASPECT_RATIO = 0.72f

        /**
         * How long typing must pause before a search starts. Long enough that
         * a word typed at speed scans once, short enough not to feel held back.
         */
        private const val SEARCH_DEBOUNCE_MS = 250L

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
