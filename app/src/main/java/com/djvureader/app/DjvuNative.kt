package com.djvureader.app

import android.graphics.Bitmap

/**
 * Raw JNI surface over DjVuLibre. Handles are opaque native pointers; every
 * call that takes one blocks while DjVuLibre decodes, so none of these may be
 * called from the main thread. Prefer [DjvuDocument], which wraps this with
 * handle lifetime management.
 */
internal object DjvuNative {
    init {
        System.loadLibrary("djvureader")
    }

    /** Version string of the linked DjVuLibre build. */
    external fun nativeVersion(): String

    /** Returns an opaque document handle, or 0 if the file could not be opened. */
    external fun nativeOpenDocument(path: String): Long

    external fun nativeCloseDocument(handle: Long)

    external fun nativeGetPageCount(handle: Long): Int

    /** Returns `[width, height, dpi]` in the page's own pixels, or null on failure. */
    external fun nativeGetPageInfo(handle: Long, pageNumber: Int): IntArray?

    /**
     * UTF-8 bytes of a page's hidden text layer, empty if the page has none,
     * or null if it could not be read. Bytes rather than a String because the
     * JNI string call expects Java's modified UTF-8, which OCR output is under
     * no obligation to be.
     */
    external fun nativeGetPageText(handle: Long, pageNumber: Int): ByteArray?

    /**
     * Renders part of [pageNumber] into [bitmap], which must be ARGB_8888.
     *
     * The page is scaled to [pageWidth] x [pageHeight]; the region of that
     * scaled page at ([clipX], [clipY]) sized to the bitmap is what gets
     * written. Bitmap-sized page dimensions with a (0,0) clip render the whole
     * page.
     */
    external fun nativeRenderPage(
        handle: Long,
        pageNumber: Int,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
        clipX: Int,
        clipY: Int,
    ): Boolean
}