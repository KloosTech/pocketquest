package de.jackbeback.pocketquest.ui.nav

import androidx.compose.runtime.Composable

/** No hardware/gesture back on desktop — nothing to hook. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
