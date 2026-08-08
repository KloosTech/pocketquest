package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.chebyshevDistanceTo

/**
 * A* over walkable, unoccupied tiles (docs/17-engine-gaps.md 1.2 — never ported from v1's
 * `AStarPathfinder.kt`). Returns the concrete step-by-step route from [from] to [to], excluding
 * [from] and including [to] — exactly the shape `Effect.MoveAlong.path` expects — or null if [to]
 * is blocked/occupied or unreachable within [maxCost].
 *
 * [moveCost] defaults to [map]'s own per-tile terrain cost (1.4 — difficult terrain costs more to
 * enter); pass a different function to price something [BattleMap] doesn't model itself.
 */
fun findPath(
    from: GridPos,
    to: GridPos,
    map: BattleMap,
    occupancy: Map<GridPos, EntityId>,
    maxCost: Int = Int.MAX_VALUE,
    moveCost: (GridPos) -> Int = { map.moveCost(it) },
): List<GridPos>? {
    fun blocked(pos: GridPos) = !map.isWalkable(pos) || occupancy.containsKey(pos)
    if (blocked(to)) return null
    if (from == to) return emptyList()

    data class Node(val pos: GridPos, val g: Int, val f: Int)

    val open = mutableListOf(Node(from, 0, from.chebyshevDistanceTo(to)))
    val gCost = mutableMapOf(from to 0)
    val cameFrom = mutableMapOf<GridPos, GridPos>()

    while (open.isNotEmpty()) {
        val current = open.minBy { it.f }
        open.remove(current)
        if (current.pos == to) return reconstructPath(cameFrom, from, to)

        for ((dc, dr) in EIGHT_DIRECTIONS) {
            val next = GridPos(current.pos.col + dc, current.pos.row + dr)
            if (!map.inBounds(next) || blocked(next) || !map.canCross(current.pos, next)) continue
            val tentativeG = current.g + moveCost(next)
            if (tentativeG > maxCost) continue
            if (tentativeG < (gCost[next] ?: Int.MAX_VALUE)) {
                gCost[next] = tentativeG
                cameFrom[next] = current.pos
                open += Node(next, tentativeG, tentativeG + next.chebyshevDistanceTo(to))
            }
        }
    }
    return null
}

/** What an already-resolved [findPath] route actually costs to walk — the number `canPerform`/`perform` must spend, not the route's raw tile count. */
fun List<GridPos>.pathCost(map: BattleMap): Int = sumOf { map.moveCost(it) }

private fun reconstructPath(cameFrom: Map<GridPos, GridPos>, from: GridPos, to: GridPos): List<GridPos> {
    val path = mutableListOf<GridPos>()
    var current = to
    while (current != from) {
        path.add(current)
        current = cameFrom[current] ?: break
    }
    path.reverse()
    return path
}
