package de.jackbeback.pocketquest.ui.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * doc16's shared "ink register" widget set — originally built twice independently (`:ui`'s own
 * private `InkButton`, `:designer`'s `DButton`/`DTextField`/`DSelect`/`Stepper`/`Label`) before this
 * existed. No `compose.material`/`material3` anywhere here — a real version-skew bug (pass 12 of
 * this project) came from pulling material3 in, and this project settled on plain `foundation`
 * primitives project-wide rather than reintroducing that risk for a handful of widgets.
 *
 * [InkTooltip] is *not* here — it needs desktop-only `onPointerEvent(PointerEventType)` hover
 * detection (same reason `PlatformGestures.kt` needed an expect/actual split earlier), and hover
 * isn't a mobile concept anyway. It lives in `ui/desktopMain/.../ink/InkTooltip.kt` instead.
 */
@Composable
fun InkButton(label: String, modifier: Modifier = Modifier, emphasized: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .border(1.dp, INK)
            .background(if (emphasized) PAPER_SHEET else PAPER)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        BasicText(label, style = TextStyle(color = INK, fontSize = 14.sp))
    }
}

@Composable
fun InkTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.border(1.dp, INK_FAINT).padding(6.dp),
        textStyle = TextStyle(color = INK, fontSize = 13.sp),
        singleLine = true,
    )
}

/** A minimal dropdown select — no `compose.material` `DropdownMenu`. Scrolls once its option list grows past a few entries (e.g. picking one of 74 props). */
@Composable
fun <T> InkSelect(selected: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .border(1.dp, INK_FAINT)
                .background(PAPER)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = !open }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            BasicText(label(selected), style = TextStyle(color = INK, fontSize = 13.sp))
        }
        if (open) {
            Column(
                modifier = Modifier
                    .border(1.dp, INK)
                    .background(PAPER)
                    .widthIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    Box(
                        modifier = Modifier
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onSelect(option)
                                open = false
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        BasicText(label(option), style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

@Composable
fun InkStepper(value: Int, onValueChange: (Int) -> Unit, min: Int = 0, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        InkButton("-", onClick = { if (value > min) onValueChange(value - 1) })
        Box(modifier = Modifier.size(32.dp, 24.dp), contentAlignment = Alignment.Center) {
            BasicText("$value", style = TextStyle(color = INK, fontSize = 13.sp))
        }
        InkButton("+", onClick = { onValueChange(value + 1) })
    }
}

@Composable
fun InkLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier = modifier, style = TextStyle(color = INK_FAINT, fontSize = 11.sp))
}
