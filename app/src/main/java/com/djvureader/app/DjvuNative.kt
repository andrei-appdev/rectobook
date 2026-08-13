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

    /** Renders [pageNumber] scaled to fill [bitmap], which must be ARGB_8888. */
    external fun nativeRenderPage(handle: Long, pageNumber: Int, bitmap: Bitmap): Boolean
}