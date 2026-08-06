package de.jackbeback.pocketquest.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.Serializable

@Serializable
data class GridPos(val col: Int, val row: Int)

/** Grid distance where diagonal movement costs the same as cardinal — matches the uniform-cost BattleMap. */
fun GridPos.chebyshevDistanceTo(other: GridPos): Int =
    max(abs(col - other.col), abs(row - other.row))

/** True circular distance — used for Shape.Sphere so a "burst radius" reads as round, not square. */
fun GridPos.euclideanDistanceTo(other: GridPos): Double {
    val dx = (col - other.col).toDouble()
    val dy = (row - other.row).toDouble()
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * Minimal battle map: just enough for invariant checking (bounds + walkable).
 * Full targeting geometry (LoS, shapes) is out of scope until doc 05.
 */
@Serializable
data class BattleMap(
    val width: Int,
    val height: Int,
    val blockedTiles: Set<GridPos> = emptySet(),
) {
    fun inBounds(pos: GridPos): Boolean =
        pos.col in 0 until width && pos.row in 0 until height

    fun isWalkable(pos: GridPos): Boolean =
        inBounds(pos) && pos !in blockedTiles
}
