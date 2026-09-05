package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode

/** Sanity bound for area effects (docs/04-resolver.md's guard constants table) — a candidate list this large is misconfigured content, not a case to silently truncate. */
const val MAX_TARGETS = 32

internal fun matchesFilter(entity: Entity, caster: EntityId, filter: TargetFilter): Boolean {
    if (filter.excludeSelf && entity.id == caster) return false
    if (filter.faction != null && entity.actor?.faction != filter.faction) return false
    val health = entity.health
    if (filter.requireAlive && (health == null || health.current <= 0)) return false
    if (filter.hasStatus != null && entity.statuses.none { it.def == filter.hasStatus }) return false
    return true
}

/** Feeds tile highlighting directly — no second code path for rendering. See docs/05-actions-and-effects.md. */
fun legalTargets(state: GameState, caster: EntityId, def: ActionDef, cat: Catalog): Set<GridPos> {
    val casterEntity = state.byId[caster] ?: return emptySet()
    val origin = casterEntity.pos ?: return emptySet()
    val targeting = def.targeting

    if (targeting.mode == TargetMode.SelfOnly) return setOf(origin)

    val maxRange = rangeInTiles(targeting.range)
    val inRange = tilesWithinRange(origin, maxRange, state.map)
    val visible = if (targeting.requiresLoS) inRange.filter { hasLineOfSight(origin, it, state.map) } else inRange

    return when (targeting.mode) {
        TargetMode.SingleEntity -> visible.filter { pos ->
            val occupant = state.occupancy[pos]?.let { state.byId[it] }
            occupant != null && matchesFilter(occupant, caster, targeting.filter)
        }.toSet()
        TargetMode.Point, TargetMode.Direction -> visible.toSet()
        // Real bug found by actually playing a Move action live, not by inspection: this used to
        // budget purely off the ActionDef's static `range` (an action-design ceiling, e.g. "Dash
        // reaches at most 8 tiles"), completely ignoring the caster's actual remaining AP — so the
        // board highlighted tiles the player could see but not afford, canPerform()/perform()
        // correctly rejected them on Confirm, and it silently looked like "the tile I tapped does
        // nothing." Capped by whichever is smaller so every highlighted tile is one Confirm will
        // actually honour — every other TargetMode already has this property, Path was the outlier.
        // A caster with no `resources` at all (a wall/hazard, not a real actor) has no AP economy to
        // cap by, so it falls back to the range-only budget rather than being clamped to zero.
        TargetMode.Path -> {
            val budget = casterEntity.resources?.let { minOf(maxRange, it.ap) } ?: maxRange
            reachableTiles(origin, budget, state.map, state.blockingOccupancy)
        }
        TargetMode.SelfOnly -> setOf(origin) // unreachable — handled above
    }
}

/** Drives the area preview when the player hovers a tile — the same shape math [legalTargets] used to filter candidates. */
fun affectedBy(state: GameState, def: ActionDef, caster: EntityId, at: GridPos): List<EntityId> {
    val casterEntity = state.byId[caster] ?: return emptyList()
    val origin = casterEntity.pos ?: return emptyList()
    val targeting = def.targeting

    val tiles = tilesInShape(origin, at, targeting.shape, state.map)
    val candidates = tiles.mapNotNull { pos -> state.occupancy[pos]?.let { state.byId[it] } }
        .filter { matchesFilter(it, caster, targeting.filter) }
        .filter { !targeting.requiresLoS || hasLineOfSight(origin, it.pos!!, state.map) }
        .sortedBy { it.id.raw } // deterministic — EachTarget expansion depends on this order

    check(candidates.size <= MAX_TARGETS) { "affectedBy matched ${candidates.size} candidates for ${def.id.raw}, exceeding MAX_TARGETS=$MAX_TARGETS — misconfigured content, not a case to truncate" }
    return candidates.take(targeting.maxTargets).map { it.id }
}
