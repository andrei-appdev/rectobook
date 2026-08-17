package com.djvureader.app

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
    override fun pageText(pageNumber: Int): String? = locked {
        if (closed) return@locked null
        runCatching {
            document.openPage(pageNumber).use { page ->
                document.openTextPage(page).use { text ->
                    val characters = text.textPageCountChars()
                    if (characters <= 0) "" else text.textPageGetText(0, characters).orEmpty()
                }
            }
        }.getOrNull()
    }

    /**
     * PDFium reports the rectangles a character range occupies itself, one per
     * line the range crosses, which is exactly what a highlight needs.
     */
    override fun textRects(pageNumber: Int, start: Int, end: Int): List<PageRect> = locked {
        if (closed || end <= start) return@locked emptyList()
        runCatching {
            document.openPage(pageNumber).use { page ->
                val width = page.getPageWidthPoint().toFloat()
                val height = page.getPageHeightPoint().toFloat()
                if (width <= 0f || height <= 0f) return@use emptyList()

                document.openTextPage(page).use { text ->
                    val count = end - start
                    (0 until text.textPageCountRects(start, count)).mapNotNull { index ->
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

    override fun close() = locked {
        if (!closed) {
            closed = true
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
         * Opens [file] as a PDF, throwing [IOException] with something worth
         * showing if it cannot be.
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
                throw IOException("This PDF is password-protected.", passworded)
            } catch (failure: Throwable) {
                runCatching { descriptor.close() }
                throw failure
            }
        }
    }
}
