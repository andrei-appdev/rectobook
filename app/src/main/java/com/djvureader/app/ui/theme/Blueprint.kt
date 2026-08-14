package com.djvureader.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws the design system's registration marks: a short cross at each corner,
 * straddling the edge so the arms sit partly outside the element. This is the
 * motif `.blueprint > .corner` produces in styles.css, where an 11px mark is
 * offset -6px past the corner.
 *
 * The marks are drawn outside the layout bounds, so the element needs room
 * around it - give surrounding layouts a little padding or the arms clip.
 */
fun Modifier.blueprintCorners(
    color: Color = onSurfaceAlpha(0.55f),
    armLength: Dp = 11.dp,
    overhang: Dp = 6.dp,
    thickness: Dp = 1.dp,
): Modifier = drawWithContent {
    drawContent()

    val arm = armLength.toPx()
    val over = overhang.toPx()
    val line = thickness.toPx()
    val w = size.width
    val h = size.height

    // Distance of each stroke from the edge it parallels; negative, so the
    // strokes sit just outside the box exactly as the CSS mark does.
    val gap = arm - 2 * over
    val from = -over
    val to = arm - over

    fun mark(x: Float, y: Float, sx: Int, sy: Int) {
        // Vertical arm
        drawLine(
            color = color,
            start = Offset(x + gap * sx, y + from * sy),
            end = Offset(x + gap * sx, y + to * sy),
            strokeWidth = line,
        )
        // Horizontal arm
        drawLine(
            color = color,
            start = Offset(x + from * sx, y + gap * sy),
            end = Offset(x + to * sx, y + gap * sy),
            strokeWidth = line,
        )
    }

    mark(0f, 0f, 1, 1)
    mark(w, 0f, -1, 1)
    mark(0f, h, 1, -1)
    mark(w, h, -1, -1)
}
