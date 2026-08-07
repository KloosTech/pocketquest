package de.jackbeback.pocketquest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import de.jackbeback.pocketquest.core.model.GridPos

/** Tile-center in pixels — where a token's `Offset` sits for a given `GridPos`. */
fun GridPos.toOffset(tilePx: Float): Offset = Offset((col + 0.5f) * tilePx, (row + 0.5f) * tilePx)

/** Inverse of [toOffset] — which tile a tap landed on. */
fun Offset.toGridPos(tilePx: Float): GridPos =
    GridPos((x / tilePx).toInt(), (y / tilePx).toInt())

/**
 * doc15's pan+zoom viewport: [world] positions (unscaled tile-px, same space [VisualEntity.pos]
 * lives in) map to screen-px by centering [camera] in the viewport, then scaling by [zoom].
 * [screenToWorld] is the exact inverse — used to turn a tap's screen position back into a world
 * position (then [toGridPos]) regardless of how far panned/zoomed the board currently is.
 */
fun worldToScreen(world: Offset, camera: Offset, zoom: Float, viewport: Size): Offset =
    (world - camera) * zoom + Offset(viewport.width / 2f, viewport.height / 2f)

fun screenToWorld(screen: Offset, camera: Offset, zoom: Float, viewport: Size): Offset =
    (screen - Offset(viewport.width / 2f, viewport.height / 2f)) / zoom + camera
