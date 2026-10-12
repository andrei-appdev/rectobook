package com.rectobook.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rectobook.app.ui.theme.IndustryAccent
import com.rectobook.app.ui.theme.IndustryAccent700
import com.rectobook.app.ui.theme.IndustryDivider
import com.rectobook.app.ui.theme.LightIndustry
import com.rectobook.app.ui.theme.SectionLabel
import com.rectobook.app.ui.theme.TabularNums
import com.rectobook.app.ui.theme.ink
import com.rectobook.app.ui.theme.onSurfaceAlpha

/**
 * The library, styled after the design's first screen: wordmark, a single
 * primary action, and the recent documents as bordered cards carrying their own
 * reading progress.
 *
 * [thumbnail] is read during composition, so a card redraws as soon as its page
 * arrives; [onRequestThumbnail] is what sets that going, and is given the size
 * in pixels the card will actually draw.
 */
@Composable
fun LibraryScreen(
    recents: List<RecentDocument>,
    thumbnail: (RecentDocument) -> Bitmap?,
    onRequestThumbnail: (RecentDocument, Int, Int) -> Unit,
    onPick: () -> Unit,
    onSettings: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().clearOfSystemBars()) {
        // Everything above the cards stays put while they scroll under it. The
        // wordmark and the action are what the screen is for, and a long shelf
        // that pushed the way to open a document off the top would hide it
        // behind a scroll back up; the section row goes with them because it
        // names the column the cards' page markers sit in.
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp)) {
            // The wordmark and the way out to the settings. Neither the version
            // nor a count of what has been read belongs here: the screen's job
            // is to open a document.
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = upperStringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                IconAction(MenuIcon, stringResource(R.string.library_settings), onSettings)
            }

            PrimaryAction(
                icon = FolderIcon,
                label = upperStringResource(R.string.library_open_file),
                onClick = onPick,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )

            if (recents.isNotEmpty()) {
                Text(
                    text = upperStringResource(R.string.library_recent),
                    style = SectionLabel,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
        }

        // The rule the cards pass under. Without it a card scrolled to the top
        // edge is simply sliced through, which reads as a drawing error rather
        // than as the end of the list's window. Full width, like every other
        // rule in the design.
        if (recents.isNotEmpty()) Hairline()

        // Full width rather than inset, so the overscroll glow meets the edges
        // of the screen; the content is what carries the 18dp margin. The first
        // card is set off the rule by the same 13dp that separates the cards
        // from each other.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 28.dp),
        ) {
            items(count = recents.size, key = { recents[it].key }) { index ->
                val document = recents[index]
                RecentCard(
                    document = document,
                    thumbnail = thumbnail(document),
                    onRequestThumbnail = onRequestThumbnail,
                    onClick = { onOpenRecent(document) },
                    modifier = Modifier.padding(bottom = 13.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentCard(
    document: RecentDocument,
    thumbnail: Bitmap?,
    onRequestThumbnail: (RecentDocument, Int, Int) -> Unit,
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
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        PageThumbnail(
            document = document,
            thumbnail = thumbnail,
            onRequest = onRequestThumbnail,
        )
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
                modifier = Modifier.padding(top = 2.dp),
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
                    text = stringResource(R.string.library_page_marker, document.lastPage + 1),
                    style = MaterialTheme.typography.labelSmall
                        .copy(fontFeatureSettings = TabularNums),
                    color = IndustryAccent700,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** The design's page slot: a 62 x 78 sheet, bordered, on white. */
internal val ThumbnailWidth = 62.dp
internal val ThumbnailHeight = 78.dp

/**
 * The document's own first page, in the slot the design fills with a drawn
 * glyph. The glyph stays as the fallback - it is what a card shows while its
 * page is being rendered, and what it keeps if the document cannot be read at
 * all.
 *
 * The page is rendered at exactly the pixels this box occupies and fitted
 * inside it, so a page of any shape sits centred on the sheet rather than being
 * cropped to the slot.
 */
@Composable
private fun PageThumbnail(
    document: RecentDocument,
    thumbnail: Bitmap?,
    onRequest: (RecentDocument, Int, Int) -> Unit,
) {
    val width = with(LocalDensity.current) { ThumbnailWidth.roundToPx() }
    val height = with(LocalDensity.current) { ThumbnailHeight.roundToPx() }

    LaunchedEffect(document.key, document.sizeBytes) {
        onRequest(document, width, height)
    }

    Box(
        modifier = Modifier
            .size(width = ThumbnailWidth, height = ThumbnailHeight)
            .background(Color.White)
            .border(1.dp, IndustryDivider),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail == null) {
            // Paper ink, not the app's. This sheet is white in both palettes,
            // so it cannot follow a theme that turns the ink pale.
            PageGlyph(LightIndustry.ink(0.6f), LightIndustry.ink(0.32f))
        } else {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(1.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * The stand-in page the design draws for each recent document: a solid heading
 * rule over ruled body lines.
 *
 * Both blocks are `repeating-linear-gradient`s in the design, and the sheet
 * clips them (`overflow: hidden`), so the body is cut off part-way rather than
 * ending on a whole line.
 *
 * The two inks are the caller's, because the settings screen draws this same
 * sheet in whatever tone is being set rather than in the library's fixed one,
 * and [scale] because it draws it larger: the geometry is written for the
 * [ThumbnailWidth] slot, and a bigger sheet wants a bigger hand rather than the
 * same rules spread thinner over it.
 */
@Composable
internal fun PageGlyph(headingInk: Color, bodyInk: Color, scale: Float = 1f) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        fun unit(dp: Float) = dp.dp.toPx() * scale

        val border = 1.dp.toPx()
        val padX = border + unit(5f)
        val padY = border + unit(6f)
        val innerWidth = size.width - padX * 2

        clipRect(padX, padY, size.width - padX, size.height - padY) {
            // Heading: 3dp of ink every 6dp, over a 7dp-tall block.
            stripes(
                x = padX,
                top = padY,
                height = unit(7f),
                width = innerWidth,
                ink = unit(3f),
                period = unit(6f),
                color = headingInk,
            )
            // Body: a 1dp hairline every 4dp, over a 56dp block the sheet clips.
            stripes(
                x = padX,
                top = padY + unit(13f),
                height = unit(56f),
                width = innerWidth,
                ink = unit(1f).coerceAtLeast(1.dp.toPx()),
                period = unit(4f),
                color = bodyInk,
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

@Composable
private fun documentMeta(document: RecentDocument): String = buildList {
    document.format?.let { add(it.label) }
    if (document.pageCount > 0) {
        add(pluralStringResource(R.plurals.library_page_count, document.pageCount, document.pageCount))
    }
    if (document.sizeBytes > 0) add(formatSize(document.sizeBytes))
}.joinToString(" · ").ifEmpty { stringResource(R.string.library_not_measured) }

/**
 * A file size in the reader's own language: the unit is a word, and the decimal
 * separator is a comma in most of Europe.
 */
@Composable
private fun formatSize(bytes: Long): String {
    val locale = readingLocale()
    val mb = bytes / 1024.0 / 1024.0
    return when {
        mb >= 1024 -> stringResource(R.string.size_gb, String.format(locale, "%.1f", mb / 1024))
        mb >= 10 -> stringResource(R.string.size_mb, String.format(locale, "%.0f", mb))
        mb >= 0.1 -> stringResource(R.string.size_mb, String.format(locale, "%.1f", mb))
        else -> stringResource(
            R.string.size_kb,
            String.format(locale, "%d", (bytes / 1024).coerceAtLeast(1)),
        )
    }
}
