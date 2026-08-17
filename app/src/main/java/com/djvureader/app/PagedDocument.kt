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
)

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
