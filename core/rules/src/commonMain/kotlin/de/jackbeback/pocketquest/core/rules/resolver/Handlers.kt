package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Decision
import de.jackbeback.pocketquest.core.model.DecisionId
import de.jackbeback.pocketquest.core.model.DecisionRequest
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.RollMode
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.rules.abilityModifier
import de.jackbeback.pocketquest.core.rules.action.instantiate
import de.jackbeback.pocketquest.core.rules.d20
import de.jackbeback.pocketquest.core.rules.resolveAdvantage
import de.jackbeback.pocketquest.core.rules.roll
import de.jackbeback.pocketquest.core.rules.stat.stats
import kotlin.math.roundToInt

/** What applying one effect produced. `spawn` goes to the FRONT of the stack — see docs/04-resolver.md. */
data class HandlerOutcome(
    val state: GameState,
    val events: List<GameEvent> = emptyList(),
    val spawn: List<Effect> = emptyList(),
)

/**
 * [Effect.Ask] never reaches here — `run()` intercepts it before dispatch.
 * A plain `when` rather than a handler registry: Effect is sealed and
 * defined entirely in :core:model, so the compiler already enforces
 * exhaustiveness; a registry would just be indirection with nothing to
 * plug into it.
 */
internal fun applyEffect(state: GameState, effect: Effect, answers: Map<DecisionId, Decision>, cat: Catalog, mode: RngMode = RngMode.Live): HandlerOutcome =
    when (effect) {
        is Effect.Ask -> error("Ask must be intercepted by run() before reaching a handler")
        is Effect.DealDamage -> dealDamage(state, effect, cat)
        is Effect.MoveAlong -> moveAlong(state, effect)
        is Effect.SpendCost -> spendCost(state, effect)
        is Effect.ApplyStatus -> applyStatus(state, effect, cat)
        is Effect.RollAttack -> rollAttack(state, effect, cat, mode)
        is Effect.RollSave -> rollSave(state, effect, cat, mode)
        is Effect.OfferReaction -> offerReaction(state, effect, cat)
        is Effect.ResolveReaction -> resolveReaction(state, effect, answers, cat)
    }

/**
 * Live rolls from `state.rng`, advancing it and carrying the new state
 * forward. Expected substitutes a fixed value and never touches `state.rng`
 * — see [RngMode]. Returned as Double so both callers (hit/miss comparison,
 * damage totals) share one code path regardless of mode.
 */
private fun rollD20(state: GameState, mode: RngMode, advantage: RollMode): Pair<Double, GameState> = when (mode) {
    RngMode.Live -> {
        val (next, value) = state.rng.d20(advantage)
        value.toDouble() to state.copy(rng = next)
    }
    RngMode.Expected -> 10.5 to state
}

private fun rollDice(state: GameState, mode: RngMode, spec: DiceSpec): Pair<Double, GameState> = when (mode) {
    RngMode.Live -> {
        val (next, result) = state.rng.roll(spec)
        result.total.toDouble() to state.copy(rng = next)
    }
    RngMode.Expected -> {
        val avgPerDie = (spec.sides + 1) / 2.0
        (spec.count * avgPerDie + spec.modifier) to state
    }
}

private fun GameState.withEntity(id: EntityId, transform: (Entity) -> Entity): GameState =
    copy(entities = entities.map { if (it.id == id) transform(it) else it }, version = version + 1)

private fun fizzle(state: GameState, effect: Effect, reason: Rejection): HandlerOutcome =
    HandlerOutcome(state, events = listOf(GameEvent.Fizzled(effect::class.simpleName ?: "Effect", reason)))

private fun dealDamage(state: GameState, effect: Effect.DealDamage, cat: Catalog): HandlerOutcome {
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    val health = target.health ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    if (health.current <= 0) return fizzle(state, effect, Rejection.TargetMissing(effect.target))

    val resistance = target.stats(cat).resistances[effect.type] ?: Resistance.None
    val finalAmount = when (resistance) {
        Resistance.Immune -> 0
        Resistance.Resistant -> effect.amount / 2
        Resistance.Vulnerable -> effect.amount * 2
        Resistance.None -> effect.amount
    }
    val newCurrent = (health.current - finalAmount).coerceAtLeast(0)
    val newState = state.withEntity(target.id) { it.copy(health = it.health!!.copy(current = newCurrent)) }

    val events = buildList {
        add(GameEvent.DamageTaken(target.id, finalAmount, effect.type))
        if (newCurrent == 0) add(GameEvent.Died(target.id))
    }
    return HandlerOutcome(newState, events)
}

private fun moveAlong(state: GameState, effect: Effect.MoveAlong): HandlerOutcome {
    val who = state.byId[effect.who] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.who))
    val from = who.pos ?: return fizzle(state, effect, Rejection.TargetMissing(effect.who))
    if (effect.index !in effect.path.indices) return HandlerOutcome(state)

    val to = effect.path[effect.index]
    if (!state.map.isWalkable(to) || state.occupancy.containsKey(to)) {
        return fizzle(state, effect, Rejection.Blocked(to))
    }

    val newState = state.withEntity(who.id) { it.copy(pos = to) }
    val nextIndex = effect.index + 1
    val spawn = if (nextIndex < effect.path.size) listOf(effect.copy(index = nextIndex)) else emptyList()
    return HandlerOutcome(newState, listOf(GameEvent.MoveStepped(who.id, from, to)), spawn)
}

private fun spendCost(state: GameState, effect: Effect.SpendCost): HandlerOutcome {
    val who = state.byId[effect.who] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.who))
    val resources = who.resources ?: return fizzle(state, effect, Rejection.TargetMissing(effect.who))
    if (resources.ap < effect.ap) return fizzle(state, effect, Rejection.NotEnoughAp(effect.ap, resources.ap))
    if (resources.mana < effect.mana) return fizzle(state, effect, Rejection.NotEnoughMana(effect.mana, resources.mana))

    val newState = state.withEntity(who.id) { entity ->
        val current = entity.resources!!
        entity.copy(
            resources = current.copy(
                ap = current.ap - effect.ap,
                mana = current.mana - effect.mana,
                quickUsed = current.quickUsed || effect.markQuickUsed,
                reactionUsed = current.reactionUsed || effect.markReactionUsed,
            ),
        )
    }
    return HandlerOutcome(newState, listOf(GameEvent.ResourcesSpent(who.id, effect.ap, effect.mana)))
}

private fun applyStatus(state: GameState, effect: Effect.ApplyStatus, cat: Catalog): HandlerOutcome {
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    val def = cat.statusDef(effect.status)
    val existing = target.statuses.find { it.def == effect.status }
    val incoming = ActiveStatus(
        def = effect.status,
        sourceId = effect.sourceId,
        linkId = effect.linkId,
        stacks = effect.stacks,
        expiry = effect.expiry,
        appliedAtVersion = state.version,
    )

    fun replace(stacks: Int, expiry: Expiry): HandlerOutcome {
        val without = target.statuses.filterNot { it.def == effect.status }
        val applied = incoming.copy(stacks = stacks, expiry = expiry)
        val newState = state.withEntity(target.id) { it.copy(statuses = without + applied) }
        return HandlerOutcome(newState, listOf(GameEvent.StatusApplied(target.id, effect.status, stacks, expiry)))
    }

    return when (def.stackPolicy) {
        StackPolicy.Refresh -> replace(stacks = 1, expiry = effect.expiry)
        StackPolicy.AddStacks -> replace(stacks = (existing?.stacks ?: 0) + effect.stacks, expiry = effect.expiry)
        StackPolicy.KeepStrongest ->
            if (existing != null && existing.stacks >= effect.stacks) {
                HandlerOutcome(state) // incoming is weaker or equal — dropped silently, nothing changes
            } else {
                replace(stacks = effect.stacks, expiry = effect.expiry)
            }
        StackPolicy.Independent -> {
            val newState = state.withEntity(target.id) { it.copy(statuses = it.statuses + incoming) }
            HandlerOutcome(newState, listOf(GameEvent.StatusApplied(target.id, effect.status, effect.stacks, effect.expiry)))
        }
    }
}

private fun rollAttack(state: GameState, effect: Effect.RollAttack, cat: Catalog, mode: RngMode): HandlerOutcome {
    val attacker = state.byId[effect.attacker] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.attacker))
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    if ((target.health?.current ?: 0) <= 0) return fizzle(state, effect, Rejection.TargetMissing(effect.target))

    val ac = target.stats(cat).armorClass
    val advantageMode = resolveAdvantage(effect.advantage)
    val (rollValue, afterD20) = rollD20(state, mode, advantageMode)
    val total = rollValue + effect.attackBonus
    val hit = total >= ac

    val rolledEvent = GameEvent.AttackRolled(
        attacker = attacker.id,
        target = target.id,
        d20 = rollValue.roundToInt(),
        mod = effect.attackBonus,
        ac = ac,
        hit = hit,
    )
    if (!hit) return HandlerOutcome(afterD20, listOf(rolledEvent))

    val (dmgValue, afterDamageRoll) = rollDice(afterD20, mode, effect.damage)
    val spawn = listOf(Effect.DealDamage(effect.target, dmgValue.roundToInt(), effect.damageType, source = effect.attacker))
    return HandlerOutcome(afterDamageRoll, listOf(rolledEvent), spawn)
}

private fun rollSave(state: GameState, effect: Effect.RollSave, cat: Catalog, mode: RngMode): HandlerOutcome {
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))

    val score = target.stats(cat).abilities.forAbility(effect.ability)
    val mod = abilityModifier(score)
    val advantageMode = resolveAdvantage(effect.advantage)
    val (rollValue, afterD20) = rollD20(state, mode, advantageMode)
    val success = rollValue + mod >= effect.dc

    val rolledEvent = GameEvent.SaveRolled(
        target = target.id,
        ability = effect.ability,
        d20 = rollValue.roundToInt(),
        mod = mod,
        dc = effect.dc,
        success = success,
    )
    val spawn = if (success) effect.onSuccess else effect.onFail
    return HandlerOutcome(afterD20, listOf(rolledEvent), spawn)
}

private fun AbilityScores.forAbility(ability: Ability): Int =
    when (ability) {
        Ability.Str -> str
        Ability.Dex -> dex
        Ability.Con -> con
        Ability.Int -> int
        Ability.Wis -> wis
        Ability.Cha -> cha
    }

private fun offerReaction(state: GameState, effect: Effect.OfferReaction, cat: Catalog): HandlerOutcome {
    val reactor = state.byId[effect.who] ?: return HandlerOutcome(state)
    return when (answererFor(reactor)) {
        Answerer.HumanUi -> {
            val decisionId = DecisionId(state.nextDecisionId)
            val nextState = state.copy(nextDecisionId = state.nextDecisionId + 1)
            val request = DecisionRequest(decisionId)
            HandlerOutcome(
                nextState,
                spawn = listOf(Effect.Ask(request), Effect.ResolveReaction(decisionId, effect.trigger, effect.who, effect.actionId)),
            )
        }
        // Placeholder policy pending real :core:ai (doc01: AI is "a consumer of the resolver, not
        // a special case inside it") — AI always accepts an available reaction. Proves the resolver
        // mechanism (inline resolution, never AwaitingInput for AI) without inventing AI judgement.
        is Answerer.Ai -> acceptReaction(state, effect.who, effect.actionId, effect.trigger, cat)
    }
}

private fun resolveReaction(state: GameState, effect: Effect.ResolveReaction, answers: Map<DecisionId, Decision>, cat: Catalog): HandlerOutcome {
    val accept = answers[effect.decisionId]?.accept ?: false
    return if (accept) acceptReaction(state, effect.who, effect.actionId, effect.trigger, cat) else HandlerOutcome(state)
}

private fun acceptReaction(state: GameState, who: EntityId, actionId: ActionId, trigger: GameEvent, cat: Catalog): HandlerOutcome {
    val def = cat.actionDef(actionId)
    val ctx = ActionCtx(who, targetsFor(trigger))
    val spend = Effect.SpendCost(who, mana = def.cost.mana, markReactionUsed = true)
    val instantiated = def.effects.flatMap { it.instantiate(ctx, cat) }
    return HandlerOutcome(state, spawn = listOf(spend) + instantiated)
}
