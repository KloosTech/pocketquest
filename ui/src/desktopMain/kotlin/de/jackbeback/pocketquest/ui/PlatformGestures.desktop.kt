package de.jackbeback.pocketquest.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.scrollWheelZoom(enabled: Boolean, onZoomStep: (Int) -> Unit): Modifier =
    onPointerEvent(PointerEventType.Scroll) { event ->
        if (!enabled) return@onPointerEvent
        val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
        when {
            scrollY > 0f -> onZoomStep(-1)
            scrollY < 0f -> onZoomStep(1)
        }
    }
