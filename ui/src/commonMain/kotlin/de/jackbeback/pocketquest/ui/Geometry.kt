package de.jackbeback.pocketquest.ui

import androidx.compose.ui.geometry.Offset
import de.jackbeback.pocketquest.core.model.GridPos

/** Tile-center in pixels — where a token's `Offset` sits for a given `GridPos`. */
fun GridPos.toOffset(tilePx: Float): Offset = Offset((col + 0.5f) * tilePx, (row + 0.5f) * tilePx)

/** Inverse of [toOffset] — which tile a tap landed on. */
fun Offset.toGridPos(tilePx: Float): GridPos =
    GridPos((x / tilePx).toInt(), (y / tilePx).toInt())
