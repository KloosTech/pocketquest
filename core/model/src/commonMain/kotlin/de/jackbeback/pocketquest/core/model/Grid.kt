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
 * What the rules need from a tile and nothing else (docs/16-art-direction.md) — walkable,
 * movement cost, blocks line of sight, and hazard are independent axes on purpose: rubble blocks
 * movement but not sight, tall grass the reverse. [hazard] is a data-only marker for now — no
 * on-enter effect is wired to it yet, that's future content-authoring work, not an engine gap.
 */
@Serializable
data class TileType(
    val walkable: Boolean = true,
    val moveCost: Int = 1,
    val blocksLoS: Boolean = false,
    val hazard: Boolean = false,
) {
    companion object {
        val Floor = TileType()
        val Wall = TileType(walkable = false, blocksLoS = true)
        val Difficult = TileType(moveCost = 2)
        val Hazard = TileType(hazard = true)
    }
}

/**
 * Minimal battle map: just enough for invariant checking (bounds + walkable) plus terrain
 * (docs/17-engine-gaps.md 1.4 — was uniform walkable/blocked with no TileType until now). A tile
 * absent from [terrain] is [TileType.Floor] — most of a map is plain floor, so this stays sparse
 * rather than requiring every cell to be listed.
 */
@Serializable
data class BattleMap(
    val width: Int,
    val height: Int,
    val terrain: Map<GridPos, TileType> = emptyMap(),
) {
    fun inBounds(pos: GridPos): Boolean =
        pos.col in 0 until width && pos.row in 0 until height

    fun tileAt(pos: GridPos): TileType = terrain[pos] ?: TileType.Floor

    fun isWalkable(pos: GridPos): Boolean =
        inBounds(pos) && tileAt(pos).walkable

    fun moveCost(pos: GridPos): Int = tileAt(pos).moveCost

    fun blocksLoS(pos: GridPos): Boolean = tileAt(pos).blocksLoS

    /** Every unwalkable tile — the old `blockedTiles` field's role, for rendering/queries that only care about walls. */
    val walls: Set<GridPos> get() = terrain.filterValues { !it.walkable }.keys
}
