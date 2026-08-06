package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GridPos(val col: Int, val row: Int)

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
