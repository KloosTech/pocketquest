package de.jackbeback.pocketquest.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Top-level app chrome only — Splash, the roster Hub, Settings, and "a run is active" (`InRun`).
 * `RunApp.kt`'s `RunScreen` keeps its own domain-state-driven branching (loot reveal / node choice /
 * per-`NodeType` content) entirely separate from this: a run is inherently linear, there's no "back"
 * to give it, so folding it into a back stack would just be navigation machinery standing in for
 * state that's already the real source of truth.
 */
sealed interface Screen {
    data object Splash : Screen
    data object CharacterCreation : Screen
    data object Hub : Screen
    data object Settings : Screen
    data object Inventory : Screen
    data object InRun : Screen
}

/** A minimal push/pop stack — Android's first real consumer needs a genuine back target (Settings), which desktop never had a reason to grow. */
class NavController(start: Screen) {
    var stack: List<Screen> by mutableStateOf(listOf(start))
        private set

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (canPop) stack = stack.dropLast(1)
    }

    /**
     * Replaces the ROOT entry only, leaving anything pushed on top (Settings) undisturbed — a
     * domain-state change (a run starting while Settings happens to be open) shouldn't yank the
     * player off the screen they're looking at.
     */
    fun setRoot(screen: Screen) {
        stack = listOf(screen) + stack.drop(1)
    }
}

@Composable
fun rememberNavController(start: Screen): NavController = remember { NavController(start) }
