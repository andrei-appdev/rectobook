package com.djvureader.app

import android.graphics.Bitmap
import java.io.File

/**
 * An open DjVu document, decoded by the vendored DjVuLibre.
 *
 * Decoding happens synchronously on the calling thread, so open/render calls
 * belong on a background dispatcher. Instances own a native handle and must be
 * [close]d.
 */
class DjvuDocument private constructor(private var handle: Long) : PagedDocument {

    override val pageCount: Int
        get() = if (handle == 0L) 0 else DjvuNative.nativeGetPageCount(handle)

    override fun pageSize(pageNumber: Int): PageSize? {
        checkOpen()
        val info = DjvuNative.nativeGetPageInfo(handle, pageNumber) ?: return null
        return PageSize(width = info[0], height = info[1], dpi = info[2])
    }

    /**
     * The page's hidden text layer, from whatever OCR pass produced the file.
     */
    override fun pageText(pageNumber: Int): String? {
        checkOpen()
        return DjvuNative.nativeGetPageText(handle, pageNumber)?.toString(Charsets.UTF_8)
    }

    override fun renderPage(pageNumber: Int, width: Int, height: Int): Bitmap? =
        renderRegion(pageNumber, width, height, 0, 0, width, height)

    override fun renderRegion(
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