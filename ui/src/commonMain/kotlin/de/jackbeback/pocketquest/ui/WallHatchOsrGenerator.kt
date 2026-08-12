package de.jackbeback.pocketquest.ui

import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.HatchLine
import de.jackbeback.pocketquest.core.model.WallHatchOsrParams
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * docs/33-wall-hatch-osr-packing.md / docs/34-wall-hatch-osr-configurable-params.md: the OSR
 * hatch's actual generator — a real space-filling pass over a fine sub-grid, run ONCE
 * (`:designer`'s "Regenerate Hatch" button, or once-per-map on Save if it has no baked geometry
 * yet) and its result stored as [HatchLine]s, never re-run per frame. `WallHatchOsr.kt` only ever
 * renders whatever this produced — no generation logic lives there, or anywhere in `:ui`'s live
 * render path.
 *
 * Replaces the earlier independent-scatter approach (docs/32) entirely: no stroke there knew what
 * any other stroke had already claimed, so gaps and overlaps were inherent to the technique, not a
 * tuning problem. This one tracks per-sub-cell occupancy and grows each stroke (or GROUP of
 * parallel strokes — [WallHatchOsrParams.minGroupSize]/[maxGroupSize], the reference image's
 * "clusters of 3 to 5 parallel lines") only as far as free space allows.
 *
 * Every tunable is a [WallHatchOsrParams] field now (docs/34) rather than a hardcoded constant —
 * two rounds of live tuning each needed a code change to try a different look, which is exactly
 * the kind of thing that should be an authoring input instead.
 */
private val SNAP_ANGLE_STEPS = listOf(0f to (1 to 0), 45f to (1 to 1), 90f to (0 to 1), 135f to (-1 to 1))

/** Rotate a sub-cell direction step 90° — used to offset a group's parallel lines perpendicular to their shared growth direction. */
private fun perpendicular(step: Pair<Int, Int>): Pair<Int, Int> = -step.second to step.first

/** Chebyshev-ring distance (whole tiles) from a Wall tile to the nearest non-wall tile, capped at [fadeDistanceCells]. */
private fun distanceToFloor(isWall: (GridPos) -> Boolean, pos: GridPos, fadeDistanceCells: Int): Int {
    if (!isWall(pos)) return 0
    for (d in 1..fadeDistanceCells) {
        for (dc in -d..d) {
            for (dr in -d..d) {
                if (maxOf(abs(dc), abs(dr)) != d) continue
                if (!isWall(GridPos(pos.col + dc, pos.row + dr))) return d
            }
        }
    }
    return fadeDistanceCells + 1
}

private fun isEligible(isWall: (GridPos) -> Boolean, pos: GridPos, fadeDistanceCells: Int): Boolean =
    isWall(pos) && distanceToFloor(isWall, pos, fadeDistanceCells) <= fadeDistanceCells

/** One shared base angle per [WallHatchOsrParams.angleRegionSubcells]-sub-cell block — keeps nearby strokes reading as coherent parallel "streams" instead of independently-rolled crossing streaks. */
private fun regionAngleStep(subCol: Int, subRow: Int, seed: Long, regionSubcells: Int): Pair<Int, Int> {
    val regionCol = subCol.floorDiv(regionSubcells)
    val regionRow = subRow.floorDiv(regionSubcells)
    val rng = Random(seed xor (regionCol.toLong() * 6364136223846793005L) xor (regionRow.toLong() * 1442695040888963407L))
    return SNAP_ANGLE_STEPS.random(rng).second
}

/**
 * The actual packing pass. [isWall] and the [cols]/[rows] tile bounds define the region to
 * generate over (typically the whole map); [seed] makes it fully deterministic — same wall layout
 * plus same seed always reproduces the identical result, so a catalog Save that regenerates
 * nothing new never visually churns an already-baked map. A different [seed] (the "Regenerate
 * Hatch" button's job) is the only thing that ever produces a different roll. [params] is every
 * other tunable (docs/34).
 */
fun generateWallHatchOsr(isWall: (GridPos) -> Boolean, cols: IntRange, rows: IntRange, seed: Long, params: WallHatchOsrParams = WallHatchOsrParams()): List<HatchLine> {
    if (cols.isEmpty() || rows.isEmpty()) return emptyList()
    val subcellsPerTile = params.subcellsPerTile.coerceAtLeast(1)
    val subColRange = (cols.first * subcellsPerTile)..(cols.last * subcellsPerTile + subcellsPerTile - 1)
    val subRowRange = (rows.first * subcellsPerTile)..(rows.last * subcellsPerTile + subcellsPerTile - 1)

    fun tileOf(subCol: Int, subRow: Int) = GridPos(subCol.floorDiv(subcellsPerTile), subRow.floorDiv(subcellsPerTile))
    fun eligible(subCol: Int, subRow: Int) =
        subCol in subColRange && subRow in subRowRange && isEligible(isWall, tileOf(subCol, subRow), params.fadeDistanceCells)

    val occupied = HashSet<Long>()
    fun key(c: Int, r: Int) = c.toLong() shl 32 or (r.toLong() and 0xFFFFFFFFL)
    fun isOccupied(c: Int, r: Int) = key(c, r) in occupied
    fun occupy(c: Int, r: Int) { occupied += key(c, r) }

    val eligibleCells = buildList {
        for (sc in subColRange) for (sr in subRowRange) if (eligible(sc, sr)) add(sc to sr)
    }
    if (eligibleCells.isEmpty()) return emptyList()

    val rng = Random(seed)
    val lines = mutableListOf<HatchLine>()
    var occupiedEligibleCount = 0
    val targetCount = (eligibleCells.size * params.targetCoverage).toInt()
    val attemptMultiplier = 8 // internal safety valve, not authorial — bounds worst-case time, no visual meaning of its own
    val maxAttempts = eligibleCells.size * attemptMultiplier
    var attempts = 0

    /** Grows one line from ([startCol],[startRow]) both ways along ([stepC],[stepR]) until blocked; marks its span occupied regardless of length. Returns the endpoints, or null if too short to draw. */
    fun growLine(startCol: Int, startRow: Int, stepC: Int, stepR: Int): HatchLine? {
        var forward = 0
        while (forward < params.maxLineLengthSubcells) {
            val c = startCol + stepC * (forward + 1)
            val r = startRow + stepR * (forward + 1)
            if (!eligible(c, r) || isOccupied(c, r)) break
            forward++
        }
        var backward = 0
        while (backward < params.maxLineLengthSubcells - forward) {
            val c = startCol - stepC * (backward + 1)
            val r = startRow - stepR * (backward + 1)
            if (!eligible(c, r) || isOccupied(c, r)) break
            backward++
        }
        val totalLen = forward + backward + 1
        for (i in -backward..forward) occupy(startCol + stepC * i, startRow + stepR * i)
        occupiedEligibleCount += totalLen
        if (totalLen < params.minLineLengthSubcells) return null

        val x0 = (startCol - stepC * backward).toFloat() / subcellsPerTile
        val y0 = (startRow - stepR * backward).toFloat() / subcellsPerTile
        val x1 = (startCol + stepC * forward).toFloat() / subcellsPerTile
        val y1 = (startRow + stepR * forward).toFloat() / subcellsPerTile
        // The growth direction itself must stay exactly grid-aligned for occupancy tracking to
        // work at all — [angleJitterDegrees] instead wobbles the already-committed line's drawn
        // endpoints around its own midpoint, a purely cosmetic hand-drawn imperfection that never
        // touches the occupancy grid (docs/34).
        if (params.angleJitterDegrees <= 0f) return HatchLine(x0, y0, x1, y1, params.lineWidthFraction)
        val theta = (rng.nextFloat() * 2f - 1f) * params.angleJitterDegrees * PI.toFloat() / 180f
        val midX = (x0 + x1) / 2f
        val midY = (y0 + y1) / 2f
        val dx = x0 - midX
        val dy = y0 - midY
        val cosT = cos(theta)
        val sinT = sin(theta)
        val rx = dx * cosT - dy * sinT
        val ry = dx * sinT + dy * cosT
        return HatchLine(midX + rx, midY + ry, midX - rx, midY - ry, params.lineWidthFraction)
    }

    while (occupiedEligibleCount < targetCount && attempts < maxAttempts) {
        attempts++
        val (startCol, startRow) = eligibleCells[rng.nextInt(eligibleCells.size)]
        if (isOccupied(startCol, startRow)) continue

        val step = regionAngleStep(startCol, startRow, seed, params.angleRegionSubcells.coerceAtLeast(1))
        val (perpC, perpR) = perpendicular(step)
        val groupSize = if (params.maxGroupSize <= params.minGroupSize) {
            params.minGroupSize.coerceAtLeast(1)
        } else {
            params.minGroupSize + rng.nextInt(params.maxGroupSize - params.minGroupSize + 1)
        }
        val centerOffset = (groupSize - 1) / 2
        for (j in 0 until groupSize) {
            val offset = j - centerOffset
            val lineStartCol = startCol + perpC * offset
            val lineStartRow = startRow + perpR * offset
            if (!eligible(lineStartCol, lineStartRow) || isOccupied(lineStartCol, lineStartRow)) continue
            growLine(lineStartCol, lineStartRow, step.first, step.second)?.let { lines += it }
        }
    }
    return lines
}
