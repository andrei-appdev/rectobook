package com.rectobook.app

/** One occurrence of the query, with enough of its line to read it in a list. */
data class SearchHit(
    /** 0-based page, as everything below the UI counts them. */
    val page: Int,
    /**
     * Where the match sits in the string the page's text came back as, so the
     * document can be asked what it covers and the match marked there.
     */
    val start: Int,
    val end: Int,
    val before: String,
    /** The matched text itself, in the case the page spells it. */
    val term: String,
    val after: String,
)

/** What the search screen is showing. */
data class SearchResults(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    /** Pages examined so far, so a long search can show progress. */
    val scannedPages: Int = 0,
    val pageCount: Int = 0,
    val running: Boolean = false,
    /**
     * Whether any page scanned so far carried text at all. A document made from
     * images with no OCR pass finds nothing however it is spelled, and saying so
     * is more use than "0 matches".
     */
    val hasTextLayer: Boolean = false,
    /** Set when [MAX_HITS] was reached and scanning stopped early. */
    val truncated: Boolean = false,
) {
    val isEmptyQuery: Boolean get() = query.isEmpty()
}

/** Shortest query worth running: one letter matches most of a book. */
const val MIN_QUERY_LENGTH = 2

/**
 * Ceiling on hits. Searching a common word across a long scan can match
 * thousands of times, and nobody scrolls a list that long - past this the
 * search stops and says it was cut short.
 */
const val MAX_HITS = 400

/** Characters of context kept either side of a match. */
private const val LEAD = 36
private const val TRAIL = 64

/**
 * A page's text with every run of whitespace collapsed to one space, and, for
 * each character kept, where it came from in the original.
 *
 * Searching wants the flattened form - hidden text arrives broken by the lines
 * of the scan, and a snippet that kept those breaks would be shaped by the
 * page's column width rather than by the sentence. Marking a match on the page
 * wants the original, because that is the string the document can be asked
 * about. Keeping the map means neither has to give way.
 */
private class Flattened(val text: String, val source: IntArray)

private fun flatten(pageText: String): Flattened {
    val text = StringBuilder(pageText.length)
    val source = IntArray(pageText.length)
    var afterSpace = true
    for (index in pageText.indices) {
        val character = pageText[index]
        if (character.isWhitespace()) {
            // Leading and repeated whitespace is dropped rather than kept as a
            // space nothing would match against.
            if (!afterSpace) {
                source[text.length] = index
                text.append(' ')
                afterSpace = true
            }
        } else {
            source[text.length] = index
            text.append(character)
            afterSpace = false
        }
    }
    while (text.isNotEmpty() && text.last() == ' ') text.setLength(text.length - 1)
    return Flattened(text.toString(), source)
}

/** Every occurrence of [query] in one page's text, as hits ready to display. */
fun findHits(pageText: String, query: String, page: Int, limit: Int): List<SearchHit> {
    if (query.isEmpty() || limit <= 0) return emptyList()
    val flattened = flatten(pageText)
    val text = flattened.text
    if (text.isEmpty()) return emptyList()

    val hits = mutableListOf<SearchHit>()
    var from = 0
    while (hits.size < limit) {
        val at = text.indexOf(query, startIndex = from, ignoreCase = true)
        if (at < 0) break
        val end = at + query.length

        val leadFrom = (at - LEAD).coerceAtLeast(0)
        val trailTo = (end + TRAIL).coerceAtMost(text.length)
        hits += SearchHit(
            page = page,
            start = flattened.source[at],
            end = flattened.source[end - 1] + 1,
            // The ellipses mark where the line was cut, so a snippet starting
            // mid-sentence does not read as the start of one.
            before = (if (leadFrom > 0) "…" else "") + text.substring(leadFrom, at),
            term = text.substring(at, end),
            after = text.substring(end, trailTo) + (if (trailTo < text.length) "…" else ""),
        )
        // Overlapping matches ("aa" in "aaa") would otherwise be reported twice
        // over the same characters.
        from = end
    }
    return hits
}
