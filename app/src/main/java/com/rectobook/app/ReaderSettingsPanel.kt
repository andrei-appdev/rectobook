package com.rectobook.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rectobook.app.ui.theme.IndustryBackground
import com.rectobook.app.ui.theme.IndustryDivider

/**
 * How much of the screen the panel leaves around itself, on each side.
 *
 * Fractions rather than sizes, and the same fractions in both orientations:
 * the panel is 84% of the screen across and 62% of it down wherever it is
 * drawn, which is what keeps it the same object when the screen turns. What it
 * grows into is the room above it: the gap below is what holds it off the
 * bottom bar, and it still sits low, because the tone is judged on the page
 * over it - a panel in the middle would cover the part of the page being
 * looked at.
 */
private const val PANEL_SIDE = 0.08f
private const val PANEL_TOP = 0.23f
private const val PANEL_BOTTOM = 0.15f

/**
 * The reader's own way at the tone settings: the same controls the settings
 * screen carries, in a panel over the page they are being set for.
 *
 * There is no sample sheet here and no scrim over the page, for one reason -
 * the document itself is the sample. The screen has to draw a page because it
 * has none; this has the real one behind it, and covering it would take away
 * the thing that makes setting a tone here better than setting it there.
 *
 * The panel's ground is the bars' ground at the bars' opacity, so it reads as
 * another piece of the reader's chrome laid on the page rather than as a
 * window from somewhere else.
 */
@Composable
fun ReaderSettingsPanel(
    mode: PageColorMode,
    contrast: Float,
    systemBarRules: SystemBarRules,
    onModeChange: (PageColorMode) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSystemBarRulesChange: (SystemBarRules) -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A press off the panel is swallowed rather than taken as a way out.
        // The slider sits near the panel's edge, and a thumb that slides off
        // it would otherwise close the panel mid-adjustment and lose the tone
        // being judged. Swallowed rather than let through, so it does not land
        // on the page underneath either. Drawn under the panel and given no
        // indication, so nothing about it shows.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = maxWidth * PANEL_SIDE,
                    end = maxWidth * PANEL_SIDE,
                    top = maxHeight * PANEL_TOP,
                    bottom = maxHeight * PANEL_BOTTOM,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IndustryBackground.copy(alpha = 0.94f))
                    .border(1.dp, IndustryDivider)
                    // The panel swallows its own presses, so a touch that misses
                    // a control stops here rather than reaching the catcher
                    // behind it.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                // The panel is opened by a gesture and closed only by this mark
                // or by back - a tap outside it does nothing. Back is not on
                // screen, so the mark is: a panel you have to guess your way
                // out of is one you stop opening. It is outside the scrolling column below for
                // the same reason - a way out that scrolls off the top is one
                // more thing to guess at from the bottom of the settings.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconAction(
                        CloseIcon,
                        stringResource(R.string.settings_close),
                        onDismiss,
                        size = 16.dp,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 12.dp, bottom = 16.dp),
                ) {
                    ToneSettings(
                        mode = mode,
                        contrast = contrast,
                        onModeChange = onModeChange,
                        onContrastChange = onContrastChange,
                        modifier = Modifier.padding(end = 6.dp),
                    )

                    SystemBarSettings(
                        rules = systemBarRules,
                        onChange = onSystemBarRulesChange,
                        modifier = Modifier.padding(top = 22.dp, end = 6.dp),
                    )
                }
            }
        }
    }
}
