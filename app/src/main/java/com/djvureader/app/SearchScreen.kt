package com.djvureader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.djvureader.app.ui.theme.HeadingFont
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryAccent100
import com.djvureader.app.ui.theme.IndustryAccent700
import com.djvureader.app.ui.theme.IndustryAccent800
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.SectionLabel
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.onSurfaceAlpha

/**
 * In-document search: a field, a count, and the hits with the term marked in
 * each one. Covers the reader entirely, as the design draws it.
 *
 * [results] arrive while the scan is still running, so the list fills as pages
 * are read rather than waiting for the last one.
 */
@Composable
fun SearchScreen(
    query: String,
    results: SearchResults,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenHit: (SearchHit) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustryBackground)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconAction(BackIcon, "Close search", onClose)
            IndustryTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Search in document",
                modifier = Modifier.weight(1f),
                height = 38.dp,
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
            )
        }
        Hairline()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = statusLine(results),
                style = SectionLabel,
                color = onSurfaceAlpha(0.55f),
            )
            Tag(text = "OCR LAYER")
        }
        Hairline()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(count = results.hits.size) { index ->
                val hit = results.hits[index]
                HitRow(hit = hit, onClick = { onOpenHit(hit) })
            }
            if (results.truncated) {
                item {
                    Note("Stopped at $MAX_HITS matches. Narrow the search to see the rest.")
                }
            }
        }
    }
}

/**
 * What the scan has found, or why it has found nothing. The design shows a
 * match count; the other states are ours, because a real document can have no
 * text layer at all and a real scan takes time.
 */
private fun statusLine(results: SearchResults): String = when {
    results.query.isEmpty() -> "Type to search"
    results.query.length < MIN_QUERY_LENGTH -> "Keep typing"
    results.running && results.hits.isEmpty() ->
        "Searching ${results.scannedPages} / ${results.pageCount}"
    results.hits.isEmpty() && !results.hasTextLayer -> "No text layer in this document"
    results.hits.isEmpty() -> "No matches for “${results.query}”"
    else -> {
        val count = if (results.truncated) "${results.hits.size}+" else "${results.hits.size}"
        val matches = if (results.hits.size == 1) "1 match" else "$count matches"
        val suffix = if (results.running) " so far" else ""
        "$matches$suffix for “${results.query}”"
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "p.${hit.page + 1}",
                style = TextStyle(
                    fontFamily = HeadingFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    fontFeatureSettings = TabularNums,
                ),
                color = IndustryAccent700,
                modifier = Modifier.padding(top = 1.dp),
            )
            Text(
                text = snippet(hit),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                color = onSurfaceAlpha(0.8f),
            )
        }
        // Lighter than the design's usual divider: these run between every row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(onSurfaceAlpha(0.08f)),
        )
    }
}

/** The design's `<mark>`: the term on a wash of accent, the text left alone. */
private fun snippet(hit: SearchHit): AnnotatedString = buildAnnotatedString {
    append(hit.before)
    withStyle(SpanStyle(background = IndustryAccent.copy(alpha = 0.32f))) {
        append(hit.term)
    }
    append(hit.after)
}

/** The design's `.tag.tag-accent`. */
@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .background(IndustryAccent100)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = HeadingFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.02.em,
            ),
            color = IndustryAccent800,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = onSurfaceAlpha(0.5f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
