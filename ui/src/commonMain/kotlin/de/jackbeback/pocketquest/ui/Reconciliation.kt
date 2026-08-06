package de.jackbeback.pocketquest.ui

import androidx.compose.ui.geometry.Offset
import de.jackbeback.pocketquest.core.model.GameState

/**
 * doc07's settle() — the safety net. Call after every drain (or a skip):
 * a forgotten or cancelled beat becomes a one-frame cosmetic glitch instead
 * of a token permanently in the wrong place.
 */
suspend fun VisualWorld.settle(logical: GameState, tilePx: Float) {
    logical.entities.forEach { e ->
        val v = entities.getOrPut(e.id) {
            VisualEntity(e.pos?.toOffset(tilePx) ?: Offset.Zero, (e.health?.current ?: 0).toFloat())
        }
        e.pos?.toOffset(tilePx)?.let { if (v.pos.value != it) v.pos.snapTo(it) }
        e.health?.let { if (v.hp.value != it.current.toFloat()) v.hp.snapTo(it.current.toFloat()) }
        v.alpha.snapTo(1f)
        v.scale.snapTo(1f)
    }
    entities.keys.retainAll(logical.byId.keys)
}
