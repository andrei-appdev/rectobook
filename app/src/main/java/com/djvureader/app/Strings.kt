package com.djvureader.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.util.Locale

/**
 * The language the app is being read in.
 *
 * Taken from the configuration rather than [Locale.getDefault], so that it
 * still follows if a per-app language is ever offered.
 */
@Composable
@ReadOnlyComposable
fun readingLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * A string resource in capitals, as the design draws its labels.
 *
 * Uppercasing here rather than in the resource keeps the casing rules with the
 * language: Turkish capitalises `i` as `İ`, Greek drops accents on the way up,
 * and neither is a decision to leave to whoever writes the translation.
 */
@Composable
@ReadOnlyComposable
fun upperStringResource(@StringRes id: Int): String =
    stringResource(id).uppercase(readingLocale())

/** As [upperStringResource], for a resource that takes arguments. */
@Composable
@ReadOnlyComposable
fun upperStringResource(@StringRes id: Int, vararg formatArgs: Any): String =
    stringResource(id, *formatArgs).uppercase(readingLocale())

/** What to tell the reader about a document that would not open. */
@Composable
@ReadOnlyComposable
fun DocumentFailure.text(): String = when (this) {
    DocumentFailure.NotADocument -> stringResource(R.string.error_not_a_document)
    DocumentFailure.UnreadableDjvu -> stringResource(R.string.error_unreadable_djvu)
    DocumentFailure.PasswordProtected -> stringResource(R.string.error_password_protected)
    DocumentFailure.CannotRead -> stringResource(R.string.error_cannot_read)
    DocumentFailure.EmptyFile -> stringResource(R.string.error_empty_file)
    // Whatever was thrown, in the language it was thrown in.
    is DocumentFailure.Unexpected -> detail
}
