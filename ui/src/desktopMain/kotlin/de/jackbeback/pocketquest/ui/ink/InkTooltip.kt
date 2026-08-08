@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package de.jackbeback.pocketquest.ui.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A hover-follows tooltip via [Popup] rather than an overflowing sibling `Box` — an overlay `Box`
 * gets painted over by later siblings in the same Row/Column (the next widget, the next section
 * label). A `Popup` draws in its own layer above the whole composition, so it can't be occluded.
 *
 * Desktop-only: needs `onPointerEvent(PointerEventType.Enter/Exit)`, which only fires meaningfully
 * with a real pointer device — not something touch-first mobile needs anyway. `:designer` (this
 * function's only consumer today) is desktop-only itself, so it's a natural fit here rather than in
 * `commonMain`'s `InkComponents.kt` alongside the truly cross-platform widgets.
 */
@Composable
fun InkTooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val yOffsetPx = with(LocalDensity.current) { 34.dp.roundToPx() }
    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
    ) {
        content()
        if (hovered) {
            Popup(offset = IntOffset(0, yOffsetPx), properties = PopupProperties(focusable = false)) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .background(INK)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    BasicText(text, style = TextStyle(color = PAPER, fontSize = 11.sp))
                }
            }
        }
    }
}
