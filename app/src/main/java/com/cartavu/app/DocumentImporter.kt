package com.cartavu.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlin.math.absoluteValue

/** A document copied into app storage, with the name it was opened under. */
data class LocalDocument(val file: File, val displayName: String)

/**
 * DjVuLibre opens documents by filesystem path, but Android hands out
 * `content://` URIs whose bytes live behind a provider. Imported documents are
 * therefore copied into the cache first. DjVu files are small by design - a
 * 57-page scan runs to a few hundred KB - which keeps the copy cheap.
 */
object DocumentImporter {

    private const val IMPORT_DIR = "documents"

    /** Copies [uri] into cache storage. Throws [DocumentException] if it cannot be read. */
    fun import(context: Context, uri: Uri): LocalDocument {
        val directory = File(context.cacheDir, IMPORT_DIR).apply { mkdirs() }
        val target = File(directory, importedName(uri))

        // Always re-copy: the bytes behind a content:// URI can change, and a
        // stale cache entry would silently serve the old document.
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw DocumentException(DocumentFailure.CannotRead)

        if (target.length() == 0L) {
            throw DocumentException(DocumentFailure.EmptyFile)
        }
        return LocalDocument(file = target, displayName = displayName(context, uri))
    }

    /**
     * The copy [import] would write for [uri], if one is already there.
     *
     * [import] deliberately re-copies every time, because the bytes behind a
     * `content://` URI can change under us. Callers that only want something
     * disposable - a thumbnail - can take the existing copy instead and skip
     * pulling the whole document back through the provider.
     */
    fun cachedCopy(context: Context, uri: Uri): File? =
        File(File(context.cacheDir, IMPORT_DIR), importedName(uri)).takeIf { it.length() > 0 }

    private fun importedName(uri: Uri) = "${uri.toString().hashCode().absoluteValue}.djvu"

    private fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) {
                        return cursor.getString(0)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
    }
}