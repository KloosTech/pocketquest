package de.jackbeback.pocketquest.ui

import androidx.compose.ui.Modifier

/**
 * Mouse-wheel zoom — a desktop/trackpad-only input with no touch equivalent (pinch already covers
 * touch, via `detectTransformGestures` in [Board] itself, which is common code). [onZoomStep] fires
 * with +1/-1 per wheel notch when [enabled]. The real implementation (Compose Desktop's
 * `PointerEventType.Scroll`, `@ExperimentalComposeUiApi` and desktop-only) lives in the desktopMain
 * actual; Android/iOS get a no-op actual.
 */
expect fun Modifier.scrollWheelZoom(enabled: Boolean, onZoomStep: (Int) -> Unit): Modifier
