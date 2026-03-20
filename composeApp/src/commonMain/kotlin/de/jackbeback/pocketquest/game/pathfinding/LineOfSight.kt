package de.jackbeback.pocketquest.game.pathfinding

import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.game.battle.chebyshevDistance
import kotlin.math.abs

/**
 * Returns true if there is a clear line of sight from [from] to [to] —
 * i.e., no non-walkable tile lies on the straight line between them.
 *
 * Uses Bresenham's line algorithm. Checks all intermediate cells (excluding
 * both endpoints). The target cell itself is never checked so an enemy standing
 * on a walkable cell is always reachable once LOS to that cell is clear.
 *
 * If [tileMap] is null, LOS is always clear (no terrain to block).
 */
fun hasLineOfSight(
    from: PositionComponent,
    to: PositionComponent,
    tileMap: TileMap?,
): Boolean {
    if (tileMap == null) return true
    if (from == to) return true

    // Diagonal adjacency corner check: a 1-step diagonal is blocked if either shared
    // cardinal neighbour is a wall — Bresenham visits no intermediate cells for such a
    // step, so without this check a grunt could swing through a wall corner.
    if (abs(to.col - from.col) == 1 && abs(to.row - from.row) == 1) {
        if (!tileMap.isWalkable(to.col, from.row) || !tileMap.isWalkable(from.col, to.row)) return false
    }

    var x = from.col
    var y = from.row
    val dx = abs(to.col - from.col)
    val dy = abs(to.row - from.row)
    val sx = if (to.col > from.col) 1 else -1
    val sy = if (to.row > from.row) 1 else -1
    var err = dx - dy

    while (true) {
        val e2 = 2 * err
        if (e2 > -dy) { err -= dy; x += sx }
        if (e2 < dx)  { err += dx; y += sy }
        if (x == to.col && y == to.row) break
        if (!tileMap.isWalkable(x, y)) return false
    }

    return true
}

const val PLAYER_VISION_RANGE = 16

/**
 * Returns the set of (col, row) tiles visible from [from].
 * When [tileMap] is null (no terrain), returns the entire grid as visible.
 * Uses [hasLineOfSight] for each candidate tile within [range].
 */
fun computeVisibleTiles(
    from: PositionComponent,
    tileMap: TileMap?,
    gridCols: Int,
    gridRows: Int,
    range: Int = PLAYER_VISION_RANGE,
): Set<Pair<Int, Int>> {
    if (tileMap == null) {
        return buildSet {
            for (c in 0 until gridCols) for (r in 0 until gridRows) add(c to r)
        }
    }
    return buildSet {
        for (c in 0 until gridCols) {
            for (r in 0 until gridRows) {
                val target = PositionComponent(c, r)
                if (chebyshevDistance(from, target) > range) continue
                if (hasLineOfSight(from, target, tileMap)) add(c to r)
            }
        }
    }
}
