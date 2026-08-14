package com.djvureader.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryAccent700
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.IndustryDivider
import com.djvureader.app.ui.theme.SectionLabel
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.blueprintCorners
import com.djvureader.app.ui.theme.onSurfaceAlpha
import java.util.Locale

/**
 * The library, styled after the design's first screen: wordmark, corpus line,
 * a single primary action, and the recent documents as bordered cards carrying
 * their own reading progress.
 */
@Composable
fun LibraryScreen(
    recents: List<RecentDocument>,
    onPick: () -> Unit,
    onSample: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
    ) {
        item {
            // The design aligns the wordmark and its version on a shared
            // baseline, not on their boxes - the two faces have different
            // descenders, so bottom alignment sits the version too low.
            Row {
                Text(
                    text = "DJVU READER",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = "v1.0",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.12.em,
                    color = onSurfaceAlpha(0.45f),
                    modifier = Modifier.alignByBaseline().padding(start = 10.dp),
                )
            }
            Text(
                text = corpusLine(recents),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceAlpha(0.55f),
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )
        }

        item {
            Button(
                onClick = onPick,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = IndustryAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .blueprintCorners(),
            ) {
                StrokeIcon(FolderIcon, size = 17.dp, color = IndustryBackground)
                Text(
                    text = "OPEN FILE",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            TextButton(
                onClick = onSample,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    text = "Open bundled sample",
                    style = MaterialTheme.typography.labelMedium,
                    color = onSurfaceAlpha(0.5f),
                )
            }
        }

        if (recents.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "RECENT", style = SectionLabel, modifier = Modifier.alignByBaseline())
                    Text(
                        text = "Last opened",
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceAlpha(0.45f),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            items(count = recents.size, key = { recents[it].key }) { index ->
                val document = recents[index]
                RecentCard(
                    document = document,
                    onClick = { onOpenRecent(document) },
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentCard(
    document: RecentDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The design stretches the card's two columns to a shared height so
            // the progress row can be pushed to the bottom of the page glyph
            // (`margin-bottom: auto`), whatever the title's length.
            .height(IntrinsicSize.Min)
            .border(1.dp, IndustryDivider)
            .blueprintCorners()
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        PageGlyph()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 13.dp),
        ) {
            Text(
                text = document.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = documentMeta(document),
                style = MaterialTheme.typography.labelMedium,
                color = onSurfaceAlpha(0.5f),
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(onSurfaceAlpha(0.14f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(document.progress)
                            .height(2.dp)
                            .background(IndustryAccent),
                    )
                }
                Text(
                    text = if (document.isFinished) "DONE" else "P.${document.lastPage + 1}",
                    style = MaterialTheme.typography.labelSmall
                        .copy(fontFeatureSettings = TabularNums),
                    color = IndustryAccent700,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * The stand-in page the design draws for each recent document: a bordered
 * sheet with a solid heading rule over ruled body lines. The design uses a
 * drawn glyph rather than a real thumbnail, which also means the library does
 * not have to open every document it lists.
 *
 * Both blocks are `repeating-linear-gradient`s in the design, and the sheet
 * clips them (`overflow: hidden`), so the body is cut off part-way rather than
 * ending on a whole line.
 */
@Composable
private fun PageGlyph() {
    Canvas(
        modifier = Modifier
            .size(width = 46.dp, height = 62.dp)
            .background(Color.White)
            .border(1.dp, IndustryDivider),
    ) {
        val border = 1.dp.toPx()
        val padX = border + 4.dp.toPx()
        val padY = border + 5.dp.toPx()
        val innerWidth = size.width - padX * 2

        clipRect(padX, padY, size.width - padX, size.height - padY) {
            // Heading: 3dp of ink every 6dp, over a 6dp-tall block.
            stripes(
                x = padX,
                top = padY,
                height = 6.dp.toPx(),
                width = innerWidth,
                ink = 3.dp.toPx(),
                period = 6.dp.toPx(),
                color = onSurfaceAlpha(0.6f),
            )
            // Body: a 1dp hairline every 4dp, over a 44dp block the sheet clips.
            stripes(
                x = padX,
                top = padY + 11.dp.toPx(),
                height = 44.dp.toPx(),
                width = innerWidth,
                ink = 1.dp.toPx(),
                period = 4.dp.toPx(),
                color = onSurfaceAlpha(0.32f),
            )
        }
    }
}

/** One `repeating-linear-gradient(to bottom, …)` band. */
private fun DrawScope.stripes(
    x: Float,
    top: Float,
    height: Float,
    width: Float,
    ink: Float,
    period: Float,
    color: Color,
) {
    var y = top
    while (y < top + height) {
        drawRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(width, ink.coerceAtMost(top + height - y)),
        )
        y += period
    }
}

private fun corpusLine(recents: List<RecentDocument>): String {
    if (recents.isEmpty()) return "No documents yet"
    val bytes = recents.sumOf { it.sizeBytes }
    val documents = if (recents.size == 1) "1 document" else "${recents.size} documents"
    return if (bytes > 0) "$documents · ${formatSize(bytes)} indexed" else documents
}

private fun documentMeta(document: RecentDocument): String = buildList {
    if (document.pageCount > 0) add("${document.pageCount} pp")
    if (document.sizeBytes > 0) add(formatSize(document.sizeBytes))
}.joinToString(" · ").ifEmpty { "Not yet measured" }

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return when {
        mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024)
        mb >= 10 -> String.format(Locale.US, "%.0f MB", mb)
        mb >= 0.1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
    }
}
