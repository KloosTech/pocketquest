package de.jackbeback.pocketquest.ui.nav

import androidx.compose.runtime.Composable

/** iOS's own edge-swipe-back is a system affordance this app doesn't participate in yet — nothing to hook. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
