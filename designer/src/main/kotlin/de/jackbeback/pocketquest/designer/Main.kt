package de.jackbeback.pocketquest.designer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.ui.App as BattleApp

/**
 * doc16's desktop content-authoring tool — a real desktop-shaped window, unlike :app's phone-shaped
 * one. Playtest opens the real battle screen (`de.jackbeback.pocketquest.ui.App`) as a *second*
 * `Window` in this same `application { }` scope, not by calling `runDesktopApp` (which wraps its
 * own `application { }` — nesting two of those is the wrong pattern, this is Compose Desktop's
 * documented way to open an additional window on demand).
 */
fun main() = application {
    var playtest by remember { mutableStateOf<Pair<GameState, Catalog>?>(null) }

    Window(onCloseRequest = ::exitApplication, title = "PocketQuest Designer", state = WindowState(size = DpSize(1800.dp, 750.dp))) {
        DesignerApp(onPlaytest = { state, catalog -> playtest = state to catalog })
    }

    playtest?.let { (state, catalog) ->
        Window(onCloseRequest = { playtest = null }, title = "PocketQuest — Playtest") {
            BattleApp(state, catalog)
        }
    }
}
