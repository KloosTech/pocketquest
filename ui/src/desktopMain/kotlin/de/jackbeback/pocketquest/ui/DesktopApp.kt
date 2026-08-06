package de.jackbeback.pocketquest.ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.jackbeback.pocketquest.core.model.GameState

/** Desktop-only: `Window`/`application` aren't available on Android/iOS Compose targets. */
fun runDesktopApp(state: GameState, log: List<String>) = application {
    Window(onCloseRequest = ::exitApplication, title = "PocketQuest — end-to-end smoke test") {
        App(state, log)
    }
}
