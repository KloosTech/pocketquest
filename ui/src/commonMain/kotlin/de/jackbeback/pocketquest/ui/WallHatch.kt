package de.jackbeback.pocketquest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import de.jackbeback.pocketquest.core.model.GridPos
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural hand-drawn crosshatch for Wall-type cells — replaces an earlier sprite-sheet-based
 * attempt (per-cell sub-patches sampled from a small tileable sheet) that looked visibly tiled: hand-
 * drawn crosshatch fundamentally can't be chopped into independent cell-aligned squares, since the
 * strokes need to run continuously across cell boundaries to read as one rock face, not repeating
 * wallpaper. Validated as a Python/PIL prototype (matching this exact algorithm) before porting.
 *
 * Iterates per-Wall-cell, not over the whole viewport — an earlier "one continuous field across the
 * entire visible area, then punch every floor cell on top" version scanned the full viewport's anchor
 * grid every frame regardless of how little of it was actually walls, heavy enough on a large open
 * map to visibly stutter panning and drop clicks. [clipRect] confines each cell's own strokes to that
 * cell's rect instead — anchor points still sit on a jittered grid spaced far finer than one tile
 * (NOT one anchor per cell) and keyed by [anchorRandom] on the anchor's own coordinate, so adjacent
 * Wall cells' clipped stroke fragments still line up into one continuous rock face with no seam:
 * drawing the same unclipped stroke once, or drawing its two cell-clipped halves separately from each
 * cell's own pass, produces the same pixels either way.
 *
 * Shared between `:ui`'s real Board and `:designer`'s own Map editor canvas — [isWall] is the only
 * thing that differs between them (a runtime `BattleMap.tileAt` vs the editor's expanded terrain
 * map), so this stays decoupled from either module's specific map representation.
 */
private const val SPACING_FRACTION = 0.172f // matches the chosen "v4" prototype: spacing=11 at a 64px cell
private const val STROKE_LEN_FRACTION = 0.344f // stroke_len=22 at a 64px cell
private const val STROKE_WIDTH_FRACTION = 0.031f // stroke_w=2 at a 64px cell
private const val ANGLE_JITTER = 0.5f

private fun anchorRandom(gc: Int, gr: Int): Random = Random((gc.toLong() * 374761393L) xor (gr.toLong() * 668265263L))

/**
 * 1.0 deep inside a wall mass, fading toward 0.35 at its boundary with non-wall cells — the "denser
 * near edges, thinning toward open floor" look the hand-drawn Geomorph references actually use,
 * approximated cheaply via a wall-neighbor count rather than a real distance transform.
 */
private fun wallDensity(isWall: (GridPos) -> Boolean, pos: GridPos): Float {
    if (!isWall(pos)) return 0f
    var openNeighbors = 0
    for (dc in -1..1) for (dr in -1..1) {
        if (dc == 0 && dr == 0) continue
        if (!isWall(GridPos(pos.col + dc, pos.row + dr))) openNeighbors++
    }
    return (1f - openNeighbors * 0.08f).coerceIn(0.35f, 1f)
}

/** Draws hatch for every Wall cell in [cols]/[rows] (world-space tile ranges) — a no-op for any cell [isWall] rejects, so callers can pass the full visible bounds without pre-filtering. */
fun DrawScope.drawWallHatch(
    isWall: (GridPos) -> Boolean,
    cols: IntRange,
    rows: IntRange,
    tilePx: Float,
    zoom: Float,
    ink: Color,
    toScreen: (Offset) -> Offset,
) {
    if (cols.isEmpty() || rows.isEmpty()) return
    val spacing = tilePx * SPACING_FRACTION
    val strokeLen = tilePx * STROKE_LEN_FRACTION
    val strokeWidth = tilePx * STROKE_WIDTH_FRACTION
    // Anchors within this margin of a cell's edge can still draw a stroke reaching into it — without
    // the margin, strokes near a cell boundary would get clipped to a stub instead of their full length.
    val margin = strokeLen
    val densityCache = HashMap<GridPos, Float>()
    fun densityAt(pos: GridPos) = densityCache.getOrPut(pos) { wallDensity(isWall, pos) }

    for (col in cols) {
        for (row in rows) {
            val pos = GridPos(col, row)
            val density = densityAt(pos)
            if (density <= 0f) continue

            val cellLeft = col * tilePx
            val cellTop = row * tilePx
            val screenTopLeft = toScreen(Offset(cellLeft, cellTop))
            val screenTile = tilePx * zoom
            clipRect(screenTopLeft.x, screenTopLeft.y, screenTopLeft.x + screenTile, screenTopLeft.y + screenTile) {
                val gcRange = floor((cellLeft - margin) / spacing).toInt()..ceil((cellLeft + tilePx + margin) / spacing).toInt()
                val grRange = floor((cellTop - margin) / spacing).toInt()..ceil((cellTop + tilePx + margin) / spacing).toInt()
                for (gc in gcRange) {
                    for (gr in grRange) {
                        val rng = anchorRandom(gc, gr)
                        val wx = gc * spacing + (rng.nextFloat() - 0.5f) * spacing
                        val wy = gr * spacing + (rng.nextFloat() - 0.5f) * spacing
                        if (rng.nextFloat() > density) continue

                        val angle = rng.nextFloat() * PI.toFloat() + (rng.nextFloat() - 0.5f) * ANGLE_JITTER * 2f
                        val length = strokeLen * (0.7f + rng.nextFloat() * 0.6f)
                        val width = strokeWidth * (0.8f + rng.nextFloat() * 0.4f)
                        val dx = cos(angle) * length / 2f
                        val dy = sin(angle) * length / 2f
                        drawLine(
                            color = ink,
                            start = toScreen(Offset(wx - dx, wy - dy)),
                            end = toScreen(Offset(wx + dx, wy + dy)),
                            strokeWidth = width * zoom,
                        )
                    }
                }
            }
        }
    }
}
