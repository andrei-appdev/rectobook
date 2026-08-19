package com.cartavu.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import java.io.File
import java.io.IOException
import io.legere.pdfiumandroid.PdfDocument as PdfiumDocument

/**
 * PDFium keeps process-wide state and is not thread-safe, not even across
 * separate documents, so every call into it goes through this.
 *
 * The reader already serialises its own document behind a lock, and the
 * thumbnailer serialises its own, but those two run concurrently - which is
 * fine for DjVuLibre, where each document owns its context, and is not fine
 * here. Calls are made off the main thread, so blocking on this costs nothing
 * that matters.
 */
private val PdfiumLock = Any()

private inline fun <T> locked(block: () -> T): T = synchronized(PdfiumLock, block)

/**
 * Whether PDFium put this character in a page's text rather than the page did.
 *
 * U+FFFE marks a hyphen PDFium generated to break a word across lines, and
 * dropping it joins the word back up - which is both what the reader means by
 * copying it and what makes a search for the unbroken word find the page. The
 * rest are the encoding failing: a glyph the page's font gave no Unicode for
 * arrives as U+0000 or U+0001. Tab and the two line breaks are real text and
 * the only C0 characters kept.
 */
private fun Char.isPdfiumMark(): Boolean =
    this == '\uFFFE' || this == '\uFFFF' ||
        (code < 0x20 && this != '\n' && this != '\r' && this != '\t')

/**
 * An open PDF, decoded by PDFium.
 *
 * Pages are opened per call rather than held: PDFium page handles are cheap to
 * reopen, the library reference-counts them, and holding one would mean this
 * class had a current page to keep in step with the caller's.
 */
class PdfDocument private constructor(
    private val document: PdfiumDocument,
    private val descriptor: ParcelFileDescriptor,
) : PagedDocument {

    private var closed = false

    override val format = DocumentFormat.Pdf

    override val pageCount: Int
        get() = locked { if (closed) 0 else runCatching { document.getPageCount() }.getOrDefault(0) }

    /**
     * Page geometry in PostScript points, which is what a PDF is measured in -
     * hence the fixed 72 dpi. Only the ratio of the two is read downstream, so
     * the unit matters less than that width and height share it.
     */
    override fun pageSize(pageNumber: Int): PageSize? = locked {
        if (closed) return@locked null
        runCatching {
            document.openPage(pageNumber).use { page ->
                PageSize(
                    width = page.getPageWidthPoint(),
                    height = page.getPageHeightPoint(),
                    dpi = POINTS_PER_INCH,
                )
            }
        }.getOrNull()
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
    ): Bitmap? = locked {
        if (closed) return@locked null
        if (pageWidth <= 0 || pageHeight <= 0) return@locked null
        if (regionWidth <= 0 || regionHeight <= 0) return@locked null

        runCatching {
            val bitmap = Bitmap.createBitmap(regionWidth, regionHeight, Bitmap.Config.ARGB_8888)
            // A PDF page has no opaque background of its own; the paper is ours
            // to supply, and without it the page arrives on transparent black.
            bitmap.eraseColor(Color.WHITE)
            document.openPage(pageNumber).use { page ->
                // startX/startY place the page's top-left corner within the
                // bitmap, so a region at (clipX, clipY) of the scaled page is
                // the page pushed that far up and left.
                page.renderPageBitmap(
                    bitmap,
                    -clipX,
                    -clipY,
                    pageWidth,
                    pageHeight,
                    /* renderAnnot = */ true,
                    /* textMask = */ false,
                )
            }
            bitmap
        }.getOrNull()
    }

    /**
     * The page's text. Unlike a scan's, this is usually the document's own text
     * rather than an OCR pass over a picture of it, so it is exact.
     */
    override fun pageText(pageNumber: Int): String? = locked { textOf(pageNumber)?.text }

    /**
     * A page's text with PDFium's own marks taken out of it, and the map back
     * to the indices PDFium answers in.
     *
     * What `FPDFText_GetText` returns is not only the page's characters: a
     * hyphen PDFium generated at a line break comes back as U+FFFE, and a
     * character the page's encoding did not resolve as U+0000 or U+0001. None
     * of them are whitespace, so nothing downstream drops them - they reach the
     * clipboard, draw as tofu in a search snippet, and stop "структуры"
     * matching a page that breaks it as "структу-ры".
     *
     * Dropping them means this class's indices are no longer PDFium's, so
     * [toRaw] carries the way back for the calls that are asked in ours and
     * answered in PDFium's.
     */
    private class PageText(val pageNumber: Int, val text: String, val toRaw: IntArray) {
        /** Where clean index [at] sits in PDFium's numbering. */
        fun raw(at: Int): Int =
            if (toRaw.isEmpty()) 0 else toRaw[at.coerceIn(0, toRaw.lastIndex)]

        /**
         * Where PDFium's index [at] sits in ours, or the nearest kept character
         * if [at] is one of the marks that was dropped.
         */
        fun clean(at: Int): Int {
            if (toRaw.isEmpty()) return 0
            val found = toRaw.binarySearch(at)
            return if (found >= 0) found else (-found - 1).coerceIn(0, toRaw.lastIndex)
        }
    }

    /**
     * Held for the page last asked about: a drag asks for rectangles on every
     * frame, and each would otherwise reopen the page and re-extract its text.
     */
    private var lastText: PageText? = null

    private fun textOf(pageNumber: Int): PageText? {
        if (closed) return null
        lastText?.let { if (it.pageNumber == pageNumber) return it }
        val built = runCatching {
            document.openPage(pageNumber).use { page ->
                document.openTextPage(page).use { text ->
                    val characters = text.textPageCountChars()
                    val raw = if (characters <= 0) {
                        ""
                    } else {
                        text.textPageGetText(0, characters).orEmpty()
                    }
                    val kept = StringBuilder(raw.length)
                    val toRaw = IntArray(raw.length)
                    for (index in raw.indices) {
                        if (raw[index].isPdfiumMark()) continue
                        toRaw[kept.length] = index
                        kept.append(raw[index])
                    }
                    PageText(pageNumber, kept.toString(), toRaw.copyOf(kept.length))
                }
            }
        }.getOrNull() ?: return null
        lastText = built
        return built
    }

    /**
     * PDFium reports the rectangles a character range occupies itself, one per
     * line the range crosses, which is exactly what a highlight needs.
     */
    override fun textRects(pageNumber: Int, start: Int, end: Int): List<PageRect> = locked {
        if (closed || end <= start) return@locked emptyList()
        val mapped = textOf(pageNumber) ?: return@locked emptyList()
        if (mapped.text.isEmpty() || start >= mapped.text.length) return@locked emptyList()
        // Back into PDFium's numbering. A mark dropped in the middle of the
        // range stays inside it, which is what a highlight wants: the hyphen
        // U+FFFE stood for is drawn on the page and belongs under the mark.
        val rawStart = mapped.raw(start)
        val rawEnd = mapped.raw(end - 1) + 1
        runCatching {
            document.openPage(pageNumber).use { page ->
                val width = page.getPageWidthPoint().toFloat()
                val height = page.getPageHeightPoint().toFloat()
                if (width <= 0f || height <= 0f) return@use emptyList()

                document.openTextPage(page).use { text ->
                    val count = rawEnd - rawStart
                    (0 until text.textPageCountRects(rawStart, count)).mapNotNull { index ->
                        text.textPageGetRect(index)?.let { rect ->
                            PageRect(
                                left = rect.left / width,
                                // PDF pages are measured from the bottom, and
                                // PDFium keeps that convention here.
                                top = 1f - rect.top / height,
                                right = rect.right / width,
                                bottom = 1f - rect.bottom / height,
                            )
                        }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * The character under the point. PDFium answers this itself, in the page's
     * own points, and takes a tolerance for a finger that lands beside the
     * glyph rather than on it.
     */
    override fun charAt(pageNumber: Int, x: Float, y: Float): Int? = locked {
        if (closed) return@locked null
        runCatching {
            document.openPage(pageNumber).use { page ->
                val width = page.getPageWidthPoint().toDouble()
                val height = page.getPageHeightPoint().toDouble()
                if (width <= 0 || height <= 0) return@use null

                document.openTextPage(page).use { text ->
                    val index = text.textPageGetCharIndexAtPos(
                        x * width,
                        // Back to the bottom-left origin a PDF is measured in.
                        (1f - y) * height,
                        width * TOUCH_TOLERANCE,
                        height * TOUCH_TOLERANCE,
                    )
                    // PDFium answers in its own numbering, which counts the
                    // marks this class drops.
                    index.takeIf { it >= 0 }?.let { textOf(pageNumber)?.clean(it) }
                }
            }
        }.getOrNull()
    }

    /**
     * PDFium reads the page's link annotations itself, and resolves an internal
     * destination to a page index for us. A link carries one or the other -
     * a page to go to, or a URI to hand on.
     */
    override fun links(pageNumber: Int): List<PageLink> = locked {
        if (closed) return@locked emptyList()
        runCatching {
            document.openPage(pageNumber).use { page ->
                val width = page.getPageWidthPoint().toFloat()
                val height = page.getPageHeightPoint().toFloat()
                if (width <= 0f || height <= 0f) return@use emptyList()

                page.getPageLinks().mapNotNull { link ->
                    val target = link.destPageIdx?.let { LinkTarget.Page(it) }
                        ?: link.uri?.takeIf { it.isNotBlank() }?.let { LinkTarget.Url(it) }
                        ?: return@mapNotNull null
                    PageLink(
                        bounds = PageRect(
                            left = link.bounds.left / width,
                            // Measured from the foot of the page, as everything
                            // in a PDF is.
                            top = 1f - link.bounds.top / height,
                            right = link.bounds.right / width,
                            bottom = 1f - link.bounds.bottom / height,
                        ),
                        target = target,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * PDFium walks the outline itself and resolves each entry to a page index.
     */
    override fun outline(): List<OutlineEntry> = locked {
        if (closed) return@locked emptyList()
        runCatching { document.getTableOfContents().map(::toEntry) }.getOrDefault(emptyList())
    }

    private fun toEntry(bookmark: PdfiumDocument.Bookmark): OutlineEntry = OutlineEntry(
        title = bookmark.title.orEmpty().ifBlank { "Untitled" },
        target = LinkTarget.Page(bookmark.pageIdx.toInt()),
        children = bookmark.children.map(::toEntry),
    )

    override fun close() = locked {
        if (!closed) {
            closed = true
            lastText = null
            // The document owns the descriptor and closes it, but saying so
            // twice is harmless and saying it never is a leaked fd.
            runCatching { document.close() }
            runCatching { descriptor.close() }
            Unit
        }
    }

    companion object {
        /** A PDF is measured in points, 72 to the inch, by definition. */
        private const val POINTS_PER_INCH = 72

        /**
         * How far from a glyph a touch still counts, as a fraction of the page.
         * A fingertip covers a good deal more of a page than a character does.
         */
        private const val TOUCH_TOLERANCE = 0.01

        /**
         * Opens [file] as a PDF, throwing [DocumentException] naming what went
         * wrong if it cannot be.
         */
        fun open(context: Context, file: File): PdfDocument {
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            return try {
                val document = locked { PdfiumCore(context).newDocument(descriptor) }
                PdfDocument(document, descriptor)
            } catch (passworded: PdfPasswordException) {
                runCatching { descriptor.close() }
                throw DocumentException(DocumentFailure.PasswordProtected, passworded)
            } catch (failure: Throwable) {
                runCatching { descriptor.close() }
                throw failure
            }
        }
    }
}
