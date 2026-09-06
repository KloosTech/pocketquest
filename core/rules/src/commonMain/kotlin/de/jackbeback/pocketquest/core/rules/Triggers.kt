package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.rules.action.instantiate

/**
 * docs/36-map-triggers.md: shared by both firing sites — `:ui`'s `exploreMoveTo` (pre-combat) and
 * `Handlers.kt`'s `moveAlong` (combat) — so "player-controlled, unfired, one shot" is defined once.
 * Marks the trigger fired BEFORE instantiating its effects, so a trigger effect that moves the
 * stepper back onto the same cell (e.g. a knockback bouncing off a wall) can't re-fire it. `Ref.Caster`
 * resolves to [entityId] (the stepper); `Ref.EachTarget` resolves to the whole living player party —
 * see the doc's `ActionCtx` reuse explanation for why no new `Ref` case was needed for "whole party"
 * vs "just the stepper."
 */
fun fireTriggerIfAny(state: GameState, entityId: EntityId, at: GridPos, cat: Catalog): Pair<GameState, List<Effect>>? {
    val stepper = state.byId[entityId] ?: return null
    if (stepper.actor?.controller != Controller.Human) return null
    val trigger = state.map.triggers.firstOrNull { it.at == at } ?: return null
    if (trigger.id in state.firedTriggers) return null

    val fired = state.copy(firedTriggers = state.firedTriggers + trigger.id)
    val party = fired.entities
        .filter { it.actor?.faction == Faction.Player && (it.health?.current ?: 0) > 0 }
        .map { it.id }
    val ctx = ActionCtx(caster = entityId, targets = party, point = at)
    val effects = trigger.effects.flatMap { it.instantiate(fired, ctx, cat) }
    return fired to (effects + newlySatisfiedGateOpens(fired))
}

/**
 * docs/48-gates-and-wander-ai.md's multi-trigger unlock amendment: any gate whose
 * [de.jackbeback.pocketquest.core.model.GatePlacement.requiredTriggers] is non-empty and now fully
 * covered by [state]'s [GameState.firedTriggers] gets a synthesized [Effect.OpenGate] — reusing the
 * effect's own handler to actually flip [GameState.openGates] rather than mutating it here directly,
 * so this stays a plain, ordinary effect from the resolver's point of view (animates, dedupes,
 * round-trips through save exactly like an authored `OpenGate` would). Only ever ADDS effects for
 * gates not already open — an already-open gate never re-fires.
 */
private fun newlySatisfiedGateOpens(state: GameState): List<Effect> =
    state.map.gates
        .filter { it.id !in state.openGates && it.requiredTriggers.isNotEmpty() && it.requiredTriggers.all { req -> req in state.firedTriggers } }
        .map { Effect.OpenGate(it.id) }
