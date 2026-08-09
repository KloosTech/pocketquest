package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.WallEdge
import kotlin.math.abs

/**
 * Bresenham's line algorithm — true from [from] to [to] iff no LoS-blocking tile
 * ([BattleMap.blocksLoS], doc17 1.4 — distinct from walkable: rubble can block movement without
 * blocking sight, tall grass the reverse) lies on the straight line between them (endpoints
 * excluded, so a target standing on a visible tile is always reachable once LoS to that tile is
 * clear), AND no [WallEdge] crosses the straight sightline between the two cell centres. Strict —
 * no partial/"peek through" mode; fog-of-war's own wall-tile reveal is a separate adjacency rule
 * in Visibility.kt, not a softened version of this. Adapted from v1's LineOfSight.kt (kept as
 * reference only).
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

    if (map.wallEdges.isNotEmpty()) {
        val sightA = Pt(from.col.toDouble(), from.row.toDouble())
        val sightB = Pt(to.col.toDouble(), to.row.toDouble())
        for (edge in map.wallEdges) {
            val (wallA, wallB) = edge.segmentEndpoints()
            if (segmentsIntersect(sightA, sightB, wallA, wallB)) return false
        }
    }
    return true
}

private data class Pt(val x: Double, val y: Double)

/**
 * A [WallEdge] as the unit-length segment straddling the boundary it sits on — cell (col, row)
 * occupies the unit square centred on that integer point, so its edges sit at the ±0.5 offsets.
 */
private fun WallEdge.segmentEndpoints(): Pair<Pt, Pt> {
    val cx = pos.col.toDouble()
    val cy = pos.row.toDouble()
    return when (side) {
        Side.North -> Pt(cx - 0.5, cy - 0.5) to Pt(cx + 0.5, cy - 0.5)
        Side.South -> Pt(cx - 0.5, cy + 0.5) to Pt(cx + 0.5, cy + 0.5)
        Side.East -> Pt(cx + 0.5, cy - 0.5) to Pt(cx + 0.5, cy + 0.5)
        Side.West -> Pt(cx - 0.5, cy - 0.5) to Pt(cx - 0.5, cy + 0.5)
    }
}

private const val EPSILON = 1e-9

private fun orientation(a: Pt, b: Pt, c: Pt): Int {
    val v = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    return when {
        v > EPSILON -> 1
        v < -EPSILON -> -1
        else -> 0
    }
}

private fun onSegment(a: Pt, b: Pt, p: Pt): Boolean =
    p.x >= minOf(a.x, b.x) - EPSILON && p.x <= maxOf(a.x, b.x) + EPSILON &&
        p.y >= minOf(a.y, b.y) - EPSILON && p.y <= maxOf(a.y, b.y) + EPSILON

/** Standard CCW-orientation segment intersection test, incl. the collinear/touching edge cases. */
private fun segmentsIntersect(a1: Pt, a2: Pt, b1: Pt, b2: Pt): Boolean {
    val o1 = orientation(a1, a2, b1)
    val o2 = orientation(a1, a2, b2)
    val o3 = orientation(b1, b2, a1)
    val o4 = orientation(b1, b2, a2)
    if (o1 != o2 && o3 != o4) return true
    if (o1 == 0 && onSegment(a1, a2, b1)) return true
    if (o2 == 0 && onSegment(a1, a2, b2)) return true
    if (o3 == 0 && onSegment(b1, b2, a1)) return true
    if (o4 == 0 && onSegment(b1, b2, a2)) return true
    return false
}
