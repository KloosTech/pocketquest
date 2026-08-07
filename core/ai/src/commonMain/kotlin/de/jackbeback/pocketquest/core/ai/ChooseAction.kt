package de.jackbeback.pocketquest.core.ai

import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.action.canPerform
import de.jackbeback.pocketquest.core.rules.action.grantedActions
import de.jackbeback.pocketquest.core.rules.action.preview
import de.jackbeback.pocketquest.core.rules.action.tauntedBy
import de.jackbeback.pocketquest.core.rules.targeting.affectedBy
import de.jackbeback.pocketquest.core.rules.targeting.legalTargets

/** What the AI decided to do, and why — `score` is exposed for tests/debugging, not used by the caller. */
data class AiDecision(val actionId: ActionId, val ctx: ActionCtx, val score: Int)

/**
 * doc05: "[preview] hands the AI its evaluation function for free: enumerate
 * legal actions, run each in Expected mode, score the resulting event list."
 * This is exactly that — nothing more. Enumerates every action the entity's
 * archetype knows plus anything a level feature granted it (doc17-engine-gaps.md 1.6), every
 * legal target for each (via the same
 * `legalTargets`/`affectedBy` a UI's tile-highlighting would call), filters
 * through `canPerform` (the one true legality check, same as doc05's "one
 * function, three consumers"), scores each candidate with [preview] in
 * `Expected` mode, and returns the best. Returns null if nothing is legal —
 * the caller passes the turn.
 *
 * Deliberately out of scope: `TargetMode.Point`/`Direction`/`Path` (movement,
 * ground-targeted AoE) are not enumerated — nothing in the demo catalog uses
 * them, and picking a movement destination is a pathfinding/positioning
 * problem, not a same-shape extension of this one.
 */
fun chooseAction(state: GameState, entityId: EntityId, cat: Catalog): AiDecision? {
    val entity = state.byId[entityId] ?: return null
    val archetype = cat.archetype(entity.archetype)
    val faction = entity.actor?.faction

    var best: AiDecision? = null
    for (actionId in archetype.actions + entity.grantedActions(cat)) {
        val def = cat.actionDef(actionId)
        for (ctx in candidateContexts(state, entityId, def, cat)) {
            if (canPerform(state, entityId, def, ctx, cat).isNotEmpty()) continue
            val result = preview(state, entityId, actionId, ctx, cat)
            val score = score(result.events, faction, state)
            if (best == null || score > best.score) {
                best = AiDecision(actionId, ctx, score)
            }
        }
    }
    return best
}

private fun candidateContexts(state: GameState, caster: EntityId, def: ActionDef, cat: Catalog): List<ActionCtx> {
    val casterEntity = state.byId[caster] ?: return emptyList()
    val casterPos = casterEntity.pos ?: return emptyList()
    return when (def.targeting.mode) {
        TargetMode.SelfOnly -> listOf(ActionCtx(caster, targets = listOf(caster), point = casterPos))
        TargetMode.SingleEntity -> narrowedByTaunt(state, casterEntity, legalTargets(state, caster, def, cat), cat).map { point ->
            ActionCtx(caster, targets = affectedBy(state, def, caster, point), point = point)
        }
        TargetMode.Point, TargetMode.Direction, TargetMode.Path -> emptyList()
    }
}

/**
 * doc10/doc18: Taunt "lives in :core:ai's target selection" — restricts candidates to whichever
 * taunter(s) are among them, when any are. Scoped naturally to enemy-facing actions only: an
 * ally-heal's [legal] set is never going to contain the (enemy-of-the-caster) taunter's position to
 * begin with, so this only ever narrows something that was already enemy-targeting. If the
 * taunter(s) aren't legal targets for THIS action at all — out of range, blocked LoS, wrong weapon
 * — [legal] is returned unchanged rather than narrowing to nothing: taunt binds the choice among
 * reachable options, it doesn't invent a target the action mechanically can't reach.
 */
private fun narrowedByTaunt(state: GameState, caster: Entity, legal: Set<GridPos>, cat: Catalog): Set<GridPos> {
    val tauntedBy = caster.tauntedBy(cat)
    if (tauntedBy.isEmpty()) return legal
    val onlyTaunters = legal.filterTo(mutableSetOf()) { pos -> state.occupancy[pos] in tauntedBy }
    return onlyTaunters.ifEmpty { legal }
}

/**
 * A damage-maximizing heuristic, nothing more: enemy damage/deaths are good,
 * self/ally damage/deaths are bad, everything else (status application,
 * resource spend) is unscored — there is no way to tell a buff from a debuff
 * from the event alone without inspecting `StatusDef.modifiers`, and that is
 * more sophistication than a first pass needs.
 */
private fun score(events: List<GameEvent>, aiFaction: Faction?, state: GameState): Int {
    var total = 0
    for (event in events) {
        when (event) {
            is GameEvent.DamageTaken -> {
                val hostile = state.byId[event.target]?.actor?.faction != aiFaction
                total += if (hostile) event.amount else -event.amount
            }
            is GameEvent.Died -> {
                val hostile = state.byId[event.target]?.actor?.faction != aiFaction
                total += if (hostile) 1000 else -1000
            }
            else -> Unit
        }
    }
    return total
}
