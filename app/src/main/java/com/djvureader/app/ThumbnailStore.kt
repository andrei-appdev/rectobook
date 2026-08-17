package com.djvureader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

/**
 * Identity of a document's thumbnail. The file's length is part of it, so a
 * document whose bytes have changed since it was last opened gets a fresh
 * thumbnail rather than keeping the old one forever.
 */
internal val RecentDocument.thumbnailKey: String
    get() = "${key.hashCode().absoluteValue}-$sizeBytes"

/**
 * The first page of each library document, rendered small and kept as a PNG in
 * the cache directory.
 *
 * Making one means opening the document, which is why the result is stored
 * rather than re-derived: the library shows every recent entry at once, and
 * decoding a shelf of scans on every visit would cost far more than the picture
 * is worth. Cached files are only rewritten when a document changes size.
 *
 * Renders are serialised. They open a second [PagedDocument] alongside whatever
 * the reader has open - each document owns its own decoder state, so that is
 * safe - but decoding is memory-hungry, and several at once on a library of
 * large scans is not worth the parallelism.
 */
class ThumbnailStore(private val context: Context) {

    private val lock = Mutex()

    /**
     * The first page of [document] at up to [width] x [height] pixels, or null
     * if it could not be rendered - a document whose permission has lapsed, or
     * one that has since been deleted.
     */
    suspend fun thumbnail(document: RecentDocument, width: Int, height: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(directory(), "${document.thumbnailKey}.png")
            decode(file)?.let { return@withContext it }

            lock.withLock {
                // Another caller may have rendered this one while we waited.
                decode(file)?.let { return@withLock it }
                val rendered = render(document, width, height) ?: return@withLock null
                runCatching {
                    file.outputStream().use { out ->
                        rendered.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                    }
                }
                rendered
            }
        }

    private fun render(document: RecentDocument, width: Int, height: Int): Bitmap? {
        val source = sourceFile(document) ?: return null
        // A library entry we cannot open is not worth reporting: the card keeps
        // its drawn glyph, and the reader will say why if it is tapped.
        return runCatching {
            openDocument(context, source).use { it.renderPageToFit(0, width, height) }
        }.getOrNull()
    }

    /**
     * The document's bytes on the local filesystem. Nothing here is worth
     * failing loudly over: a library entry that cannot be read simply keeps the
     * drawn glyph.
     */
    private fun sourceFile(document: RecentDocument): File? = runCatching {
        val uri = Uri.parse(document.key)
        DocumentImporter.cachedCopy(context, uri)
            ?: DocumentImporter.import(context, uri).file
    }.getOrNull()

    private fun decode(file: File): Bitmap? =
        if (file.length() > 0) BitmapFactory.decodeFile(file.path) else null

    private fun directory() = File(context.cacheDir, THUMBNAIL_DIR).apply { mkdirs() }

    private companion object {
        const val THUMBNAIL_DIR = "thumbnails"

        /** PNG ignores the quality argument; it is lossless either way. */
        const val PNG_QUALITY = 100
    }
}
