package com.djvureader.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.djvureader.app.ui.theme.BodyFont
import com.djvureader.app.ui.theme.IndustryAccent
import com.djvureader.app.ui.theme.IndustryDivider
import com.djvureader.app.ui.theme.IndustrySurface
import com.djvureader.app.ui.theme.IndustryText
import com.djvureader.app.ui.theme.TabularNums
import com.djvureader.app.ui.theme.onSurfaceAlpha

/**
 * The design's `.input`: a square, hairline-bordered field on the surface tone,
 * its border taking the accent while focused.
 *
 * Written out rather than dressed up from Material's text fields, which cannot
 * be talked out of their own container, indicator line and floating label.
 *
 * [autoFocus] opens the keyboard as the field appears, which suits a field that
 * is the only reason its panel exists.
 */
@Composable
internal fun IndustryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    fontSize: TextUnit = 14.sp,
    tabularNums: Boolean = false,
    autoFocus: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    val textStyle = TextStyle(
        fontFamily = BodyFont,
        fontSize = fontSize,
        color = IndustryText,
        fontFeatureSettings = if (tabularNums) TabularNums else null,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(IndustryAccent),
        interactionSource = interactionSource,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.height(height).focusRequester(focusRequester),
        decorationBox = { field ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(IndustrySurface)
                    .border(1.dp, if (focused) IndustryAccent else IndustryDivider)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = onSurfaceAlpha(0.4f)),
                    )
                }
                field()
            }
        },
    )
}
