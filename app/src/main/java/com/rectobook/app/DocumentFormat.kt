package com.rectobook.app

import java.io.File

/** A document format the reader knows how to recognise. */
enum class DocumentFormat {
    Djvu,
    Pdf,
    ;

    /** How the reader's chrome names the format. */
    val label: String
        get() = when (this) {
            Djvu -> "DJVU"
            Pdf -> "PDF"
        }

    companion object {
        /**
         * What [file] actually is, or null if it is neither format.
         *
         * Read from the bytes rather than the name. A document arriving through
         * the system picker may be called anything at all - providers hand out
         * `content://` URIs whose display name is whatever the source felt like
         * - and a reader that trusts the extension opens the wrong decoder on a
         * mislabelled file.
         */
        fun of(file: File): DocumentFormat? {
            val head = runCatching {
                file.inputStream().use { stream ->
                    ByteArray(HEAD_BYTES).let { buffer ->
                        val read = stream.read(buffer)
                        if (read <= 0) ByteArray(0) else buffer.copyOf(read)
                    }
                }
            }.getOrElse { return null }

            return when {
                head.startsWith(DJVU_MAGIC, at = 0) -> Djvu
                // Some DjVu files are bare IFF, without the AT&T prefix.
                head.startsWith(IFF_MAGIC, at = 0) && head.startsWith(DJVU_CHUNK, at = 8) -> Djvu
                // The PDF header is allowed to sit behind leading junk, and
                // real files do: the spec only asks that it be near the start.
                head.indexOf(PDF_MAGIC) >= 0 -> Pdf
                else -> null
            }
        }

        /** Enough to cover a displaced PDF header; the DjVu ones are at the front. */
        private const val HEAD_BYTES = 1024

        private val DJVU_MAGIC = "AT&TFORM".toByteArray(Charsets.US_ASCII)
        private val IFF_MAGIC = "FORM".toByteArray(Charsets.US_ASCII)

        /** DjVu's IFF body: a single page, a multi-page bundle, or an insert. */
        private val DJVU_CHUNK = "DJV".toByteArray(Charsets.US_ASCII)

        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}

private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean {
    if (at + prefix.size > size) return false
    return prefix.indices.all { this[at + it] == prefix[it] }
}

private fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) return -1
    outer@ for (start in 0..size - needle.size) {
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) continue@outer
        }
        return start
    }
    return -1
}
