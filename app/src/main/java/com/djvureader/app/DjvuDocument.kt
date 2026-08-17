package com.djvureader.app

import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An open DjVu document, decoded by the vendored DjVuLibre.
 *
 * Decoding happens synchronously on the calling thread, so open/render calls
 * belong on a background dispatcher. Instances own a native handle and must be
 * [close]d.
 */
class DjvuDocument private constructor(private var handle: Long) : PagedDocument {

    override val format = DocumentFormat.Djvu

    override val pageCount: Int
        get() = if (handle == 0L) 0 else DjvuNative.nativeGetPageCount(handle)

    override fun pageSize(pageNumber: Int): PageSize? {
        checkOpen()
        val info = DjvuNative.nativeGetPageInfo(handle, pageNumber) ?: return null
        return PageSize(width = info[0], height = info[1], dpi = info[2])
    }

    /**
     * The page's hidden text layer, from whatever OCR pass produced the file.
     *
     * Words are joined with single spaces here rather than by DjVuLibre: asking
     * it for the text at page granularity returns one pre-joined string in
     * which some words run together and others do not, so "DjVu is an image"
     * can come back as "DjVuisanimage".
     */
    override fun pageText(pageNumber: Int): String? {
        checkOpen()
        val words = words(pageNumber) ?: return null
        return words.joinToString(" ") { it.text }
    }

    override fun textRects(pageNumber: Int, start: Int, end: Int): List<PageRect> {
        checkOpen()
        val words = words(pageNumber) ?: return emptyList()
        val size = pageSize(pageNumber) ?: return emptyList()
        if (size.width <= 0 || size.height <= 0) return emptyList()

        val rects = mutableListOf<PageRect>()
        var at = 0
        for (word in words) {
            val wordEnd = at + word.text.length
            // Any overlap at all: a match usually starts or ends mid-word, and
            // marking the whole word is what a highlighter does anyway.
            if (wordEnd > start && at < end) {
                rects += PageRect(
                    left = word.left.toFloat() / size.width,
                    // DjVu measures from the bottom of the page, Android from
                    // the top.
                    top = 1f - word.top.toFloat() / size.height,
                    right = word.right.toFloat() / size.width,
                    bottom = 1f - word.bottom.toFloat() / size.height,
                )
            }
            // The separating space this document's text was joined with.
            at = wordEnd + 1
            if (at > end) break
        }
        return rects
    }

    /**
     * The word under the point, by its start in the joined text.
     *
     * Only whole words are boxed in a DjVu, so this is as fine as the file
     * gets. A point that misses every box falls back to the nearest word on
     * the same line, within a tolerance of one line's height - a finger aiming
     * at a line usually lands in the leading above or below it, and refusing
     * to answer would make selection feel broken rather than precise.
     */
    override fun charAt(pageNumber: Int, x: Float, y: Float): Int? {
        checkOpen()
        val words = words(pageNumber) ?: return null
        val size = pageSize(pageNumber) ?: return null
        if (size.width <= 0 || size.height <= 0) return null

        // Into the page's own pixels, and back to DjVu's bottom-left origin.
        val px = x * size.width
        val py = (1f - y) * size.height

        var at = 0
        var nearest: Int? = null
        var nearestDistance = Float.MAX_VALUE
        for (word in words) {
            val height = (word.top - word.bottom).toFloat().coerceAtLeast(1f)
            val insideY = py >= word.bottom && py <= word.top
            if (insideY && px >= word.left && px <= word.right) return at

            // Distance to the box, counted as zero along an axis already
            // inside it, so a point beside a word beats one a line away.
            val dx = maxOf(word.left - px, 0f, px - word.right)
            val dy = maxOf(word.bottom - py, 0f, py - word.top)
            val distance = dx + dy * LINE_MISS_WEIGHT
            if (distance < nearestDistance && dy <= height) {
                nearestDistance = distance
                nearest = at
            }
            at += word.text.length + 1
        }
        return nearest
    }

    /** One word of hidden text, boxed in the page's own bottom-left pixels. */
    private class Word(
        val text: String,
        val left: Int,
        val bottom: Int,
        val right: Int,
        val top: Int,
    )

    /**
     * The page's words, decoded from what the JNI packed.
     *
     * Kept for the page most recently asked about: searching a page and then
     * marking the matches on it are two passes over the same data, and decoding
     * a page's text twice for that is wasteful.
     */
    private fun words(pageNumber: Int): List<Word>? {
        cachedWords?.let { (page, words) -> if (page == pageNumber) return words }
        val packed = DjvuNative.nativeGetPageWords(handle, pageNumber) ?: return null
        val words = decodeWords(packed)
        cachedWords = pageNumber to words
        return words
    }

    private var cachedWords: Pair<Int, List<Word>>? = null

    private fun decodeWords(packed: ByteArray): List<Word> {
        val buffer = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        val words = mutableListOf<Word>()
        while (buffer.remaining() >= WORD_HEADER_BYTES) {
            val left = buffer.int
            val bottom = buffer.int
            val right = buffer.int
            val top = buffer.int
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) break
            val text = ByteArray(length).also(buffer::get).toString(Charsets.UTF_8)
            words += Word(text = text, left = left, bottom = bottom, right = right, top = top)
        }
        return words
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
        /** Four box edges and a length, per word. */
        private const val WORD_HEADER_BYTES = 5 * Int.SIZE_BYTES

        /**
         * How much dearer a miss up or down is than one along the line. Lines
         * sit close together, so without this a point in the gap between two
         * lines would as readily take a word from the wrong one.
         */
        private const val LINE_MISS_WEIGHT = 4f

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