package de.jackbeback.pocketquest.designer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    // found live — `:ui`'s App has several unkeyed `remember`s
    // (`state`, `world`, `player`) that only ever initialize ONCE per composition slot. `:app`'s own
    // real RunApp gets this for free (its App(...) call site genuinely leaves/re-enters composition
    // between encounters, since it's nested inside a conditional `when` branch), but this Window's
    // `BattleApp(state, catalog)` call is the SAME call site every time Playtest is clicked — if the
    // previous Playtest window is still open when a new one is launched, Compose just recomposes it
    // with new state/catalog PARAMETERS while leaving App's own internal `remember`s untouched,
    // silently running the OLD encounter's stale turn order/entities. Worse, `startEncounter`
    // defaults to `seed = 0L`, so replaying the SAME encounter can produce a data-class-EQUAL
    // GameState anyway — keying on the state/catalog VALUES wouldn't reliably catch it either.
    // `playtestSession` is a plain incrementing counter, guaranteed to differ every single click
    // regardless of content — `key(playtestSession)` forces a full dispose+recreate of the whole
    // subtree, exactly mirroring the fresh composition `:app`'s flow already gets for free.
    var playtestSession by remember { mutableStateOf(0) }

    Window(onCloseRequest = ::exitApplication, title = "PocketQuest Designer", state = WindowState(size = DpSize(1800.dp, 750.dp))) {
        DesignerApp(onPlaytest = { state, catalog ->
            playtestSession++
            playtest = state to catalog
        })
    }

    playtest?.let { (state, catalog) ->
        key(playtestSession) {
            Window(onCloseRequest = { playtest = null }, title = "PocketQuest — Playtest") {
                BattleApp(state, catalog)
            }
        }
    }
}
