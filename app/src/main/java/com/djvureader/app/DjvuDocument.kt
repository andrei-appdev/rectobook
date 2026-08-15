package com.djvureader.app

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

/** Page geometry in the page's own pixel space, at its native resolution. */
data class PageSize(val width: Int, val height: Int, val dpi: Int)

/**
 * An open DjVu document.
 *
 * Decoding happens synchronously on the calling thread, so open/render calls
 * belong on a background dispatcher. Instances own a native handle and must be
 * [close]d.
 */
class DjvuDocument private constructor(private var handle: Long) : Closeable {

    /** Number of pages, or 0 once closed. */
    val pageCount: Int
        get() = if (handle == 0L) 0 else DjvuNative.nativeGetPageCount(handle)

    /** Page geometry at native resolution, or null if it could not be read. */
    fun pageSize(pageNumber: Int): PageSize? {
        checkOpen()
        val info = DjvuNative.nativeGetPageInfo(handle, pageNumber) ?: return null
        return PageSize(width = info[0], height = info[1], dpi = info[2])
    }

    /**
     * The page's hidden text layer, or null if it could not be read.
     *
     * Empty when the page carries no text. DjVu scans usually have a text layer
     * from OCR, but nothing requires one, and a document made from images alone
     * returns empty for every page.
     */
    fun pageText(pageNumber: Int): String? {
        checkOpen()
        return DjvuNative.nativeGetPageText(handle, pageNumber)?.toString(Charsets.UTF_8)
    }

    /**
     * Renders [pageNumber] scaled to [width] x [height]. Returns null if the
     * page could not be decoded.
     */
    fun renderPage(pageNumber: Int, width: Int, height: Int): Bitmap? =
        renderRegion(pageNumber, width, height, 0, 0, width, height)

    /**
     * Renders a [regionWidth] x [regionHeight] region of [pageNumber], taken
     * from the page scaled to [pageWidth] x [pageHeight] and offset by
     * ([clipX], [clipY]).
     *
     * This is the primitive behind zoomed and tiled rendering: hold the region
     * at viewport size while growing the page size to zoom in.
     */
    fun renderRegion(
        pageNumber: Int,
        pageWidth: Int,
        pageHeight: Int,
        clipX: Int,
        clipY: Int,
        regionWidth: Int,
        regionHeight: Int,
    ): Bitmap? {
        checkOpen()
        require(pageWidth > 0 && pageHeight > 0) { "page size must be positive" }
        require(regionWidth > 0 && regionHeight > 0) { "region size must be positive" }
        val bitmap = Bitmap.createBitmap(regionWidth, regionHeight, Bitmap.Config.ARGB_8888)
        val ok = DjvuNative.nativeRenderPage(
            handle, pageNumber, bitmap, pageWidth, pageHeight, clipX, clipY,
        )
        if (!ok) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    /**
     * Renders [pageNumber] scaled to fit within [maxWidth] x [maxHeight] while
     * preserving the page's aspect ratio.
     */
    fun renderPageToFit(pageNumber: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        val size = pageSize(pageNumber) ?: return null
        if (size.width <= 0 || size.height <= 0) return null
        val scale = minOf(
            maxWidth.toFloat() / size.width,
            maxHeight.toFloat() / size.height,
        )
        val width = (size.width * scale).toInt().coerceAtLeast(1)
        val height = (size.height * scale).toInt().coerceAtLeast(1)
        return renderPage(pageNumber, width, height)
    }

    override fun close() {
        if (handle != 0L) {
            DjvuNative.nativeCloseDocument(handle)
            handle = 0L
        }
    }

    private fun checkOpen() {
        check(handle != 0L) { "document is closed" }
    }

    companion object {
        /** DjVuLibre version string, for diagnostics. */
        val libraryVersion: String get() = DjvuNative.nativeVersion()

        /** Opens a local file, or returns null if it is not a readable DjVu document. */
        fun open(file: File): DjvuDocument? = open(file.absolutePath)

        fun open(path: String): DjvuDocument? {
            val handle = DjvuNative.nativeOpenDocument(path)
            return if (handle == 0L) null else DjvuDocument(handle)
        }
    }
}