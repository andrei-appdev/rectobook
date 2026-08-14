package com.djvureader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.djvureader.app.ui.theme.Barlow
import com.djvureader.app.ui.theme.BarlowCondensed
import com.djvureader.app.ui.theme.BlueprintCorner
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryAccent700
import com.djvureader.app.ui.theme.IndustryBackground
import com.djvureader.app.ui.theme.IndustryDivider
import com.djvureader.app.ui.theme.IndustryNeutral900
import com.djvureader.app.ui.theme.IndustrySurface
import com.djvureader.app.ui.theme.IndustryText
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.blueprintCorners
import com.djvureader.app.ui.theme.onSurfaceAlpha
import kotlinx.coroutines.launch

/**
 * The design's go-to-page panel: a bottom sheet carrying a numeric field, a
 * JUMP action and a row of quick marks.
 *
 * [pageCount] is the number of pages; pages are numbered from 1 here, as they
 * are everywhere the reader shows them, and [onJump] receives that 1-based
 * number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoToPageSheet(
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pageCount }

    // Let the sheet slide away before the page moves under it, rather than
    // having it disappear the instant a jump is taken.
    fun jumpTo(target: Int) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onJump(target) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = IndustryBackground,
        dragHandle = null,
        scrimColor = IndustryNeutral900.copy(alpha = 0.5f),
        // The sheet manages its own insets below, so its content can sit
        // against the keyboard rather than the sheet being padded away from it.
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .blueprintCorners(corners = BlueprintCorner.Top, overhang = 0.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Go to page",
                    style = TextStyle(
                        fontFamily = BarlowCondensed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                    ),
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = "of $pageCount",
                    style = MaterialTheme.typography.labelMedium
                        .copy(fontFeatureSettings = TabularNums),
                    color = onSurfaceAlpha(0.45f),
                    modifier = Modifier.alignByBaseline(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PageNumberField(
                    text = text,
                    onTextChange = { text = it },
                    onSubmit = { page?.let(::jumpTo) },
                    modifier = Modifier.weight(1f),
                )
                JumpButton(enabled = page != null, onClick = { page?.let(::jumpTo) })
            }

            // The design fills this row from the document's outline. DjVuLibre
            // exposes outline entries as URLs that may name a page rather than
            // number it, and the ddjvu C API has no name-to-number lookup, so
            // until that is resolved these are the jumps we actually know.
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                QuickMark(number = 1, label = "First", onClick = { jumpTo(1) })
                if (pageCount > 1) {
                    QuickMark(
                        number = pageCount,
                        label = "Last",
                        onClick = { jumpTo(pageCount) },
                    )
                }
            }
        }
    }
}

/** The design's `.input`, which Material's own text fields cannot be talked into. */
@Composable
private fun PageNumberField(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // The sheet exists only to take a number, so it opens ready for one.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = text,
        onValueChange = { onTextChange(it.filter(Char::isDigit).take(6)) },
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = Barlow,
            fontSize = 17.sp,
            color = IndustryText,
            fontFeatureSettings = TabularNums,
        ),
        cursorBrush = SolidColor(IndustryAccent),
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        modifier = modifier.height(44.dp).focusRequester(focusRequester),
        decorationBox = { field ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IndustrySurface)
                    .border(1.dp, if (focused) IndustryAccent else IndustryDivider)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Page number",
                        style = TextStyle(fontFamily = Barlow, fontSize = 17.sp),
                        color = onSurfaceAlpha(0.4f),
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun JumpButton(enabled: Boolean, onClick: () -> Unit) {
    // `.btn:disabled { opacity: 0.45 }`
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .size(width = 92.dp, height = 44.dp)
            .background(IndustryAccent.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "JUMP",
            style = MaterialTheme.typography.labelLarge,
            color = IndustryBackground.copy(alpha = alpha),
        )
    }
}

/** One of the design's marks: a page number in accent, then what is there. */
@Composable
private fun QuickMark(number: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .border(1.dp, IndustryDivider)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$number",
            style = TextStyle(
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.04.em,
                fontFeatureSettings = TabularNums,
            ),
            color = IndustryAccent700,
        )
        Text(
            text = label,
            style = TextStyle(fontFamily = Barlow, fontSize = 12.sp),
            color = onSurfaceAlpha(0.65f),
        )
    }
}
