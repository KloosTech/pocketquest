package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GridPos
import kotlin.math.abs

/**
 * Bresenham's line algorithm — true from [from] to [to] iff no LoS-blocking tile
 * ([BattleMap.blocksLoS], doc17 1.4 — distinct from walkable: rubble can block movement without
 * blocking sight, tall grass the reverse) lies on the straight line between them (endpoints
 * excluded, so a target standing on a visible tile is always reachable once LoS to that tile is
 * clear). Adapted from v1's LineOfSight.kt (kept as reference only).
 */
fun hasLineOfSight(from: GridPos, to: GridPos, map: BattleMap): Boolean {
    if (from == to) return true

    // A 1-step diagonal is blocked if either shared cardinal neighbour blocks LoS —
    // Bresenham visits no intermediate cells for such a step, so without this check
    // a mover could see (and target) through a wall corner.
    if (abs(to.col - from.col) == 1 && abs(to.row - from.row) == 1) {
        if (map.blocksLoS(GridPos(to.col, from.row)) || map.blocksLoS(GridPos(from.col, to.row))) return false
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
        if (e2 < dx) { err += dx; y += sy }
        if (x == to.col && y == to.row) break
        if (map.blocksLoS(GridPos(x, y))) return false
    }
    return true
}
