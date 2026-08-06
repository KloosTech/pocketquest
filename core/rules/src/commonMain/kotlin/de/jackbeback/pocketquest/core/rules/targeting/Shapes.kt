package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.euclideanDistanceTo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** All in-bounds tiles the shape covers, aimed at [at] (Cone/Line also need [origin] for direction). */
fun tilesInShape(origin: GridPos, at: GridPos, shape: Shape, map: BattleMap): Set<GridPos> =
    when (shape) {
        Shape.Single -> setOf(at)
        is Shape.Sphere -> sphereTiles(at, shape.radius, map)
        is Shape.Cone -> coneTiles(origin, at, shape.length, shape.degrees, map)
        is Shape.Line -> lineTiles(origin, at, shape.length, map)
        is Shape.Rect -> rectTiles(at, shape.width, shape.height, map)
    }

private fun boundedBox(center: GridPos, radius: Int, map: BattleMap): List<GridPos> = buildList {
    for (c in (center.col - radius)..(center.col + radius)) {
        for (r in (center.row - radius)..(center.row + radius)) {
            val pos = GridPos(c, r)
            if (map.inBounds(pos)) add(pos)
        }
    }
}

/** True circular burst — Euclidean, not Chebyshev, so it reads as round rather than a square. */
private fun sphereTiles(at: GridPos, radius: Int, map: BattleMap): Set<GridPos> =
    boundedBox(at, radius, map).filter { it.euclideanDistanceTo(at) <= radius }.toSet()

private fun coneTiles(origin: GridPos, at: GridPos, length: Int, degrees: Int, map: BattleMap): Set<GridPos> {
    if (origin == at || length <= 0) return emptySet()
    val dirAngle = atan2((at.row - origin.row).toDouble(), (at.col - origin.col).toDouble())
    val halfSpreadRad = (degrees / 2.0) * (PI / 180.0)

    return boundedBox(origin, length, map).filterTo(mutableSetOf()) { tile ->
        if (tile == origin) return@filterTo false
        if (origin.euclideanDistanceTo(tile) > length) return@filterTo false
        val angle = atan2((tile.row - origin.row).toDouble(), (tile.col - origin.col).toDouble())
        var diff = abs(angle - dirAngle)
        if (diff > PI) diff = 2 * PI - diff
        diff <= halfSpreadRad
    }
}

private fun Int.sign(): Int = if (this > 0) 1 else if (this < 0) -1 else 0

/** Snaps to one of 8 compass directions from [origin] toward [at] — single-file, no width parameter (matches doc05's Line(len)). */
private fun lineTiles(origin: GridPos, at: GridPos, length: Int, map: BattleMap): Set<GridPos> {
    if (origin == at || length <= 0) return emptySet()
    val stepCol = (at.col - origin.col).sign()
    val stepRow = (at.row - origin.row).sign()

    val result = mutableSetOf<GridPos>()
    var cur = GridPos(origin.col + stepCol, origin.row + stepRow)
    var steps = 0
    while (steps < length && map.inBounds(cur)) {
        result += cur
        cur = GridPos(cur.col + stepCol, cur.row + stepRow)
        steps++
    }
    return result
}

/** Axis-aligned, centered on [at] — not rotated toward the caster (simplification; noted in the pass-3 commit). */
private fun rectTiles(at: GridPos, width: Int, height: Int, map: BattleMap): Set<GridPos> {
    val fromCol = at.col - width / 2
    val fromRow = at.row - height / 2
    return buildSet {
        for (c in fromCol until fromCol + width) {
            for (r in fromRow until fromRow + height) {
                val pos = GridPos(c, r)
                if (map.inBounds(pos)) add(pos)
            }
        }
    }
}
