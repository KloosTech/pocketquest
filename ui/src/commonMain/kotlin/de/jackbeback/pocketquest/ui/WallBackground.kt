package de.jackbeback.pocketquest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import de.jackbeback.pocketquest.ui.ink.PAPER
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** How many map tiles one repeat of `background.png` spans — the image isn't per-tile content (unlike a floor texture's sub-patch sampling), it's a single wallpaper tiled across world space at this fixed scale. */
private const val BACKGROUND_TILE_SPAN = 6f

/**
 * docs/35-wall-background-punch-through.md: tiles [image] behind the map's own
 * `[0, mapWidthTiles] x [0, mapHeightTiles]` footprint, extended outward by [marginTiles] tiles on
 * every side — NOT the whole pannable viewport (the original behavior; found live to read as "the
 * map lost in an infinite texture" rather than a bounded scene with a frame around it). A single
 * repeat spans [BACKGROUND_TILE_SPAN] tiles regardless of map size, so it reads the same at any
 * zoom/map dimensions instead of distorting to fit. Still culled against the visible viewport for
 * performance — the map+margin rect is only ever a hard OUTER limit, not a reason to draw tiles
 * that are off-screen anyway.
 *
 * [screenToWorld]/[toScreen] are lambdas (not raw camera/zoom) for the same reason [drawWallHatch]
 * takes a `toScreen` lambda — `:ui`'s Board and `:designer`'s Map editor canvas each have their own
 * differently-shaped conversion helpers, so this function stays decoupled from either one's
 * specific signature. Shared between the two for the same reason `drawWallHatch` is: an author
 * should see the same background while editing that they'll see in Playtest.
 *
 * Clipped precisely to the map+margin rect (not just tile-granularity culled) — found live:
 * without an explicit clip, a plain `drawImage` call has no idea where its own logical boundary is
 * supposed to end, the same class of bleed [drawWallHatch]'s own per-cell `clipRect` already guards
 * against, here doubling as the actual mechanism that makes the margin a hard edge rather than a
 * fuzzy "roughly around there."
 *
 * The margin band itself fades to [PAPER] rather than cutting off hard at [marginTiles] — four edge
 * `Brush.linearGradient` bands plus four corner `Brush.radialGradient` squares, the exact same
 * "edges + corner" composition [drawWallShadows] already established, just applied to all four
 * sides/corners here (a directional light only ever needed two) instead of two.
 */
fun DrawScope.drawBackgroundImage(
    image: ImageBitmap,
    mapWidthTiles: Int,
    mapHeightTiles: Int,
    marginTiles: Int,
    tilePx: Float,
    zoom: Float,
    screenToWorld: (Offset) -> Offset,
    toScreen: (Offset) -> Offset,
) {
    val spanWorld = BACKGROUND_TILE_SPAN * tilePx
    val marginWorld = marginTiles.coerceAtLeast(0) * tilePx
    val boundMinX = -marginWorld
    val boundMinY = -marginWorld
    val boundMaxX = mapWidthTiles * tilePx + marginWorld
    val boundMaxY = mapHeightTiles * tilePx + marginWorld
    if (boundMinX >= boundMaxX || boundMinY >= boundMaxY) return

    // Intersect the map+margin bound with what's actually visible — the bound is a hard limit,
    // the viewport is a performance cull, both apply.
    val viewTopLeft = screenToWorld(Offset.Zero)
    val viewBottomRight = screenToWorld(Offset(size.width, size.height))
    val drawMinX = boundMinX.coerceAtLeast(viewTopLeft.x - spanWorld)
    val drawMinY = boundMinY.coerceAtLeast(viewTopLeft.y - spanWorld)
    val drawMaxX = boundMaxX.coerceAtMost(viewBottomRight.x + spanWorld)
    val drawMaxY = boundMaxY.coerceAtMost(viewBottomRight.y + spanWorld)
    if (drawMinX >= drawMaxX || drawMinY >= drawMaxY) return

    val startCol = floor(drawMinX / spanWorld).toInt()
    val endCol = ceil(drawMaxX / spanWorld).toInt()
    val startRow = floor(drawMinY / spanWorld).toInt()
    val endRow = ceil(drawMaxY / spanWorld).toInt()
    val screenSpan = (spanWorld * zoom).roundToInt().coerceAtLeast(1)

    val clipTopLeft = toScreen(Offset(boundMinX, boundMinY))
    val clipBottomRight = toScreen(Offset(boundMaxX, boundMaxY))
    clipRect(
        left = minOf(clipTopLeft.x, clipBottomRight.x).coerceAtLeast(0f),
        top = minOf(clipTopLeft.y, clipBottomRight.y).coerceAtLeast(0f),
        right = maxOf(clipTopLeft.x, clipBottomRight.x).coerceAtMost(size.width),
        bottom = maxOf(clipTopLeft.y, clipBottomRight.y).coerceAtMost(size.height),
    ) {
        for (col in startCol..endCol) {
            for (row in startRow..endRow) {
                val topLeft = toScreen(Offset(col * spanWorld, row * spanWorld))
                drawImage(
                    image = image,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(screenSpan, screenSpan),
                )
            }
        }
        if (marginWorld > 0f) {
            drawMarginFade(mapWidthTiles * tilePx, mapHeightTiles * tilePx, marginWorld, toScreen)
        }
    }
}

/** [PAPER] alpha 0 right at the map's own edge, fading to fully opaque [PAPER] by the outer margin edge — see [drawBackgroundImage]'s own doc comment for why this is edges+corners, not a single 2D falloff. */
private fun DrawScope.drawMarginFade(mapWidthWorld: Float, mapHeightWorld: Float, marginWorld: Float, toScreen: (Offset) -> Offset) {
    val fadeColors = listOf(PAPER.copy(alpha = 0f), PAPER)

    // North / South edges — full map width, marginWorld tall, fading away from the map.
    val north = toScreen(Offset(0f, -marginWorld)) to toScreen(Offset(mapWidthWorld, 0f))
    drawRect(
        brush = Brush.verticalGradient(fadeColors, startY = north.second.y, endY = north.first.y),
        topLeft = north.first,
        size = Size(north.second.x - north.first.x, north.second.y - north.first.y),
    )
    val south = toScreen(Offset(0f, mapHeightWorld)) to toScreen(Offset(mapWidthWorld, mapHeightWorld + marginWorld))
    drawRect(
        brush = Brush.verticalGradient(fadeColors, startY = south.first.y, endY = south.second.y),
        topLeft = south.first,
        size = Size(south.second.x - south.first.x, south.second.y - south.first.y),
    )
    // West / East edges — full map height, marginWorld wide.
    val west = toScreen(Offset(-marginWorld, 0f)) to toScreen(Offset(0f, mapHeightWorld))
    drawRect(
        brush = Brush.horizontalGradient(fadeColors, startX = west.second.x, endX = west.first.x),
        topLeft = west.first,
        size = Size(west.second.x - west.first.x, west.second.y - west.first.y),
    )
    val east = toScreen(Offset(mapWidthWorld, 0f)) to toScreen(Offset(mapWidthWorld + marginWorld, mapHeightWorld))
    drawRect(
        brush = Brush.horizontalGradient(fadeColors, startX = east.first.x, endX = east.second.x),
        topLeft = east.first,
        size = Size(east.second.x - east.first.x, east.second.y - east.first.y),
    )
    // Four corners — radial, centered on the map's own corner point so the edge fades above meet
    // a matching diagonal fade instead of leaving a hard square notch.
    val corners = listOf(
        Offset(0f, 0f) to Offset(-marginWorld, -marginWorld),
        Offset(mapWidthWorld, 0f) to Offset(mapWidthWorld + marginWorld, -marginWorld),
        Offset(0f, mapHeightWorld) to Offset(-marginWorld, mapHeightWorld + marginWorld),
        Offset(mapWidthWorld, mapHeightWorld) to Offset(mapWidthWorld + marginWorld, mapHeightWorld + marginWorld),
    )
    for ((centerWorld, cornerWorld) in corners) {
        val center = toScreen(centerWorld)
        val corner = toScreen(cornerWorld)
        // Radius matches the edge bands' own fade distance (marginWorld), NOT the diagonal distance
        // to the corner point — using the diagonal made the radial gradient fade slower than the
        // adjacent linear bands, so along their shared edge the corner square was still visibly
        // un-faded where the edge band had already reached full opacity, reading as a seam. Beyond
        // this radius the gradient clamps to the final (fully opaque) color, so the true outer corner
        // point still ends up fully faded — it's just reached before the geometric corner.
        val radius = kotlin.math.abs(corner.x - center.x).coerceAtLeast(kotlin.math.abs(corner.y - center.y))
        if (radius <= 0f) continue
        drawRect(
            brush = Brush.radialGradient(fadeColors, center = center, radius = radius),
            topLeft = Offset(minOf(center.x, corner.x), minOf(center.y, corner.y)),
            size = Size(kotlin.math.abs(corner.x - center.x), kotlin.math.abs(corner.y - center.y)),
        )
    }
}
