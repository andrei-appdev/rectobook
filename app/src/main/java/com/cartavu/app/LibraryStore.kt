package com.cartavu.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** A document the reader has opened before, and how far through it we got. */
data class RecentDocument(
    /** Stable identity: the persisted content URI the document was opened from. */
    val key: String,
    val displayName: String,
    val lastPage: Int,
    val lastOpenedAt: Long,
    /** 0 when recorded by an older build that did not store it. */
    val pageCount: Int = 0,
    val sizeBytes: Long = 0,
    /** Null for the same reason, and for an entry written before this was kept. */
    val format: DocumentFormat? = null,
) {
    /** How far through the document we are, 0f..1f. */
    val progress: Float
        get() = if (pageCount > 1) (lastPage.toFloat() / (pageCount - 1)).coerceIn(0f, 1f) else 0f
}

private val Context.libraryDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "library")

/**
 * Remembers which documents have been opened and the page each was left on.
 *
 * The whole list is kept as one JSON string. It is capped at [MAX_RECENTS]
 * entries, which is small enough that rewriting it on every save costs nothing
 * and avoids pulling in a database for what is a short list.
 */
class LibraryStore(private val context: Context) {

    val recents: Flow<List<RecentDocument>> =
        context.libraryDataStore.data.map { preferences ->
            decode(preferences[RECENTS_KEY])
        }

    /** Records a document as most recently opened, preserving its saved page. */
    suspend fun recordOpened(
        key: String,
        displayName: String,
        pageCount: Int,
        sizeBytes: Long,
        format: DocumentFormat,
    ) {
        context.libraryDataStore.edit { preferences ->
            val existing = decode(preferences[RECENTS_KEY])
            val previous = existing.firstOrNull { it.key == key }
            val entry = RecentDocument(
                key = key,
                displayName = displayName,
                lastPage = previous?.lastPage ?: 0,
                lastOpenedAt = System.currentTimeMillis(),
                pageCount = pageCount,
                sizeBytes = sizeBytes,
                format = format,
            )
            val updated = (listOf(entry) + existing.filterNot { it.key == key }).take(MAX_RECENTS)
            preferences[RECENTS_KEY] = encode(updated)
        }
    }

    suspend fun saveProgress(key: String, page: Int) {
        context.libraryDataStore.edit { preferences ->
            val updated = decode(preferences[RECENTS_KEY]).map { entry ->
                if (entry.key == key) entry.copy(lastPage = page) else entry
            }
            preferences[RECENTS_KEY] = encode(updated)
        }
    }

    /** Page the document was last left on, or 0 if it is new to us. */
    suspend fun lastPage(key: String): Int =
        recents.first().firstOrNull { it.key == key }?.lastPage ?: 0

    /**
     * How hard the reader is pulling contrast on a page, 0f..1f.
     *
     * A property of the reader rather than of a document: someone reading
     * faded scans is usually reading a shelf of them, and having to set it
     * again on each one would be the whole of the annoyance it removes.
     */
    val contrast: Flow<Float> =
        context.libraryDataStore.data.map { preferences ->
            preferences[CONTRAST_KEY]?.coerceIn(0f, 1f) ?: 0f
        }

    suspend fun saveContrast(value: Float) {
        context.libraryDataStore.edit { preferences ->
            preferences[CONTRAST_KEY] = value.coerceIn(0f, 1f)
        }
    }

    /**
     * What pages are toned to, for the same reason contrast is kept here: it is
     * a way of reading rather than a property of one document.
     *
     * Stored by name, and an unreadable one falls back to [PageColorMode.NORMAL]
     * rather than taking the read down - a mode this build no longer has is not
     * a reason to open the app without a setting.
     */
    val colorMode: Flow<PageColorMode> =
        context.libraryDataStore.data.map { preferences ->
            runCatching {
                PageColorMode.valueOf(preferences[COLOR_MODE_KEY] ?: "")
            }.getOrDefault(PageColorMode.NORMAL)
        }

    suspend fun saveColorMode(mode: PageColorMode) {
        context.libraryDataStore.edit { preferences ->
            preferences[COLOR_MODE_KEY] = mode.name
        }
    }

    /**
     * What the reader does with the system bars, kept here for the same reason
     * the tone is: it is how someone reads rather than something about one
     * document.
     *
     * Each of the four is guarded on its own, and falls back to the default
     * that rule was shipped with rather than to a fixed one - a name an older
     * or newer build wrote is not a reason to hand back a reader nobody asked
     * for.
     */
    val systemBarRules: Flow<SystemBarRules> =
        context.libraryDataStore.data.map { preferences ->
            val defaults = SystemBarRules()
            fun read(key: Preferences.Key<String>, fallback: BarVisibility) =
                runCatching { BarVisibility.valueOf(preferences[key] ?: "") }
                    .getOrDefault(fallback)

            SystemBarRules(
                statusPortrait = read(STATUS_PORTRAIT_KEY, defaults.statusPortrait),
                statusLandscape = read(STATUS_LANDSCAPE_KEY, defaults.statusLandscape),
                navigationPortrait = read(NAV_PORTRAIT_KEY, defaults.navigationPortrait),
                navigationLandscape = read(NAV_LANDSCAPE_KEY, defaults.navigationLandscape),
            )
        }

    suspend fun saveSystemBarRules(rules: SystemBarRules) {
        context.libraryDataStore.edit { preferences ->
            preferences[STATUS_PORTRAIT_KEY] = rules.statusPortrait.name
            preferences[STATUS_LANDSCAPE_KEY] = rules.statusLandscape.name
            preferences[NAV_PORTRAIT_KEY] = rules.navigationPortrait.name
            preferences[NAV_LANDSCAPE_KEY] = rules.navigationLandscape.name
        }
    }

    private fun decode(raw: String?): List<RecentDocument> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                RecentDocument(
                    key = item.getString("key"),
                    displayName = item.optString("name"),
                    lastPage = item.optInt("page"),
                    lastOpenedAt = item.optLong("opened"),
                    pageCount = item.optInt("pages"),
                    sizeBytes = item.optLong("bytes"),
                    // Guarded on its own: an unreadable name here would other-
                    // wise take the whole list down with it.
                    format = runCatching {
                        DocumentFormat.valueOf(item.getString("format"))
                    }.getOrNull(),
                )
            }
            // Earlier builds shipped a sample document and recorded it under
            // `asset:`. There is no such document now, so the entry is dropped
            // rather than left in the list to fail when opened.
        }.getOrDefault(emptyList()).filterNot { it.key.startsWith("asset:") }
    }

    private fun encode(entries: List<RecentDocument>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("name", entry.displayName)
                    .put("page", entry.lastPage)
                    .put("opened", entry.lastOpenedAt)
                    .put("pages", entry.pageCount)
                    .put("bytes", entry.sizeBytes)
                    .put("format", entry.format?.name),
            )
        }
        return array.toString()
    }

    companion object {
        private val RECENTS_KEY = stringPreferencesKey("recents")
        private val CONTRAST_KEY = floatPreferencesKey("contrast")
        private val COLOR_MODE_KEY = stringPreferencesKey("color_mode")
        private val STATUS_PORTRAIT_KEY = stringPreferencesKey("status_bar_portrait")
        private val STATUS_LANDSCAPE_KEY = stringPreferencesKey("status_bar_landscape")
        private val NAV_PORTRAIT_KEY = stringPreferencesKey("nav_bar_portrait")
        private val NAV_LANDSCAPE_KEY = stringPreferencesKey("nav_bar_landscape")
        private const val MAX_RECENTS = 20
    }
}
