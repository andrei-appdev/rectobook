package com.djvureader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.IndustryDivider
import com.djvureader.app.ui.theme.IndustryNeutral900
import com.djvureader.app.ui.theme.SectionLabel
import com.djvureader.app.ui.theme.onSurfaceAlpha

/**
 * Asks before leaving the document for somewhere outside it.
 *
 * A link is written by whoever made the file, and its text need not agree with
 * where it goes - so the address itself is what this shows, in full and
 * wrapped rather than cut short, since the interesting part of a misleading
 * URL is rarely at the front.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenLinkSheet(
    url: String,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RectangleShape,
        containerColor = IndustryBackground,
        dragHandle = null,
        scrimColor = IndustryNeutral900.copy(alpha = 0.5f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Hairline()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
            ) {
                Text(
                    text = "LEAVE THIS DOCUMENT",
                    style = SectionLabel,
                    color = onSurfaceAlpha(0.55f),
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    // Long enough to judge by, short of filling the screen with
                    // a hostile one.
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetAction(
                        label = "CANCEL",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    SheetAction(
                        label = "OPEN",
                        onClick = onOpen,
                        filled = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The design's `.btn`, or `.btn-primary` when [filled]. */
@Composable
private fun SheetAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .then(
                if (filled) Modifier.background(IndustryAccent)
                else Modifier.border(1.dp, IndustryDivider),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) IndustryBackground else onSurfaceAlpha(0.75f),
        )
    }
}
