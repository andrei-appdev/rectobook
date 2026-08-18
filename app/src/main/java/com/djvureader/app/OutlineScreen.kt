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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.djvureader.app.ui.theme.IndustryAccent700
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.onSurfaceAlpha

/**
 * The document's own table of contents, as it was written into the file.
 *
 * Shown flat with each level stepped in rather than as a tree that folds open:
 * an outline is read by running an eye down it, and a row that has to be
 * opened before it can be read is in the way of that. Depth is carried by the
 * indent and by the weight of the type, so the shape is still legible.
 *
 * [listState] is the caller's, so returning here after following an entry
 * comes back to the same place - the same reason the search results keep
 * theirs.
 */
@Composable
fun OutlineScreen(
    outline: List<OutlineEntry>,
    pageCount: Int,
    listState: LazyListState,
    onClose: () -> Unit,
    onOpen: (OutlineEntry) -> Unit,
) {
    val rows = remember(outline) { outline.flattenOutline() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustryBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconAction(BackIcon, stringResource(R.string.outline_close), onClose)
            Text(
                text = stringResource(R.string.outline_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        Hairline()

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(count = rows.size) { index ->
                val (entry, depth) = rows[index]
                OutlineRow(
                    entry = entry,
                    depth = depth,
                    pageCount = pageCount,
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

@Composable
private fun OutlineRow(
    entry: OutlineEntry,
    depth: Int,
    pageCount: Int,
    onClick: () -> Unit,
) {
    // An entry that leads nowhere is still shown - it groups the ones below -
    // but it is not offered as something to press.
    val page = (entry.target as? LinkTarget.Page)?.pageNumber?.takeIf { it in 0 until pageCount }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (page != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(
                    // Each level steps in, up to a point: a deep outline in a
                    // narrow column would otherwise leave no room for the text.
                    start = 16.dp + IndentStep * depth.coerceAtMost(MAX_INDENT_LEVELS),
                    end = 16.dp,
                    top = 13.dp,
                    bottom = 13.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = entry.title,
                style = if (depth == 0) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = onSurfaceAlpha(if (page != null) 0.85f else 0.45f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (page != null) {
                Text(
                    text = stringResource(R.string.outline_page, page + 1),
                    style = MaterialTheme.typography.labelMedium
                        .copy(fontFeatureSettings = TabularNums),
                    color = IndustryAccent700,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(onSurfaceAlpha(0.08f)),
        )
    }
}

/** How far one level of nesting steps in, and how many levels still do. */
private val IndentStep = 14.dp
private const val MAX_INDENT_LEVELS = 4
