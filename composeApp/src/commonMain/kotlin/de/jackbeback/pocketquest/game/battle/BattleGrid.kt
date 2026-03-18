package de.jackbeback.pocketquest.game.battle

import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import kotlin.math.abs

/** Width of the battle map in tiles. */
const val BATTLE_COLS = 14

/** Height of the battle map in tiles. */
const val BATTLE_ROWS = 9

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
