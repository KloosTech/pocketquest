package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.DamageStep
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
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.HealStep
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.RollContext
import de.jackbeback.pocketquest.core.model.RollMode
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.rules.TurnMoment
import de.jackbeback.pocketquest.core.rules.abilityModifier
import de.jackbeback.pocketquest.core.rules.action.instantiate
import de.jackbeback.pocketquest.core.rules.d20
import de.jackbeback.pocketquest.core.rules.matches
import de.jackbeback.pocketquest.core.rules.resolveAdvantage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        is Effect.Heal -> heal(state, effect, cat)
        is Effect.RemoveStatus -> removeStatus(state, effect)
        is Effect.Composite -> HandlerOutcome(state, spawn = effect.effects)
        is Effect.RefillMana -> refillMana(state, effect, cat)
        is Effect.Push -> push(state, effect)
        is Effect.Teleport -> teleport(state, effect)
    }

/**
 * Live rolls from `state.rng`, advancing it and carrying the new state
 * forward. Expected substitutes a fixed value and never touches `state.rng`
 * — see [RngMode]. Returned as Double so both callers (hit/miss comparison,
 * damage totals) share one code path regardless of mode.
 */
/**
 * Returns an `Int`, not the `Double` it used to — Expected mode's raw expectation (10.5 for
 * Normal) was rounded separately for the recorded `d20` event field but compared unrounded for
 * the hit/success decision, so the two could disagree (KNOWN_ISSUES.md #10). Rounding once, here,
 * and using that single value for both eliminates the whole class of bug. Also fixes Expected
 * mode ignoring `advantage` entirely: it used to always return 10.5 regardless, understating the
 * true expected value of an advantaged roll (which is measurably higher) in every AI/preview
 * evaluation.
 */
private fun rollD20(state: GameState, mode: RngMode, advantage: RollMode): Pair<Int, GameState> = when (mode) {
    RngMode.Live -> {
        val (next, value) = state.rng.d20(advantage)
        value to state.copy(rng = next)
    }
    RngMode.Expected -> expectedD20(advantage) to state
}

/**
 * True expected value of a d20 under the given roll mode, rounded to the nearest integer (ties
 * toward positive infinity, matching Kotlin's `roundToInt`): Normal E[X]=10.5 -> 11. Advantage
 * E[max(X,Y)] for X,Y ~ Uniform{1..20} = (2*sum(k^2) - sum(k))/400 = 13.825 -> 14. Disadvantage
 * E[min(X,Y)] = 21 - E[max] = 7.175 -> 7.
 */
private fun expectedD20(advantage: RollMode): Int = when (advantage) {
    RollMode.Normal -> 11
    RollMode.Advantage -> 14
    RollMode.Disadvantage -> 7
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

/**
 * The effect's own polymorphic discriminator (its `@SerialName`, e.g. `"dealDamage"`), not
 * `effect::class.simpleName` — R8/ProGuard renames Kotlin classes in release builds, so the
 * simpleName would turn into an obfuscated identifier there while every debug run and test stays
 * readable, hiding the break until production (KNOWN_ISSUES.md #9). The `@SerialName` is a
 * compile-time string baked into the generated serializer, immune to minification by design —
 * exactly why every Effect subtype already carries one.
 */
private fun effectLabel(effect: Effect): String =
    Json.encodeToJsonElement(Effect.serializer(), effect).jsonObject["type"]?.jsonPrimitive?.content ?: "Effect"

private fun fizzle(state: GameState, effect: Effect, reason: Rejection): HandlerOutcome =
    HandlerOutcome(state, events = listOf(GameEvent.Fizzled(effectLabel(effect), reason)))

/**
 * docs/18-damage-pipeline.md's HealInstance — "the same shape, but much shorter": Prevent (a
 * matching HealStep.Prevent cancels the heal entirely — wounds that cannot be healed), Scale
 * (all matching HealStep.Scale multiply the amount, same "apply every match" pattern DealDamage's
 * Scale/Reduce use), Apply. No retarget/absorb/reflect — doc18 explicitly rules out redirecting
 * healing as a mechanic nobody asks for.
 */
private fun heal(state: GameState, effect: Effect.Heal, cat: Catalog): HandlerOutcome {
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    val health = target.health ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))

    val steps = collectHealSteps(target, cat)
    val prevented = steps.any { (it.step as? HealStep.Prevent)?.let { p -> matchesStepCondition(p.condition, state, null, null, emptySet()) } == true }
    if (prevented) return HandlerOutcome(state, listOf(GameEvent.Fizzled(effectLabel(effect), Rejection.Prevented)))

    var amountF = effect.amount.toFloat()
    steps.forEach { (it.step as? HealStep.Scale)?.let { s -> amountF *= s.factor } }

    val maxHp = target.stats(cat).maxHp
    val newCurrent = (health.current + amountF.roundToInt()).coerceAtMost(maxHp)
    val newState = state.withEntity(target.id) { it.copy(health = it.health!!.copy(current = newCurrent)) }
    val actualHealed = newCurrent - health.current
    val events = mutableListOf<GameEvent>(GameEvent.Healed(target.id, actualHealed, effect.source))
    if (health.current == 0 && newCurrent > 0) events += GameEvent.Revived(target.id)
    return HandlerOutcome(newState, events)
}

private fun refillMana(state: GameState, effect: Effect.RefillMana, cat: Catalog): HandlerOutcome {
    val entity = state.byId[effect.who] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.who))
    val resources = entity.resources ?: return HandlerOutcome(state)
    val maxMana = entity.stats(cat).maxMana
    if (resources.mana == maxMana) return HandlerOutcome(state)
    val newState = state.withEntity(effect.who) { it.copy(resources = it.resources!!.copy(mana = maxMana)) }
    return HandlerOutcome(newState, listOf(GameEvent.ManaRefilled(effect.who, maxMana)))
}

private fun removeStatus(state: GameState, effect: Effect.RemoveStatus): HandlerOutcome {
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    if (target.statuses.none { it.def == effect.status }) return HandlerOutcome(state) // already absent — not a precondition failure

    val newState = state.withEntity(target.id) { it.copy(statuses = it.statuses.filterNot { s -> s.def == effect.status }) }
    return HandlerOutcome(newState, listOf(GameEvent.StatusExpired(target.id, effect.status)))
}

/**
 * docs/18-damage-pipeline.md's 8-step pipeline, entirely synchronous within this one handler
 * call — [hops] is a local variable, nothing about an in-progress retarget chain is persisted,
 * since the whole chain (including any Split's spawned follow-up, which is a wholly separate
 * DealDamage the resolver processes on its own later) resolves before this function returns.
 *
 * Ordering is the whole design (doc18's own words): retarget first so everything downstream reads
 * the FINAL target's own defences, not the original's; scale before reduce so flat reduction
 * doesn't itself get multiplied by resistance; absorb last before apply so a shield soaks the
 * already-reduced number.
 */
private fun dealDamage(state: GameState, effect: Effect.DealDamage, cat: Catalog): HandlerOutcome {
    val originalTarget = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    val originalHealth = originalTarget.health ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    if (originalHealth.current <= 0) return fizzle(state, effect, Rejection.TargetMissing(effect.target))

    val events = mutableListOf<GameEvent>()
    val spawn = mutableListOf<Effect>()
    var type = effect.damageType
    var amount = effect.amount
    val hops = mutableListOf(effect.target)
    var currentTargetId = effect.target

    // Step 1: Retarget / Split, up to 4 hops (doc18's loop-protection cap) — a Retarget onto an
    // entity already in `hops` is skipped rather than looping forever between two mutual wards.
    while (hops.size <= 4) {
        val currentEntity = state.byId[currentTargetId] ?: break
        val steps = collectDamageSteps(currentEntity, cat)
        val redirect = steps.firstNotNullOfOrNull { sourced ->
            when (val step = sourced.step) {
                is DamageStep.Retarget -> {
                    val refId = resolveStepRef(step.to, sourced.statusSourceId, effect.source)
                    if (refId != null && refId !in hops && matchesStepCondition(step.condition, state, refId, currentEntity.pos, effect.tags)) {
                        Triple(refId, null as Float?, sourced.statusDefId)
                    } else {
                        null
                    }
                }
                is DamageStep.Split -> {
                    val refId = resolveStepRef(step.to, sourced.statusSourceId, effect.source)
                    if (refId != null && matchesStepCondition(step.condition, state, refId, currentEntity.pos, effect.tags)) {
                        Triple(refId, step.fraction as Float?, sourced.statusDefId)
                    } else {
                        null
                    }
                }
                else -> null
            }
        } ?: break

        val (refId, splitFraction, statusDefId) = redirect
        events += GameEvent.DamageRedirected(currentTargetId, refId, statusDefId)
        if (splitFraction != null) {
            // Split peels off a portion as an independent DealDamage (its own full pipeline, own
            // hops) and leaves the rest on the current target un-retargeted — "share the pain,"
            // not "hand it off entirely."
            val splitAmount = (amount * splitFraction).roundToInt()
            if (splitAmount > 0) {
                spawn += Effect.DealDamage(refId, splitAmount, type, source = effect.source, tags = effect.tags)
                amount -= splitAmount
            }
            break
        } else {
            currentTargetId = refId
            hops += refId
        }
    }
    if (hops.size > 4) return HandlerOutcome(state, events + GameEvent.Fizzled(effectLabel(effect), Rejection.Prevented), spawn)

    val finalTarget = state.byId[currentTargetId] ?: return HandlerOutcome(state, events, spawn)
    val finalHealth = finalTarget.health ?: return HandlerOutcome(state, events, spawn)
    if (finalHealth.current <= 0) return HandlerOutcome(state, events, spawn)
    val finalSteps = collectDamageSteps(finalTarget, cat)

    // Step 2: Prevent.
    val prevented = finalSteps.any { (it.step as? DamageStep.Prevent)?.let { p -> matchesStepCondition(p.condition, state, null, null, effect.tags) } == true }
    if (prevented) return HandlerOutcome(state, events + GameEvent.Fizzled(effectLabel(effect), Rejection.Prevented), spawn)

    // Step 3: Convert — first match wins, mirrors how step 1 resolves "the" Retarget.
    finalSteps.firstNotNullOfOrNull { sourced ->
        val step = sourced.step as? DamageStep.Convert ?: return@firstNotNullOfOrNull null
        if (step.from == null || step.from == type) step.to else null
    }?.let { type = it }

    // Step 4: Scale. Resistance (Modifier.Resist -> Stats.resistances) is the baked-in scale doc18
    // names explicitly; applied first via the exact old integer-division truncation so existing
    // behavior/tests (which predate this pipeline) don't shift, then any content-authored Scale
    // steps multiply on top as float factors.
    val resistance = finalTarget.stats(cat).resistances[type] ?: Resistance.None
    var scaled = when (resistance) {
        Resistance.Immune -> 0
        Resistance.Resistant -> amount / 2
        Resistance.Vulnerable -> amount * 2
        Resistance.None -> amount
    }
    var scaledF = scaled.toFloat()
    finalSteps.forEach { sourced ->
        val step = sourced.step as? DamageStep.Scale ?: return@forEach
        if (step.onlyType == null || step.onlyType == type) scaledF *= step.factor
    }
    scaled = scaledF.roundToInt()

    // Step 5: Reduce — every matching step sums, like Modifier.Add.
    var totalReduce = 0
    finalSteps.forEach { sourced ->
        val step = sourced.step as? DamageStep.Reduce ?: return@forEach
        if (step.onlyType == null || step.onlyType == type) totalReduce += step.flat
    }
    var remaining = (scaled - totalReduce).coerceAtLeast(0)

    // Step 6: Absorb — Health.temp, doc18's only pool today.
    var temp = finalHealth.temp
    finalSteps.forEach { sourced ->
        val step = sourced.step as? DamageStep.Absorb ?: return@forEach
        if (remaining <= 0) return@forEach
        val (newTemp, newRemaining) = step.pool.consume(temp, remaining)
        temp = newTemp
        remaining = newRemaining
    }

    // Step 7: Apply.
    val newCurrent = (finalHealth.current - remaining).coerceAtLeast(0)
    var newState = state.withEntity(finalTarget.id) { it.copy(health = it.health!!.copy(current = newCurrent, temp = temp)) }
    events += GameEvent.DamageTaken(finalTarget.id, remaining, type)
    if (newCurrent == 0) {
        events += GameEvent.Died(finalTarget.id)
        events += GameEvent.Downed(finalTarget.id)
    }

    // Damage triggers a CON save (or breaks unconditionally on death) for whichever entity is
    // concentrating, if any is — see docs/03-modifiers-and-status.md.
    val linkId = finalTarget.concentrating
    if (linkId != null && newCurrent == 0) {
        newState = breakConcentration(newState, linkId, events)
    } else if (linkId != null && remaining > 0) {
        spawn += Effect.ConcentrationCheck(finalTarget.id, dc = maxOf(10, remaining / 2))
    }

    // Step 8: After — Reflect. Never for a DealDamage that is itself the product of a Reflect
    // (Effect.fromReflect), or a thorns-on-thorns pair would loop forever the same way an
    // unguarded mutual Retarget would.
    if (!effect.fromReflect) {
        val reflectSource = effect.source
        if (reflectSource != null) {
            finalSteps.forEach { sourced ->
                val step = sourced.step as? DamageStep.Reflect ?: return@forEach
                val reflectAmount = (remaining * step.fraction).roundToInt()
                if (reflectAmount > 0) {
                    spawn += Effect.DealDamage(reflectSource, reflectAmount, step.type ?: type, source = finalTarget.id, tags = effect.tags, fromReflect = true)
                }
            }
        }
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

/**
 * doc17-engine-gaps.md 3.1: composes with [Effect.MoveAlong] rather than reimplementing its
 * blocked-tile fizzle-without-continuation behavior — see [Effect.Push]'s own doc comment. This
 * handler's only job is building the [distance]-tile path in [direction]; MoveAlong (dispatched
 * separately, across however many resolver steps it takes) does the actual moving, stopping at
 * whatever tile it first can't enter.
 */
private fun push(state: GameState, effect: Effect.Push): HandlerOutcome {
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    val from = target.pos ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))
    if (effect.distance <= 0) return HandlerOutcome(state)

    val stepCol = effect.direction.col.coerceIn(-1, 1)
    val stepRow = effect.direction.row.coerceIn(-1, 1)
    val path = (1..effect.distance).map { step -> GridPos(from.col + stepCol * step, from.row + stepRow * step) }
    return HandlerOutcome(state, spawn = listOf(Effect.MoveAlong(effect.target, path)))
}

/**
 * doc17-engine-gaps.md 3.1: instant, single-step — unlike [moveAlong] there is no path and no
 * self-continuation, and re-validation covers exactly what a manual move would (walkable,
 * unoccupied) via the same [Rejection.Blocked] a blocked [Effect.MoveAlong] step uses.
 */
private fun teleport(state: GameState, effect: Effect.Teleport): HandlerOutcome {
    val who = state.byId[effect.who] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.who))
    val from = who.pos ?: return fizzle(state, effect, Rejection.TargetMissing(effect.who))
    if (!state.map.isWalkable(effect.to) || state.occupancy.containsKey(effect.to)) {
        return fizzle(state, effect, Rejection.Blocked(effect.to))
    }

    val newState = state.withEntity(who.id) { it.copy(pos = effect.to) }
    return HandlerOutcome(newState, listOf(GameEvent.Teleported(who.id, from, effect.to)))
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
    // Advantage/disadvantage granted via Modifier.Roll (KNOWN_ISSUES.md #11) is the attacker's
    // own — self-referential, never the target's — merged with whatever the action itself
    // explicitly specifies, then resolved through the same cancellation rule as always.
    val derivedAdvantage = attacker.stats(cat).rollGrants
        .filter { it.ctx.matches(RollContext.AttackRoll(vs = target.actor?.faction)) }
        .map { it.side }
        .toSet()
    val advantageMode = resolveAdvantage(effect.advantage + derivedAdvantage)
    val (rollValue, afterD20) = rollD20(state, mode, advantageMode)
    val total = rollValue + effect.attackBonus
    val hit = total >= ac

    val rolledEvent = GameEvent.AttackRolled(
        attacker = attacker.id,
        target = target.id,
        d20 = rollValue,
        mod = effect.attackBonus,
        ac = ac,
        hit = hit,
    )
    if (!hit) return HandlerOutcome(afterD20, listOf(rolledEvent))

    val (dmgValue, afterDamageRoll) = rollDice(afterD20, mode, effect.damage)
    val spawn = listOf(Effect.DealDamage(effect.target, dmgValue.roundToInt(), effect.damageType, source = effect.attacker, tags = effect.tags))
    return HandlerOutcome(afterDamageRoll, listOf(rolledEvent), spawn)
}

private fun rollSave(state: GameState, effect: Effect.RollSave, cat: Catalog, mode: RngMode): HandlerOutcome {
    val target = state.byId[effect.target] ?: return fizzle(state, effect, Rejection.TargetMissing(effect.target))

    val targetStats = target.stats(cat)
    val score = targetStats.abilities.forAbility(effect.ability)
    val mod = abilityModifier(score)
    val derivedAdvantage = targetStats.rollGrants
        .filter { it.ctx.matches(RollContext.SavingThrow(effect.ability)) }
        .map { it.side }
        .toSet()
    val advantageMode = resolveAdvantage(effect.advantage + derivedAdvantage)
    val (rollValue, afterD20) = rollD20(state, mode, advantageMode)
    val success = rollValue + mod >= effect.dc

    val rolledEvent = GameEvent.SaveRolled(
        target = target.id,
        ability = effect.ability,
        d20 = rollValue,
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
        // No actor at all — nothing to ask, nothing to decide. ReactionTriggered still fires below
        // (it means "this opportunity was evaluated", regardless of who — or whether anyone — answers).
        Answerer.NeverReacts -> HandlerOutcome(state)
    }
    return outcome.copy(events = listOf(triggeredEvent) + outcome.events)
}

private fun resolveReaction(state: GameState, effect: Effect.ResolveReaction, answers: Map<DecisionId, Decision>, cat: Catalog): HandlerOutcome {
    val accept = answers[effect.decisionId]?.accept ?: false
    return if (accept) acceptReaction(state, effect.who, effect.actionId, effect.trigger, cat) else HandlerOutcome(state)
}

/**
 * `matchingReaction` already gates on `reactionUsed`/alive/geometry before this is ever called,
 * but not affordability — that gap let a reaction fire its effects for free (KNOWN_ISSUES.md #3):
 * `SpendCost` fizzling on insufficient mana doesn't stop the effects spawned right behind it on
 * the stack, since a fizzle is just an event, not a "cancel the rest" signal. `canPerform` itself
 * can't be reused here — its `NotYourTurn` check assumes the caster is the active entity, which a
 * reactor by definition is not. Mana is the only cost a Reaction-cost action ever has (`perform`'s
 * own SpendCost construction only ever gives Reaction actions an `ap` of 0), so that's the only
 * check needed here.
 */
private fun acceptReaction(state: GameState, who: EntityId, actionId: ActionId, trigger: GameEvent, cat: Catalog): HandlerOutcome {
    val def = cat.actionDef(actionId)
    val spend = Effect.SpendCost(who, mana = def.cost.mana, markReactionUsed = true)

    val available = state.byId[who]?.resources?.mana ?: 0
    if (available < def.cost.mana) return fizzle(state, spend, Rejection.NotEnoughMana(def.cost.mana, available))

    val ctx = ActionCtx(who, targetsFor(trigger))
    val instantiated = def.effects.flatMap { it.instantiate(state, ctx, cat) }
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

    val whoStats = who.stats(cat)
    val conScore = whoStats.abilities.con
    val mod = abilityModifier(conScore)
    // A concentration check is a CON saving throw in all but name — no explicit per-effect
    // advantage field exists here (ConcentrationCheck never had one), but a granted "advantage on
    // CON saves" must still apply, or it stays silently inert specifically for this roll.
    val derivedAdvantage = whoStats.rollGrants
        .filter { it.ctx.matches(RollContext.SavingThrow(Ability.Con)) }
        .map { it.side }
        .toSet()
    val (rollValue, afterRoll) = rollD20(state, mode, resolveAdvantage(derivedAdvantage))
    val success = rollValue + mod >= effect.dc

    val events = mutableListOf<GameEvent>(GameEvent.ConcentrationCheckRolled(who.id, effect.dc, rollValue, mod, success))
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

private fun isDead(entity: Entity): Boolean {
    val health = entity.health
    return health != null && health.current <= 0
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

    // step 7 — advance past any dead entity still sitting in turn.order (nothing removes them;
    // that's DestroyEntity's job, which doesn't exist yet — see KNOWN_ISSUES.md #7). Silent skip,
    // no event: there's no GameEvent for "this entity's turn was skipped" and inventing one is a
    // bigger change than this fix. A wholly-dead order throws rather than spinning forever.
    val order = working.turn.order
    check(order.isNotEmpty()) { "EndTurn requires a non-empty turn.order" }

    var nextIndex = working.turn.activeIndex
    var round = working.turn.round
    var advanced = 0
    do {
        nextIndex = (nextIndex + 1) % order.size
        if (nextIndex == 0) {
            working = expireStatuses(working, TurnMoment.EndOfRound(round), events)
            round += 1
        }
        advanced++
        check(advanced <= order.size) { "EndTurn found no living entity in turn.order — every entity is dead" }
    } while (isDead(working.byId.getValue(order[nextIndex])))

    val nextActiveId = order[nextIndex]
    working = working.copy(turn = working.turn.copy(round = round, activeIndex = nextIndex, phase = TurnPhase.Start))
    events += GameEvent.TurnStarted(nextActiveId, round)

    // steps 1-2 (stats are always derived, nothing to "recompute" as a write) -3
    working = expireStatuses(working, TurnMoment.StartOfTurn(nextActiveId, round), events)

    val nextActive = working.byId.getValue(nextActiveId)
    val stats = nextActive.stats(cat)
    // AP/Quick/Reaction reset every turn — a per-turn tactical budget. Mana does NOT: it's a
    // per-ENCOUNTER pool (doc10-game-loop.md), refilled only by Effect.RefillMana, not here.
    // Resetting it every turn made it indistinguishable from AP and removed any reason to ration a
    // spell across a fight — see KNOWN_ISSUES.md/doc17 1.1.
    working = working.withEntity(nextActiveId) { entity ->
        val resources = entity.resources
        if (resources == null) entity else entity.copy(resources = resources.copy(ap = stats.maxAp, quickUsed = false, reactionUsed = false))
    }
    // mana here is nextActive's PRE-reset (i.e. current, unchanged) value — this event reports the
    // full resource state after the boundary for UI/animation purposes, it does not claim mana was
    // reset.
    val preResetResources = nextActive.resources
    if (preResetResources != null) events += GameEvent.ResourcesReset(nextActiveId, stats.maxAp, preResetResources.mana)

    // step 4
    val tickEffects = nextActive.statuses.flatMap { status ->
        val def = cat.statusDef(status.def)
        if (def.onTurnStart.isEmpty()) {
            emptyList()
        } else {
            val ctx = ActionCtx(caster = status.sourceId ?: nextActiveId, targets = listOf(nextActiveId))
            def.onTurnStart.flatMap { it.instantiate(working, ctx, cat) }
        }
    }

    working = working.copy(turn = working.turn.copy(phase = TurnPhase.Main)) // step 5

    return HandlerOutcome(working, events, tickEffects)
}
