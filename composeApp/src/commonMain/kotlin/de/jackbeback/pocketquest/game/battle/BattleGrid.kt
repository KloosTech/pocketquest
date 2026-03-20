package de.jackbeback.pocketquest.game.battle

import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import kotlin.math.abs

/** Width of the battle map in tiles — matches the Throneroom tileset column count. */
const val BATTLE_COLS = 9

/** Height of the battle map in tiles — matches the Throneroom tileset row count. */
const val BATTLE_ROWS = 13

/** Zoom factor applied to the battle viewport (each tile rendered at 2× its fit-to-screen size). */
const val BATTLE_VIEWPORT_SCALE = 2f

/**
 * Chebyshev distance between two grid positions.
 * Diagonal steps count as 1, matching classic tactical RPG movement.
 */
fun chebyshevDistance(a: PositionComponent, b: PositionComponent): Int =
    maxOf(abs(a.col - b.col), abs(a.row - b.row))

/** Clamp a position to remain within the battle grid. */
fun PositionComponent.clampToGrid(): PositionComponent =
    PositionComponent(
        col = col.coerceIn(0, BATTLE_COLS - 1),
        row = row.coerceIn(0, BATTLE_ROWS - 1),
    )
