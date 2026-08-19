package com.cartavu.app

import java.io.IOException

/**
 * Why a document could not be opened, as a value rather than a sentence.
 *
 * The decoders have no `Context` and no business choosing words. The same
 * failure has to read in whatever language the app is set to, and only the UI
 * knows what that is - so the reason travels typed and becomes text at the
 * edge. "Not a document we can read" and "a DjVu file we failed on" are still
 * different things to be told, which was the point of throwing rather than
 * returning null; they are now different types rather than different strings.
 */
sealed interface DocumentFailure {

    /** The bytes are neither DjVu nor PDF. */
    data object NotADocument : DocumentFailure

    /** Recognised as DjVu, and DjVuLibre would not have it. */
    data object UnreadableDjvu : DocumentFailure

    /** A PDF that wants a password there is nowhere yet to ask for. */
    data object PasswordProtected : DocumentFailure

    /** The provider behind a `content://` URI gave us nothing to read. */
    data object CannotRead : DocumentFailure

    /** It opened, and held no bytes. */
    data object EmptyFile : DocumentFailure

    /**
     * Anything not foreseen here.
     *
     * [detail] is whatever was thrown, and stays in the language it was thrown
     * in - untranslated, and still better than a blank screen.
     */
    data class Unexpected(val detail: String) : DocumentFailure
}

/** An [IOException] that says why in a form the UI can put into words. */
class DocumentException(
    val failure: DocumentFailure,
    cause: Throwable? = null,
) : IOException(failure.toString(), cause)

/** The reason [this] failed, for anything thrown while opening a document. */
fun Throwable.documentFailure(): DocumentFailure = when (this) {
    is DocumentException -> failure
    else -> DocumentFailure.Unexpected(message ?: toString())
}
