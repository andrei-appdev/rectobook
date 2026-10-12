package com.rectobook.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectobook.app.ui.theme.IndustryAccent
import com.rectobook.app.ui.theme.IndustryBackground
import com.rectobook.app.ui.theme.IndustryNeutral900
import kotlinx.coroutines.launch

/**
 * The design's go-to-page panel: a bottom sheet carrying a numeric field and a
 * JUMP action, and nothing else.
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
                .imePadding()
                .navigationBarsPadding(),
        ) {
            // The design's `.dialog` carries a hairline border. On a sheet this
            // wide only its top edge is ever on screen, and that edge is what
            // separates the panel from the page behind it.
            Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IndustryTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(6) },
                    placeholder = stringResource(R.string.goto_page_number),
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                    fontSize = 17.sp,
                    tabularNums = true,
                    // The sheet exists only to take a number, so it opens ready
                    // for one.
                    autoFocus = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { page?.let(::jumpTo) }),
                )
                JumpButton(enabled = page != null, onClick = { page?.let(::jumpTo) })
            }
        }
    }
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
            text = upperStringResource(R.string.goto_jump),
            style = MaterialTheme.typography.labelLarge,
            color = IndustryBackground.copy(alpha = alpha),
        )
    }
}
