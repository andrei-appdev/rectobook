package com.djvureader.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
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
        val pageIndex: Int,
        val pageSize: PageSize?,
        /** Null while the page is still rendering. */
        val bitmap: Bitmap? = null,
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

    private val documentLock = Mutex()
    private var document: DjvuDocument? = null
    private var title: String = ""
    private var pageIndex: Int = 0
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var renderJob: Job? = null

    fun openUri(uri: Uri) = openWith { DocumentImporter.import(getApplication(), uri) }

    fun openBundledSample() =
        openWith { DocumentImporter.importSample(getApplication(), SAMPLE_ASSET) }

    /**
     * The viewport drives render resolution, so a size change (rotation, window
     * resize) re-renders the current page.
     */
    fun onViewportChanged(width: Int, height: Int) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        if (_state.value is ReaderState.Loaded) {
            renderCurrentPage()
        }
    }

    private fun openWith(load: () -> LocalDocument) {
        renderJob?.cancel()
        _state.value = ReaderState.Loading
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val local = load()
                    documentLock.withLock {
                        document?.close()
                        document = null
                        val opened = DjvuDocument.open(local.file)
                            ?: error("DjVuLibre could not open ${local.displayName}")
                        document = opened
                        title = local.displayName
                        pageIndex = 0
                        opened.pageCount
                    }
                }
            }
            result.fold(
                onSuccess = { pageCount ->
                    _state.value = ReaderState.Loaded(
                        title = title,
                        pageCount = pageCount,
                        pageIndex = pageIndex,
                        pageSize = null,
                    )
                    renderCurrentPage()
                },
                onFailure = { error ->
                    _state.value = ReaderState.Error(error.message ?: error.toString())
                },
            )
        }
    }

    private fun renderCurrentPage() {
        val width = viewportWidth
        val height = viewportHeight
        if (width <= 0 || height <= 0) return

        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val page = pageIndex
            val rendered = runCatching {
                withContext(Dispatchers.IO) {
                    documentLock.withLock {
                        val doc = document ?: return@withLock null
                        val size = doc.pageSize(page)
                        size to doc.renderPageToFit(page, width, height)
                    }
                }
            }.getOrNull()

            val current = _state.value
            if (current is ReaderState.Loaded && rendered != null) {
                val (size, bitmap) = rendered
                _state.value = current.copy(
                    pageIndex = page,
                    pageSize = size,
                    bitmap = bitmap,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderJob?.cancel()
        val closing = document
        document = null
        // viewModelScope is cancelled by now, so close on a detached scope -
        // still under the lock, so an in-flight render finishes first.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            documentLock.withLock { closing?.close() }
        }
    }

    companion object {
        const val SAMPLE_ASSET = "sample.djvu"
    }
}