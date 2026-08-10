package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * Repositions [entityId] directly to [to] — no AP cost, no resolver/effects pipeline, no combat
 * log entry. Exploration-mode movement only (before [de.jackbeback.pocketquest.core.rules.targeting.inCombat]): the moment combat
 * starts there's no more "exploration," and every subsequent move goes back through the normal
 * Movement action / [de.jackbeback.pocketquest.core.rules.action.perform] pipeline. Callers are
 * expected to have already validated [to] via [de.jackbeback.pocketquest.core.rules.targeting.findPath]
 * — this does not itself check walkability/occupancy/wall-edges.
 */
fun moveEntityTo(state: GameState, entityId: EntityId, to: GridPos): GameState {
    val entities = state.entities.map { if (it.id == entityId) it.copy(pos = to) else it }
    return state.copy(entities = entities)
}

/**
 * The hard reset agreed for the exploration → combat transition: nobody really "had a turn" during
 * free-roam exploration, so combat begins clean — every entity's AP/Quick/Reaction back to full
 * (same fields [Effect.EndTurn]'s own turn-start reset touches, same reason mana is left alone: it's
 * a per-encounter pool, not a per-turn budget), turn order restarting at index 0. Call this exactly
 * once, right as [de.jackbeback.pocketquest.core.rules.targeting.inCombat] flips true.
 */
fun beginCombat(state: GameState, catalog: Catalog): GameState {
    val refreshed = state.entities.map { entity ->
        val resources = entity.resources ?: return@map entity
        val stats = entity.stats(catalog)
        entity.copy(resources = resources.copy(ap = stats.maxAp, quickUsed = false, reactionUsed = false))
    }
    return state.copy(entities = refreshed, turn = state.turn.copy(round = 1, activeIndex = 0, phase = TurnPhase.Start))
}
