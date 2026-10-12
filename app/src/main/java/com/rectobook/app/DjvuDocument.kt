package com.rectobook.app

import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

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
            // Any overlap at all, clipped to what is covered for an entry that
            // holds a whole line rather than one word.
            if (wordEnd > start && at < end) {
                val (from, to) = word.span(start - at, end - at)
                val boxWidth = (word.right - word.left).toFloat()
                rects += PageRect(
                    left = (word.left + from * boxWidth) / size.width,
                    // DjVu measures from the bottom of the page, Android from
                    // the top.
                    top = 1f - word.top.toFloat() / size.height,
                    right = (word.left + to * boxWidth) / size.width,
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
            if (insideY && px >= word.left && px <= word.right) return at + word.offsetAt(px)

            // Distance to the box, counted as zero along an axis already
            // inside it, so a point beside a word beats one a line away.
            val dx = maxOf(word.left - px, 0f, px - word.right)
            val dy = maxOf(word.bottom - py, 0f, py - word.top)
            val distance = dx + dy * LINE_MISS_WEIGHT
            if (distance < nearestDistance && dy <= height) {
                nearestDistance = distance
                nearest = at + word.offsetAt(px)
            }
            at += word.text.length + 1
        }
        return nearest
    }

    /**
     * The page's hyperlinks, from its annotation chunk.
     *
     * Link coordinates need the page's rotation and the hidden text's do not.
     * ddjvuapi says so itself, under a "[brain damage warning]": maparea
     * coordinates are relative to the rotated page while the text is relative
     * to the unrotated one. So this cannot share the flip [textRects] uses.
     */
    override fun links(pageNumber: Int): List<PageLink> {
        checkOpen()
        val packed = DjvuNative.nativeGetPageLinks(handle, pageNumber) ?: return emptyList()
        val buffer = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        if (buffer.remaining() < Int.SIZE_BYTES) return emptyList()
        val rotation = buffer.int

        val size = pageSize(pageNumber) ?: return emptyList()
        if (size.width <= 0 || size.height <= 0) return emptyList()
        // A quarter turn either way puts the page on its side, so the frame the
        // link was measured in is the page's own dimensions swapped.
        val quarterTurned = rotation == 1 || rotation == 3
        val frameWidth = (if (quarterTurned) size.height else size.width).toFloat()
        val frameHeight = (if (quarterTurned) size.width else size.height).toFloat()

        val links = mutableListOf<PageLink>()
        while (buffer.remaining() >= LINK_HEADER_BYTES) {
            val left = buffer.int
            val bottom = buffer.int
            val right = buffer.int
            val top = buffer.int
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) break
            val target = ByteArray(length).also(buffer::get).decodeText()

            val bounds = PageRect(
                left = left / frameWidth,
                // Bottom-left origin, as everywhere else in a DjVu.
                top = 1f - top / frameHeight,
                right = right / frameWidth,
                bottom = 1f - bottom / frameHeight,
            )
            linkTarget(target)?.let { links += PageLink(bounds, it) }
        }
        return links
    }

    /**
     * The document's outline, rebuilt from the flat run the JNI packs.
     *
     * Each entry carries its depth, and the entries arrive depth first, so a
     * stack of the open parents is enough: anything deeper than the last entry
     * belongs to it, anything shallower closes it.
     */
    override fun outline(): List<OutlineEntry> {
        checkOpen()
        val packed = DjvuNative.nativeGetOutline(handle) ?: return emptyList()
        val buffer = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)

        val roots = mutableListOf<OutlineEntry>()
        // Children of each open parent, innermost last. Entries are immutable,
        // so they are built on the way back out rather than as they are read.
        val open = mutableListOf<Pair<OutlineEntry, MutableList<OutlineEntry>>>()

        fun closeTo(depth: Int) {
            while (open.size > depth) {
                val (entry, children) = open.removeAt(open.lastIndex)
                val built = entry.copy(children = children.toList())
                if (open.isEmpty()) roots += built else open.last().second += built
            }
        }

        while (buffer.remaining() >= Int.SIZE_BYTES) {
            val depth = buffer.int
            val target = buffer.readString() ?: break
            val title = buffer.readString() ?: break
            closeTo(depth)
            open += OutlineEntry(
                title = title.ifBlank { "Untitled" },
                target = linkTarget(target),
            ) to mutableListOf()
        }
        closeTo(0)
        return roots
    }

    /** One word of hidden text, boxed in the page's own bottom-left pixels. */
    private class Word(
        val text: String,
        val left: Int,
        val bottom: Int,
        val right: Int,
        val top: Int,
    ) {
        /** Whether this entry holds a whole line rather than a single word. */
        val isRun = text.indexOf(' ') >= 0

        /**
         * How far into this entry's text a point lies, as a character offset.
         *
         * ddjvu is asked for word-level detail, but what it returns is the
         * deepest zone that carries a string, and some files only go down to
         * lines. Answering with the start of the entry then pins every point on
         * such a line to its first word - the selection cannot be dragged,
         * because every position along it picks the same word out.
         *
         * Interpolating across the box assumes even character widths, which
         * proportional type is not, so this lands a character or two out on a
         * long line. That is enough: the caller widens the result to the word
         * around it. For an entry that really is one word, every offset is
         * inside that word and the caller's answer is unchanged.
         */
        fun offsetAt(px: Float): Int {
            val width = (right - left).toFloat()
            if (width <= 0f || text.isEmpty()) return 0
            val across = ((px - left) / width).coerceIn(0f, 1f)
            return (across * text.length).toInt().coerceAtMost(text.length - 1)
        }

        /**
         * The part of this entry's box that characters [from] until [to] of it
         * cover, as a fraction of the box from its left edge.
         *
         * A single word is always covered whole: a match usually starts or ends
         * mid-word, and marking the whole word is what a highlighter does
         * anyway. A line cannot be treated that way - marking it whole would
         * paint the entire line for a selection of one word in it.
         */
        fun span(from: Int, to: Int): Pair<Float, Float> {
            if (!isRun || text.isEmpty()) return 0f to 1f
            val length = text.length.toFloat()
            return from.coerceIn(0, text.length) / length to
                to.coerceIn(0, text.length) / length
        }
    }

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
            val text = ByteArray(length).also(buffer::get).decodeText()
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
        /**
         * What a document's bytes are read as when they turn out not to be
         * UTF-8. Not one of the charsets a JVM is required to provide, so it
         * falls back to the Latin-1 it is a superset of.
         */
        private val Legacy: Charset =
            runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)

        private const val REPLACEMENT = '�'

        /**
         * Bytes from the document, as text.
         *
         * DjVu says its strings are UTF-8 and most files mean it, but the
         * bookmarks and hidden text of older scans carry whatever eight-bit
         * encoding the tool that wrote them used, and there is no declaration
         * anywhere to say which. Read as UTF-8, every byte above ASCII in one
         * of those becomes U+FFFD - a scanned maths book listing every one of
         * its sections as "?6. Analytic Manifolds" in the contents, beside a
         * page that plainly reads "§6".
         *
         * Anything UTF-8 could not read is taken as [Legacy] instead. Where the
         * file's encoding was not a Western one that gives mojibake rather than
         * question marks - no more correct, but it is at least still the
         * reader's own alphabet to guess at rather than a row of diamonds.
         */
        private fun ByteArray.decodeText(): String {
            val text = toString(Charsets.UTF_8)
            // The replacement character is what the decoder substitutes for
            // bytes it could not read. A document that means to contain one is
            // not a thing that happens.
            return if (REPLACEMENT !in text) text else toString(Legacy)
        }

        /** A length-prefixed string, or null if the buffer is short. */
        private fun ByteBuffer.readString(): String? {
            if (remaining() < Int.SIZE_BYTES) return null
            val length = int
            if (length < 0 || length > remaining()) return null
            return ByteArray(length).also(::get).decodeText()
        }

        /** Four box edges and a length, per word. */
        private const val WORD_HEADER_BYTES = 5 * Int.SIZE_BYTES

        /** The same four edges and a length, per link. */
        private const val LINK_HEADER_BYTES = 5 * Int.SIZE_BYTES

        /**
         * Reads a maparea's target.
         *
         * DjVu writes an internal jump as a fragment: `#7` for the seventh
         * page, or `#name` for a page named in the document's directory. Page
         * numbers here count from 1, as they do to a reader; everything below
         * this counts from 0.
         *
         * A named target is not resolved. Doing it properly means reading the
         * document directory, and a name that cannot be looked up would jump
         * somewhere arbitrary, which is worse than a link that does nothing.
         */
        private fun linkTarget(target: String): LinkTarget? {
            if (target.isBlank()) return null
            if (!target.startsWith("#")) return LinkTarget.Url(target)
            val page = target.removePrefix("#").toIntOrNull() ?: return null
            return if (page >= 1) LinkTarget.Page(page - 1) else null
        }

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