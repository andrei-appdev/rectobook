package com.djvureader.app

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
 * therefore copied into the cache first. DjVu files are small by design - the
 * bundled 57-page sample is 428 KB - which keeps the copy cheap.
 */
object DocumentImporter {

    private const val IMPORT_DIR = "documents"

    /** Copies [uri] into cache storage. Throws [IOException] if it cannot be read. */
    fun import(context: Context, uri: Uri): LocalDocument {
        val directory = File(context.cacheDir, IMPORT_DIR).apply { mkdirs() }
        val target = File(directory, "${uri.toString().hashCode().absoluteValue}.djvu")

        // Always re-copy: the bytes behind a content:// URI can change, and a
        // stale cache entry would silently serve the old document.
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot open $uri")

        if (target.length() == 0L) {
            throw IOException("$uri produced an empty file")
        }
        return LocalDocument(file = target, displayName = displayName(context, uri))
    }

    /** Copies the bundled sample out of assets, for a document that always works. */
    fun importSample(context: Context, assetName: String): LocalDocument {
        val directory = File(context.cacheDir, IMPORT_DIR).apply { mkdirs() }
        val target = File(directory, assetName)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return LocalDocument(file = target, displayName = assetName)
    }

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