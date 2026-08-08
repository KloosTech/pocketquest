package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.chebyshevDistanceTo

fun rangeInTiles(range: Range): Int = when (range) {
    Range.Melee -> 1
    is Range.Tiles -> range.n
    Range.SelfRange -> 0
}

/** All in-bounds tiles within Chebyshev [range] of [origin] — the grid-tactics distance metric, distinct from Sphere's Euclidean burst. */
fun tilesWithinRange(origin: GridPos, range: Int, map: BattleMap): List<GridPos> = buildList {
    for (c in (origin.col - range)..(origin.col + range)) {
        for (r in (origin.row - range)..(origin.row + range)) {
            val pos = GridPos(c, r)
            if (map.inBounds(pos) && origin.chebyshevDistanceTo(pos) <= range) add(pos)
        }
    }
}

/**
 * Cardinal directions listed before diagonals: [findPath]'s A* has zero extra cost for a diagonal
 * step, so a straight cardinal move and a diagonal detour reaching the same tile can tie on `f`.
 * `open.minBy { it.f }` returns the first-encountered minimum, so expansion order is a real
 * tie-break — cardinal-first means a straight 2-tile drop resolves as two straight steps instead of
 * a diagonal zigzag (found live: "moved 1 diagonal bottom left then 1 diagonal bottom right" for a
 * straight-down move). Doesn't change [reachableTiles]'s result (a set, order-independent) or any
 * move's actual AP cost — purely which equal-cost route gets walked.
 */
internal val EIGHT_DIRECTIONS = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0, -1 to -1, -1 to 1, 1 to -1, 1 to 1)

/**
 * Every tile reachable from [origin] for at most [maxCost], over walkable, unoccupied tiles.
 * Cost-aware (docs/17-engine-gaps.md 1.4 — `BattleMap` used to be uniform-cost, so a plain BFS
 * flood fill was equivalent to Dijkstra; difficult terrain broke that equivalence, so this now
 * tracks the cheapest cost to reach each tile rather than just its step count). Linear min-scan
 * over the frontier, same style as [findPath] — commonMain has no `java.util.PriorityQueue`.
 */
fun reachableTiles(origin: GridPos, maxCost: Int, map: BattleMap, occupancy: Map<GridPos, EntityId>): Set<GridPos> {
    val bestCost = mutableMapOf(origin to 0)
    val frontier = mutableListOf(origin)
    while (frontier.isNotEmpty()) {
        val current = frontier.minBy { bestCost.getValue(it) }
        frontier.remove(current)
        val costSoFar = bestCost.getValue(current)
        for ((dc, dr) in EIGHT_DIRECTIONS) {
            val next = GridPos(current.col + dc, current.row + dr)
            if (!map.isWalkable(next)) continue
            if (occupancy.containsKey(next)) continue
            if (!map.canCross(current, next)) continue
            val tentative = costSoFar + map.moveCost(next)
            if (tentative > maxCost) continue
            if (tentative < (bestCost[next] ?: Int.MAX_VALUE)) {
                bestCost[next] = tentative
                frontier += next
            }
        }
    }
    return bestCost.keys - origin
}
