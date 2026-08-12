package de.jackbeback.pocketquest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Side

private const val WALL_SHADOW_DEPTH_FRACTION = 0.5f
private const val WALL_SHADOW_ALPHA = 0.45f

/**
 * docs/31-wall-shadow-casting.md: a floor cell shadows on whichever of its North/West edges
 * border a wall (fixed top-left light convention, not a real simulation) — [isWall] on that
 * neighbor or [hasWallEdge] on that exact side both count. A corner cell (wall to both North and
 * West) gets both strips, overlapping into a natural corner shadow — no special-casing needed. The
 * one gradient anywhere in this UI (everything else is flat ink-on-paper) — a deliberate exception
 * for this specific cast-shadow look. Kept in its own file, deliberately not folded into
 * `WallHatch.kt` — a separate mechanism from the crosshatch, not an extension of it. Shared between
 * `:ui`'s real Board and `:designer`'s Map editor canvas, same reasoning `drawWallHatch` is shared:
 * an author should see the same shadow while placing walls that they'll see in Playtest.
 *
 * A floor cell only diagonally touching a wall's outside corner (its North-West neighbor is a
 * wall, but neither its direct North nor West neighbor is) got no shadow at all under the
 * straight-edge rule above — a visible gap right at every convex corner of a wall mass. A small
 * radial patch anchored at that exact corner point closes it, fading over the same [depth] the
 * straight edges use so it reads as one continuous shadow wrapping the corner, not a mismatched
 * second effect.
 */
fun DrawScope.drawWallShadows(
    isWall: (GridPos) -> Boolean,
    hasWallEdge: (GridPos, Side) -> Boolean,
    cols: IntRange,
    rows: IntRange,
    tilePx: Float,
    zoom: Float,
    ink: Color,
    toScreen: (Offset) -> Offset,
) {
    if (cols.isEmpty() || rows.isEmpty()) return
    val screenTile = tilePx * zoom
    val depth = screenTile * WALL_SHADOW_DEPTH_FRACTION
    for (col in cols) {
        for (row in rows) {
            val pos = GridPos(col, row)
            if (isWall(pos)) continue
            val north = isWall(GridPos(col, row - 1)) || hasWallEdge(pos, Side.North)
            val west = isWall(GridPos(col - 1, row)) || hasWallEdge(pos, Side.West)
            val northWestCorner = !north && !west && isWall(GridPos(col - 1, row - 1))
            if (!north && !west && !northWestCorner) continue
            val topLeft = toScreen(Offset(col * tilePx, row * tilePx))
            if (northWestCorner) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(ink.copy(alpha = WALL_SHADOW_ALPHA), ink.copy(alpha = 0f)),
                        center = topLeft,
                        radius = depth,
                    ),
                    topLeft = topLeft,
                    size = Size(depth, depth),
                )
            }
            if (north) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(ink.copy(alpha = WALL_SHADOW_ALPHA), ink.copy(alpha = 0f)),
                        startY = topLeft.y,
                        endY = topLeft.y + depth,
                    ),
                    topLeft = topLeft,
                    size = Size(screenTile, depth),
                )
            }
            if (west) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(ink.copy(alpha = WALL_SHADOW_ALPHA), ink.copy(alpha = 0f)),
                        startX = topLeft.x,
                        endX = topLeft.x + depth,
                    ),
                    topLeft = topLeft,
                    size = Size(depth, screenTile),
                )
            }
        }
    }
}
