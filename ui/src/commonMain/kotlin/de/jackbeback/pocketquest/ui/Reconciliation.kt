package de.jackbeback.pocketquest.ui

import de.jackbeback.pocketquest.core.model.GameState

/**
 * doc07's settle() — the safety net. Call after every drain (or a skip):
 * a forgotten or cancelled beat becomes a one-frame cosmetic glitch instead
 * of a token permanently in the wrong place.
 *
 * Forces alpha from `health.current` rather than leaving it alone: a beat
 * fading a dead entity might have been skipped or cancelled mid-flight, and
 * doc07's whole point is that settle() restores the correct final state
 * regardless of what beats did or didn't finish — snapping every entity's
 * alpha back to 1 unconditionally would resurrect a corpse the very next
 * time this runs (found in KNOWN_ISSUES.md #2, never exercised before
 * since the demo scenario never produced a Died event). Rests at
 * [DOWNED_ALPHA], not 0 — doc15/doc10: "Downed, not dead," a 0-HP entity is
 * still on the board, revivable, not gone; nothing removes an entity from
 * the board at all yet (doc17 3.1's DestroyEntity is unimplemented), so
 * fading fully invisible would misrepresent state that's still fully live.
 *
 * A `pos == null` entity (doc02: reserve, dead — "not on the map") gets no
 * VisualEntity at all — see [VisualWorld]'s init block for the equivalent
 * fix on first construction.
 */
suspend fun VisualWorld.settle(logical: GameState) {
    logical.entities.forEach { e ->
        val pos = e.pos
        if (pos == null) {
            entities.remove(e.id)
            return@forEach
        }

        val v = entities.getOrPut(e.id) { VisualEntity(pos.toOffset(tilePx), (e.health?.current ?: 0).toFloat()) }
        val target = pos.toOffset(tilePx)
        if (v.pos.value != target) v.pos.snapTo(target)
        e.health?.let { if (v.hp.value != it.current.toFloat()) v.hp.snapTo(it.current.toFloat()) }

        val alive = (e.health?.current ?: 1) > 0
        v.alpha.snapTo(if (alive) 1f else DOWNED_ALPHA)
        v.scale.snapTo(1f)
    }
    entities.keys.retainAll(logical.entities.filter { it.pos != null }.map { it.id }.toSet())
}
