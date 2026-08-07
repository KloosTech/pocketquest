package de.jackbeback.pocketquest.ui

import androidx.compose.ui.Modifier

/** No mouse wheel on touch — pinch (common `detectTransformGestures` in [Board]) is the zoom gesture here. */
actual fun Modifier.scrollWheelZoom(enabled: Boolean, onZoomStep: (Int) -> Unit): Modifier = this
