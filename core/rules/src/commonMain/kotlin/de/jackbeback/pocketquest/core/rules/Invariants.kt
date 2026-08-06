package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * The 7 invariants from docs/02-state-model.md#invariants. Returns
 * violation messages — empty means valid. Used by tests and (in debug
 * builds) after every resolver step once the resolver exists.
 */
fun checkInvariants(state: GameState, cat: Catalog): List<String> {
    val violations = mutableListOf<String>()

    // 1. Every EntityId in turn.order exists in entities.
    for (id in state.turn.order) {
        if (id !in state.byId) violations += "turn.order references missing entity $id"
    }

    // 2. No two entities with a non-null pos share a GridPos.
    val seen = mutableMapOf<GridPos, EntityId>()
    for (e in state.entities) {
        val pos = e.pos ?: continue
        val existing = seen[pos]
        if (existing != null) violations += "entities ${existing.raw} and ${e.id.raw} both occupy $pos"
        else seen[pos] = e.id
    }

    // 3. Every pos is inside the map bounds and on a walkable tile.
    for (e in state.entities) {
        val pos = e.pos ?: continue
        if (!state.map.isWalkable(pos)) violations += "entity ${e.id.raw} at $pos is not on a walkable in-bounds tile"
    }

    // 4/5. health/resources within derived bounds.
    for (e in state.entities) {
        val s = e.stats(cat)
        e.health?.let { h ->
            if (h.current !in 0..s.maxHp) violations += "entity ${e.id.raw} health ${h.current} outside 0..${s.maxHp}"
        }
        e.resources?.let { r ->
            if (r.ap !in 0..s.maxAp) violations += "entity ${e.id.raw} ap ${r.ap} outside 0..${s.maxAp}"
            if (r.mana !in 0..s.maxMana) violations += "entity ${e.id.raw} mana ${r.mana} outside 0..${s.maxMana}"
        }
    }

    // 6. Every ActiveStatus.sourceId, if non-null, refers to an existing entity.
    for (e in state.entities) {
        for (status in e.statuses) {
            val source = status.sourceId ?: continue
            if (source !in state.byId) violations += "entity ${e.id.raw} has status ${status.def.raw} sourced from missing entity $source"
        }
    }

    // 7. activeIndex in order.indices whenever order is non-empty.
    if (state.turn.order.isNotEmpty() && state.turn.activeIndex !in state.turn.order.indices) {
        violations += "turn.activeIndex ${state.turn.activeIndex} outside order.indices (size ${state.turn.order.size})"
    }

    return violations
}
