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
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.RollMode
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.rules.TurnMoment
import de.jackbeback.pocketquest.core.rules.abilityModifier
import de.jackbeback.pocketquest.core.rules.action.instantiate
import de.jackbeback.pocketquest.core.rules.d20
import de.jackbeback.pocketquest.core.rules.matches
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
        is Effect.EndTurn -> endTurn(state, effect, cat)
        is Effect.StartConcentration -> startConcentration(state, effect)
        is Effect.ConcentrationCheck -> concentrationCheck(state, effect, cat, mode)
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
    var newState = state.withEntity(target.id) { it.copy(health = it.health!!.copy(current = newCurrent)) }

    val events = mutableListOf<GameEvent>()
    events += GameEvent.DamageTaken(target.id, finalAmount, effect.type)
    if (newCurrent == 0) events += GameEvent.Died(target.id)

    // Damage triggers a CON save (or breaks unconditionally on death) for whichever entity is
    // concentrating, if any is — see docs/03-modifiers-and-status.md.
    val linkId = target.concentrating
    val spawn = if (linkId != null && newCurrent == 0) {
        newState = breakConcentration(newState, linkId, events)
        emptyList()
    } else if (linkId != null && finalAmount > 0) {
        listOf(Effect.ConcentrationCheck(target.id, dc = maxOf(10, finalAmount / 2)))
    } else {
        emptyList()
    }
    return HandlerOutcome(newState, events, spawn)
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
    val triggeredEvent = GameEvent.ReactionTriggered(effect.who, effect.actionId)
    val outcome = when (answererFor(reactor)) {
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
    return outcome.copy(events = listOf(triggeredEvent) + outcome.events)
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

/**
 * Removes every ActiveStatus sharing [linkId] from every entity (mutating
 * directly rather than returning RemoveStatus effects to push, since that
 * primitive doesn't exist yet — a deviation from doc03's shown
 * `breakConcentration(...): List<Effect>` signature) and clears whichever
 * entity was concentrating under it. Appends the resulting events to
 * [events] in place so callers (dealDamage, concentrationCheck) can keep
 * building one combined event list.
 */
private fun breakConcentration(state: GameState, linkId: LinkId, events: MutableList<GameEvent>): GameState {
    var working = state
    for (entity in state.entities) {
        val toRemove = entity.statuses.filter { it.linkId == linkId }
        if (toRemove.isEmpty()) continue
        working = working.withEntity(entity.id) { it.copy(statuses = it.statuses.filterNot { s -> s.linkId == linkId }) }
        for (status in toRemove) events += GameEvent.StatusExpired(entity.id, status.def)
    }
    val concentrator = state.entities.find { it.concentrating == linkId }
    if (concentrator != null) {
        working = working.withEntity(concentrator.id) { it.copy(concentrating = null) }
        events += GameEvent.ConcentrationBroken(concentrator.id, linkId)
    }
    return working
}

private fun startConcentration(state: GameState, effect: Effect.StartConcentration): HandlerOutcome {
    val caster = state.byId[effect.caster] ?: return HandlerOutcome(state)
    val events = mutableListOf<GameEvent>()
    var working = state
    caster.concentrating?.let { previous -> working = breakConcentration(working, previous, events) }
    working = working.withEntity(effect.caster) { it.copy(concentrating = effect.linkId) }
    events += GameEvent.ConcentrationStarted(effect.caster, effect.linkId)
    return HandlerOutcome(working, events)
}

private fun concentrationCheck(state: GameState, effect: Effect.ConcentrationCheck, cat: Catalog, mode: RngMode): HandlerOutcome {
    val who = state.byId[effect.who] ?: return HandlerOutcome(state)
    val linkId = who.concentrating ?: return HandlerOutcome(state) // already broken by something else this step

    val conScore = who.stats(cat).abilities.con
    val mod = abilityModifier(conScore)
    val (rollValue, afterRoll) = rollD20(state, mode, RollMode.Normal)
    val success = rollValue + mod >= effect.dc

    val events = mutableListOf<GameEvent>(GameEvent.ConcentrationCheckRolled(who.id, effect.dc, rollValue.roundToInt(), mod, success))
    var working = afterRoll
    if (!success) working = breakConcentration(working, linkId, events)
    return HandlerOutcome(working, events)
}

/** All statuses (across every entity) whose expiry matches [moment], removed with a StatusExpired event each. */
private fun expireStatuses(state: GameState, moment: TurnMoment, events: MutableList<GameEvent>): GameState {
    var working = state
    for (entity in state.entities) {
        val expired = entity.statuses.filter { it.expiry.matches(moment) }
        if (expired.isEmpty()) continue
        working = working.withEntity(entity.id) { it.copy(statuses = it.statuses.filterNot { s -> s.expiry.matches(moment) }) }
        for (status in expired) events += GameEvent.StatusExpired(entity.id, status.def)
    }
    return working
}

/**
 * Doc04's 7-step turn boundary, done atomically (steps 6-7-1-2-3-4; step 5
 * "Main phase: commands accepted" is just a phase flag, not an effect).
 * Step 4's "tick start-of-turn statuses" runs each status's
 * StatusDef.onTurnStart template list, with the status's original caster
 * (or the ticking entity itself, if unsourced) as Ref.Caster.
 */
private fun endTurn(state: GameState, effect: Effect.EndTurn, cat: Catalog): HandlerOutcome {
    val endingId = effect.who
    if (state.byId[endingId] == null) return HandlerOutcome(state)

    val events = mutableListOf<GameEvent>()
    var working = state

    // step 6
    working = expireStatuses(working, TurnMoment.EndOfTurn(endingId, working.turn.round), events)
    events += GameEvent.TurnEnded(endingId)

    // step 7
    val order = working.turn.order
    val nextIndex = (working.turn.activeIndex + 1) % order.size
    var round = working.turn.round
    if (nextIndex == 0) {
        working = expireStatuses(working, TurnMoment.EndOfRound(round), events)
        round += 1
    }
    val nextActiveId = order[nextIndex]
    working = working.copy(turn = working.turn.copy(round = round, activeIndex = nextIndex, phase = TurnPhase.Start))
    events += GameEvent.TurnStarted(nextActiveId, round)

    // steps 1-2 (stats are always derived, nothing to "recompute" as a write) -3
    working = expireStatuses(working, TurnMoment.StartOfTurn(nextActiveId, round), events)

    val nextActive = working.byId.getValue(nextActiveId)
    val stats = nextActive.stats(cat)
    working = working.withEntity(nextActiveId) { entity ->
        val resources = entity.resources
        if (resources == null) entity else entity.copy(resources = resources.copy(ap = stats.maxAp, mana = stats.maxMana, quickUsed = false, reactionUsed = false))
    }
    if (nextActive.resources != null) events += GameEvent.ResourcesReset(nextActiveId, stats.maxAp, stats.maxMana)

    // step 4
    val tickEffects = nextActive.statuses.flatMap { status ->
        val def = cat.statusDef(status.def)
        if (def.onTurnStart.isEmpty()) {
            emptyList()
        } else {
            val ctx = ActionCtx(caster = status.sourceId ?: nextActiveId, targets = listOf(nextActiveId))
            def.onTurnStart.flatMap { it.instantiate(ctx, cat) }
        }
    }

    working = working.copy(turn = working.turn.copy(phase = TurnPhase.Main)) // step 5

    return HandlerOutcome(working, events, tickEffects)
}
