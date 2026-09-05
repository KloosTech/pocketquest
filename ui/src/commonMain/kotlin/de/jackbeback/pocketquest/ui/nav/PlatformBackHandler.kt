package de.jackbeback.pocketquest.ui.nav

import androidx.compose.runtime.Composable

/** Hooks the platform's own back gesture/button — a real one on Android, a no-op everywhere else (desktop has no back button; iOS's own swipe-back is a system affordance this app doesn't participate in yet). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
