package de.jackbeback.pocketquest.ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GameState

/** Desktop-only: `Window`/`application` aren't available on Android/iOS Compose targets. */
fun runDesktopApp(initialState: GameState, catalog: Catalog) = application {
    Window(onCloseRequest = ::exitApplication, title = "PocketQuest") {
        App(initialState, catalog)
    }
}
