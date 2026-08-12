package de.jackbeback.pocketquest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.jackbeback.pocketquest.core.model.HatchLine

/**
 * docs/33-wall-hatch-osr-packing.md: pure playback — [lines] is pre-generated content
 * (`generateWallHatchOsr`, run once in `:designer`, baked into the map), not computed here. Every
 * coordinate in [HatchLine] is an unscaled tile-unit float; [tilePx] converts to world pixels
 * before [toScreen] maps to the viewport, same two-step conversion every other map-geometry draw
 * call already uses. [cols]/[rows] cull the same way every other viewport-aware draw call here
 * does — the baked list is per-map, not per-visible-region, so a big map shouldn't redraw every
 * stroke on every frame regardless of pan/zoom.
 */
fun DrawScope.drawWallHatchOsr(lines: List<HatchLine>, cols: IntRange, rows: IntRange, tilePx: Float, zoom: Float, ink: Color, toScreen: (Offset) -> Offset) {
    if (cols.isEmpty() || rows.isEmpty()) return
    val colRange = (cols.first - 1).toFloat()..(cols.last + 2).toFloat()
    val rowRange = (rows.first - 1).toFloat()..(rows.last + 2).toFloat()
    for (line in lines) {
        if (line.x0 !in colRange && line.x1 !in colRange) continue
        if (line.y0 !in rowRange && line.y1 !in rowRange) continue
        drawLine(
            color = ink,
            start = toScreen(Offset(line.x0 * tilePx, line.y0 * tilePx)),
            end = toScreen(Offset(line.x1 * tilePx, line.y1 * tilePx)),
            strokeWidth = line.width * tilePx * zoom,
        )
    }
}
