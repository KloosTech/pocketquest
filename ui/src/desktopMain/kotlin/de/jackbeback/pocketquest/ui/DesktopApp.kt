package de.jackbeback.pocketquest.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GameState

/** Roughly a modern phone's portrait aspect ratio (~9:19.5) — the desktop target is a stand-in for a mobile-first app, not a desktop-shaped one. */
private val PHONE_WINDOW_SIZE = DpSize(390.dp, 844.dp)

/**
 * Desktop-only: `Window`/`application` aren't available on Android/iOS Compose targets.
 * No explicit `position` — `WindowPosition.Aligned(Alignment.Center)` centers on the wrong
 * display in a multi-monitor setup (empirically: landed at y=-960, off the primary display
 * entirely). The platform default placement is left alone.
 */
fun runDesktopApp(initialState: GameState, catalog: Catalog) = application {
    val windowState = WindowState(size = PHONE_WINDOW_SIZE)
    Window(onCloseRequest = ::exitApplication, title = "PocketQuest", state = windowState) {
        App(initialState, catalog)
    }
}
