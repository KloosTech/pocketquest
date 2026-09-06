package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.action.allActions
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * docs/15-battle-ui.md's threat overlay: "every tile an enemy could reach and attack next turn."
 * Computable from pathfinding ([reachableTiles], using next turn's full [de.jackbeback.pocketquest.core.model.Stats.speedTiles]
 * rather than any AP already spent this turn — the overlay is about future danger, not current
 * capability) plus [legalTargets] from each reachable tile, simulated by moving a COPY of [threat]
 * rather than mutating real state.
 *
 * Scoped to [TargetMode.SingleEntity] actions only — matches the only mode any current content
 * authors, and mirrors `:core:ai`'s own `candidateContexts()` excluding Point/Direction/Path for
 * the same reason (a movement/ground-targeted-AoE destination is a positioning problem, not a
 * same-shape extension). A future AoE-authoring pass would need this to also union each action's
 * blast shape around every candidate aim point, not just the aim point itself — deliberately not
 * attempted here since nothing in the demo catalog could exercise it correctly.
 *
 * Reaction-cost actions are excluded — this is about the threat's OWN upcoming turn, not an
 * opportunity attack the threat might get during the PLAYER's turn. Resource affordability (mana,
 * which persists per-encounter) is deliberately NOT checked — doc15 calls this feature "cheap" and
 * doesn't ask for it; this shows the geometric footprint, not a guaranteed-castable one.
 */
fun threatenedTiles(state: GameState, threat: EntityId, cat: Catalog): Set<GridPos> {
    val entity = state.byId[threat] ?: return emptySet()
    val origin = entity.pos ?: return emptySet()
    val faction = entity.actor?.faction

    val offensiveActions = entity.allActions(cat)
        .map { cat.actionDef(it) }
        .filter { def -> isOffensiveSingleTarget(def, faction) }
    if (offensiveActions.isEmpty()) return emptySet()

    val speed = entity.stats(cat).speedTiles
    val reachable = reachableTiles(origin, speed, state.map, state.blockingOccupancy, state.openGates) + origin

    val threatened = mutableSetOf<GridPos>()
    for (standingAt in reachable) {
        val hypothetical = state.copy(entities = state.entities.map { if (it.id == threat) it.copy(pos = standingAt) else it })
        for (def in offensiveActions) {
            threatened += legalTargets(hypothetical, threat, def, cat)
        }
    }
    return threatened
}

private fun isOffensiveSingleTarget(def: ActionDef, casterFaction: Faction?): Boolean {
    if (def.targeting.mode != TargetMode.SingleEntity) return false
    if (def.cost.action == ActionCost.Reaction) return false
    val targetFaction = def.targeting.filter.faction ?: return false
    return targetFaction != casterFaction
}

/** Unions [threatenedTiles] across every entity of [threatFaction] — doc15's overlay is one combined danger map, not per-enemy. */
fun allThreatenedTiles(state: GameState, threatFaction: Faction, cat: Catalog): Set<GridPos> =
    state.entities
        .filter { it.actor?.faction == threatFaction && (it.health?.current ?: 1) > 0 }
        .flatMap { threatenedTiles(state, it.id, cat) }
        .toSet()
