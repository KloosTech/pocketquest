package de.jackbeback.pocketquest.game.pathfinding

import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.game.battle.chebyshevDistance
import kotlin.math.abs

/**
 * Finds the shortest walkable path from [from] to [to] using A*.
 * Returns the path as a list of positions excluding [from], including [to].
 * Returns null if [to] is unreachable.
 *
 * @param isBlocked  Returns true for cells that cannot be entered (wall, occupied, out-of-bounds).
 * @param moveCost   Returns the terrain movement cost for entering a cell (default 1).
 */
fun findPath(
    from: PositionComponent,
    to: PositionComponent,
    gridCols: Int,
    gridRows: Int,
    isBlocked: (col: Int, row: Int) -> Boolean,
    moveCost: (col: Int, row: Int) -> Int = { _, _ -> 1 },
): List<PositionComponent>? {
    if (isBlocked(to.col, to.row)) return null
    if (from == to) return emptyList()

    data class Node(val pos: PositionComponent, val g: Int, val f: Int)

    val openSet = ArrayDeque<Node>()
    val gCost = mutableMapOf<PositionComponent, Int>()
    val cameFrom = mutableMapOf<PositionComponent, PositionComponent>()

    gCost[from] = 0
    openSet.add(Node(from, 0, chebyshevDistance(from, to)))

    val dirs = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to  0,           1 to  0,
        -1 to  1, 0 to  1, 1 to  1,
    )

    while (openSet.isNotEmpty()) {
        val current = openSet.minByOrNull { it.f }!!
        openSet.remove(current)

        if (current.pos == to) {
            return reconstructPath(cameFrom, from, to)
        }

        for ((dc, dr) in dirs) {
            val nc = current.pos.col + dc
            val nr = current.pos.row + dr
            if (nc !in 0 until gridCols || nr !in 0 until gridRows) continue
            if (isBlocked(nc, nr)) continue

            val neighbor = PositionComponent(nc, nr)
            val tentativeG = current.g + moveCost(nc, nr)
            if (tentativeG < (gCost[neighbor] ?: Int.MAX_VALUE)) {
                gCost[neighbor] = tentativeG
                cameFrom[neighbor] = current.pos
                val f = tentativeG + chebyshevDistance(neighbor, to)
                openSet.add(Node(neighbor, tentativeG, f))
            }
        }
    }

    return null
}

private fun reconstructPath(
    cameFrom: Map<PositionComponent, PositionComponent>,
    from: PositionComponent,
    to: PositionComponent,
): List<PositionComponent> {
    val path = mutableListOf<PositionComponent>()
    var current = to
    while (current != from) {
        path.add(current)
        current = cameFrom[current] ?: break
    }
    path.reverse()
    return path
}
