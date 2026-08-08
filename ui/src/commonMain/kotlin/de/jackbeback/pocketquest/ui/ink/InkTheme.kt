package de.jackbeback.pocketquest.ui.ink

import androidx.compose.ui.graphics.Color

/**
 * doc16's "ink on parchment" palette — the single source of truth for both the player-facing
 * battle screen (`:ui`'s own `App.kt`/`CombatLog.kt`) and the desktop designer (`:designer`), which
 * had independently arrived at the exact same hex values in two different files before this existed.
 * Names/values kept identical to what both call sites already used, so migrating either one is a
 * pure import-and-delete-the-local-copy — no call site needed to change.
 */
val INK = Color(0xFF2B241C)
val INK_FAINT = Color(0xFF9A8764)
val PAPER = Color(0xFFF4ECD8)
val PAPER_SHEET = Color(0xFFE7D9B8)
val DANGER = Color(0xFFB71C1C)
val OK = Color(0xFF2E7D32)

/** Status/objective yellow — previously an unnamed inline hex in both `CombatLog.kt` and the Map editor's Objective marker. */
val ACCENT = Color(0xFFF9A825)

/** "Blocked"/fizzle orange. */
val WARNING = Color(0xFFEF6C00)

/** Downed/death dark red — distinct from [DANGER] (damage) so a log line reads differently for "took damage" vs "died". */
val FATAL = Color(0xFF4E0A0A)
