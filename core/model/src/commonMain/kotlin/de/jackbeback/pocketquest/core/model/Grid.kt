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

/** One of the four sides of a grid cell — which edge a [WallEdge] sits on. */
@Serializable
enum class Side { North, South, East, West }

fun Side.opposite(): Side = when (this) {
    Side.North -> Side.South
    Side.South -> Side.North
    Side.East -> Side.West
    Side.West -> Side.East
}

private fun Side.step(): GridPos = when (this) {
    Side.North -> GridPos(0, -1)
    Side.South -> GridPos(0, 1)
    Side.East -> GridPos(1, 0)
    Side.West -> GridPos(-1, 0)
}

/**
 * A constructed wall on one side of [pos] — the Map editor's "room divider" style: thin, sits on a
 * tile edge, both neighbouring cells keep their full floor area. Distinct from
 * [TileType.walkable] `= false` (a whole cell consumed — natural rubble/a cave-in/a pillar), which
 * this is layered on top of rather than replacing, so every existing cell-wall map/test keeps
 * working unchanged. A physical wall needs only one canonical entry (from either side's
 * perspective) — [BattleMap.hasWallEdge] checks both directions.
 */
@Serializable
data class WallEdge(val pos: GridPos, val side: Side)

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
    val wallEdges: Set<WallEdge> = emptySet(),
    /** Carried straight from [BattleMapDef] purely for `:ui`'s Board to render — no rules-engine consumer, same as `floorTexture`/`wallHatch` below. */
    val props: List<PropPlacement> = emptyList(),
    val floorTexture: String? = null,
    val wallHatch: Boolean = true,
) {
    fun inBounds(pos: GridPos): Boolean =
        pos.col in 0 until width && pos.row in 0 until height

    fun tileAt(pos: GridPos): TileType = terrain[pos] ?: TileType.Floor

    fun isWalkable(pos: GridPos): Boolean =
        inBounds(pos) && tileAt(pos).walkable

    fun moveCost(pos: GridPos): Int = tileAt(pos).moveCost

    fun blocksLoS(pos: GridPos): Boolean = tileAt(pos).blocksLoS

    /** Every unwalkable tile — the old `blockedTiles` field's role, for rendering/queries that only care about whole-cell walls. */
    val walls: Set<GridPos> get() = terrain.filterValues { !it.walkable }.keys

    fun hasWallEdge(pos: GridPos, side: Side): Boolean {
        if (WallEdge(pos, side) in wallEdges) return true
        return WallEdge(pos + side.step(), side.opposite()) in wallEdges
    }

    /**
     * Movement legality for one step from [from] to an adjacent (incl. diagonal) [to] — purely the
     * edge-wall check; bounds/walkable/occupancy remain the caller's job, same split as before this
     * existed. A diagonal step passes through the corner shared by four cells; a wall attached to
     * that corner from *any* of them blocks it (checking only the two edges touching [from] misses
     * a cell walled on every side but approached diagonally, since none of its walls touch [from]).
     */
    fun canCross(from: GridPos, to: GridPos): Boolean {
        val dc = to.col - from.col
        val dr = to.row - from.row
        return when {
            dc == 0 && dr == -1 -> !hasWallEdge(from, Side.North)
            dc == 0 && dr == 1 -> !hasWallEdge(from, Side.South)
            dc == 1 && dr == 0 -> !hasWallEdge(from, Side.East)
            dc == -1 && dr == 0 -> !hasWallEdge(from, Side.West)
            dc == 1 && dr == -1 -> noCornerWall(from, Side.North, Side.East, to, Side.South, Side.West)
            dc == 1 && dr == 1 -> noCornerWall(from, Side.South, Side.East, to, Side.North, Side.West)
            dc == -1 && dr == -1 -> noCornerWall(from, Side.North, Side.West, to, Side.South, Side.East)
            dc == -1 && dr == 1 -> noCornerWall(from, Side.South, Side.West, to, Side.North, Side.East)
            else -> true
        }
    }

    private fun noCornerWall(from: GridPos, fromSideA: Side, fromSideB: Side, to: GridPos, toSideA: Side, toSideB: Side): Boolean =
        !hasWallEdge(from, fromSideA) && !hasWallEdge(from, fromSideB) &&
            !hasWallEdge(to, toSideA) && !hasWallEdge(to, toSideB)
}

private operator fun GridPos.plus(delta: GridPos): GridPos = GridPos(col + delta.col, row + delta.row)
