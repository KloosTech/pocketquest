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

private val EIGHT_DIRECTIONS = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)

/**
 * BFS reachability within [maxSteps] over walkable, unoccupied tiles.
 * Movement here has uniform cost (BattleMap has no terrain weighting), so
 * this is equivalent to what a full A* would produce — no pathfinding
 * algorithm needed, just a flood fill.
 */
fun reachableTiles(origin: GridPos, maxSteps: Int, map: BattleMap, occupancy: Map<GridPos, EntityId>): Set<GridPos> {
    val visited = mutableMapOf(origin to 0)
    val queue = ArrayDeque<GridPos>()
    queue.add(origin)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val dist = visited.getValue(current)
        if (dist >= maxSteps) continue
        for ((dc, dr) in EIGHT_DIRECTIONS) {
            val next = GridPos(current.col + dc, current.row + dr)
            if (next in visited) continue
            if (!map.isWalkable(next)) continue
            if (occupancy.containsKey(next)) continue
            visited[next] = dist + 1
            queue.add(next)
        }
    }
    return visited.keys - origin
}
