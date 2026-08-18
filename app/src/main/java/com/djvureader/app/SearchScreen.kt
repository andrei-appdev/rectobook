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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djvureader.app.ui.theme.HeadingFont
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryAccent700
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
 *
 * [listState] is the caller's, because this screen is torn down every time a
 * hit is opened and built again on the way back. Reading a document by its
 * matches means going down the list one at a time, and a list that returned to
 * the top each time would make that impossible.
 */
@Composable
fun SearchScreen(
    query: String,
    results: SearchResults,
    listState: LazyListState,
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
            IconAction(BackIcon, stringResource(R.string.search_close), onClose)
            IndustryTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.search_in_document),
                modifier = Modifier.weight(1f),
                height = 38.dp,
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
            )
        }
        Hairline()

        Text(
            text = statusLine(results),
            style = SectionLabel,
            color = onSurfaceAlpha(0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
        Hairline()

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(count = results.hits.size) { index ->
                val hit = results.hits[index]
                HitRow(hit = hit, onClick = { onOpenHit(hit) })
            }
            if (results.truncated) {
                item {
                    Note(stringResource(R.string.search_stopped_at, MAX_HITS))
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
@Composable
private fun statusLine(results: SearchResults): String = when {
    results.query.isEmpty() -> stringResource(R.string.search_type_to_search)
    results.query.length < MIN_QUERY_LENGTH -> stringResource(R.string.search_keep_typing)
    results.running && results.hits.isEmpty() ->
        stringResource(R.string.search_scanning, results.scannedPages, results.pageCount)
    results.hits.isEmpty() && !results.hasTextLayer ->
        stringResource(R.string.search_no_text_layer)
    results.hits.isEmpty() -> stringResource(R.string.search_no_matches, results.query)
    // Each of these is a whole sentence rather than a count with a phrase
    // glued on. The count agrees with the noun in most languages, and the
    // query does not always come last.
    results.truncated && results.running ->
        stringResource(R.string.search_matches_capped_so_far, results.hits.size, results.query)
    results.truncated ->
        stringResource(R.string.search_matches_capped, results.hits.size, results.query)
    results.running -> pluralStringResource(
        R.plurals.search_matches_so_far,
        results.hits.size,
        results.hits.size,
        results.query,
    )
    else -> pluralStringResource(
        R.plurals.search_matches,
        results.hits.size,
        results.hits.size,
        results.query,
    )
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
                text = stringResource(R.string.search_hit_page, hit.page + 1),
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
                text = snippet(hit, IndustryAccent.copy(alpha = 0.32f)),
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

/**
 * The design's `<mark>`: the term on a wash of accent, the text left alone.
 *
 * The wash is passed in rather than read here, so this stays a plain string
 * builder and the colour comes from wherever it is being drawn.
 */
private fun snippet(hit: SearchHit, wash: Color): AnnotatedString = buildAnnotatedString {
    append(hit.before)
    withStyle(SpanStyle(background = wash)) {
        append(hit.term)
    }
    append(hit.after)
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
