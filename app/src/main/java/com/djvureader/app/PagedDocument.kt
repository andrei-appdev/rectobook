package com.djvureader.app

import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable
import java.io.File
import java.io.IOException

/** Page geometry in the page's own pixel space, at its native resolution. */
data class PageSize(val width: Int, val height: Int, val dpi: Int)

/**
 * A rectangle on a page, in fractions of the page's width and height with the
 * origin at the top left.
 *
 * Fractions rather than pixels because a page is rendered at whatever width the
 * viewport and zoom ask for, and a highlight has to land on the text at every
 * one of them.
 */
data class PageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Whether ([x], [y]), in the same fractions, falls inside this. */
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom
}

/**
 * A document the reader can show: a fixed number of pages, each with a size, a
 * picture and possibly some text.
 *
 * This is the whole of what the reader needs from a file format. Implementations
 * decode on the calling thread and own native resources, so calls belong on a
 * background dispatcher and instances must be [close]d.
 */
interface PagedDocument : Closeable {

    /** What this document turned out to be, for anything that names it. */
    val format: DocumentFormat

    /** Number of pages, or 0 once closed. */
    val pageCount: Int

    /** Page geometry at native resolution, or null if it could not be read. */
    fun pageSize(pageNumber: Int): PageSize?

    /**
     * Renders [pageNumber] scaled to [width] x [height], or null if the page
     * could not be decoded.
     */
    fun renderPage(pageNumber: Int, width: Int, height: Int): Bitmap?

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
    ): Bitmap?

    /**
     * The page's text layer, empty when the page carries none, or null if it
     * could not be read.
     *
     * Scanned documents carry text only where something has run OCR over them,
     * so an empty result is ordinary rather than exceptional.
     */
    fun pageText(pageNumber: Int): String?

    /**
     * Rectangles covering characters [start] until [end] of the string
     * [pageText] returns for this page - what a search match occupies on the
     * page, so it can be marked there.
     *
     * More than one rectangle because a match can run across a line break, and
     * each line it touches needs its own.
     */
    fun textRects(pageNumber: Int, start: Int, end: Int): List<PageRect>

    /**
     * Which character of [pageText] sits at ([x], [y]), given as fractions of
     * the page, or null where nothing does.
     *
     * The inverse of [textRects], and what selection is built on: a finger
     * lands somewhere on a picture of a page, and the document is the only
     * thing that knows what is written there.
     *
     * Implementations may answer with the start of the word under the point
     * rather than the exact character - a DjVu's hidden text is boxed by the
     * word - so callers that need a whole word should widen the result
     * themselves.
     */
    fun charAt(pageNumber: Int, x: Float, y: Float): Int?

    /**
     * The page's hyperlinks: somewhere on the page, and something to do when it
     * is pressed.
     *
     * Empty for most pages. Links live in a document's own structure rather
     * than in its picture, so a scan has them only where the producer put them
     * - a table of contents, an index, a cross-reference.
     */
    fun links(pageNumber: Int): List<PageLink>

    /**
     * The document's table of contents, or empty if it carries none.
     *
     * A property of the whole document rather than of a page, and read once
     * when it is opened.
     */
    fun outline(): List<OutlineEntry>
}

/**
 * One line of a document's table of contents, and whatever sits under it.
 *
 * [target] is null for an entry that points nowhere this reader can follow -
 * a heading that only groups the entries below it, or a destination named in a
 * way that cannot be resolved. Such an entry is still worth showing, because
 * its children are.
 */
data class OutlineEntry(
    val title: String,
    val target: LinkTarget?,
    val children: List<OutlineEntry> = emptyList(),
)

/** Every entry in [outline], flattened depth-first, each with its depth. */
fun List<OutlineEntry>.flattenOutline(depth: Int = 0): List<Pair<OutlineEntry, Int>> =
    flatMap { entry -> listOf(entry to depth) + entry.children.flattenOutline(depth + 1) }

/** Where a link goes. */
sealed interface LinkTarget {
    /** Another page of this document, numbered from 0. */
    data class Page(val pageNumber: Int) : LinkTarget

    /** Somewhere outside it, to be handed to the system. */
    data class Url(val url: String) : LinkTarget
}

/** A hyperlink, boxed on the page as fractions of it. */
data class PageLink(val bounds: PageRect, val target: LinkTarget)

/**
 * The whole word around [index], as a range into [text], or null if [index]
 * does not land on one.
 *
 * Selection snaps to words rather than characters. DjVu can offer nothing finer
 * - its hidden text is boxed a word at a time - and having one format select by
 * the character while the other jumps by the word would make the same gesture
 * behave differently depending on what was opened.
 */
fun wordAround(text: String, index: Int): IntRange? {
    if (index < 0 || index >= text.length) return null
    if (text[index].isWhitespace()) return null
    var start = index
    while (start > 0 && !text[start - 1].isWhitespace()) start--
    var end = index
    while (end + 1 < text.length && !text[end + 1].isWhitespace()) end++
    return start..end
}

/**
 * A run of text picked out on one page.
 *
 * [anchor] is the word the selection was started on. Both ends are measured
 * from it, so dragging either way widens the selection from where it began
 * rather than from wherever it last reached.
 */
data class TextSelection(
    val page: Int,
    val anchor: IntRange,
    val start: Int,
    val end: Int,
) {
    val isEmpty: Boolean get() = end <= start
}

/**
 * Renders [pageNumber] scaled to fit within [maxWidth] x [maxHeight] while
 * preserving the page's aspect ratio.
 *
 * Derived from the two calls above rather than implemented per format, so every
 * backend fits pages the same way.
 */
fun PagedDocument.renderPageToFit(pageNumber: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
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

/**
 * Opens [file] with the backend its own bytes call for.
 *
 * Throws [IOException] with a message worth showing, which is the whole reason
 * this is not a nullable return: "not a document we can read" and "a DjVu file
 * we failed on" are different things to be told.
 */
fun openDocument(context: Context, file: File): PagedDocument = when (DocumentFormat.of(file)) {
    DocumentFormat.Djvu ->
        DjvuDocument.open(file) ?: throw IOException("This DjVu file could not be read.")

    DocumentFormat.Pdf ->
        PdfDocument.open(context, file)

    null ->
        throw IOException("This is not a DjVu or PDF document.")
}
