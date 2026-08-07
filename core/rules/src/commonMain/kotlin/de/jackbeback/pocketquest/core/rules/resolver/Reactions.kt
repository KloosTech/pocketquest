package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.ReactionTriggerKind
import de.jackbeback.pocketquest.core.model.chebyshevDistanceTo
import de.jackbeback.pocketquest.core.rules.targeting.rangeInTiles
import kotlinx.serialization.Serializable

/** Reaction chains trigger reactions — this bounds it. Two creatures with mutually-triggering reactions loop forever without it. */
const val MAX_REACTION_DEPTH = 8

/**
 * Tracks "entity X was already offered a reaction to event Y". `version` — `state.version` at the
 * moment the event was collected — disambiguates two structurally-identical events for the same
 * entity at different points within one resolver run; dedup on `(who, event)` alone would treat a
 * second, later occurrence of an equal event as the same offer and silently skip it
 * (KNOWN_ISSUES.md #4).
 */
@Serializable
data class ReactedKey(val who: EntityId, val event: GameEvent, val version: Long)

sealed interface Answerer {
    data object HumanUi : Answerer
    data class Ai(val profile: AiProfileId) : Answerer

    /**
     * No `actor` at all — a wall, hazard, or other non-combatant. Never answers. Distinct from
     * `HumanUi` on purpose: offering a reaction to an actor-less entity used to fall into
     * `HumanUi` and push `Effect.Ask`, parking the resolver in `AwaitingInput` forever waiting for
     * a decision nobody can make (KNOWN_ISSUES.md #8).
     */
    data object NeverReacts : Answerer
    // Auto(rule) from docs/04 omitted — no AutoRule content exists to drive it yet.
}

fun answererFor(entity: Entity): Answerer {
    val actor = entity.actor ?: return Answerer.NeverReacts
    return when (val controller = actor.controller) {
        is Controller.Ai -> Answerer.Ai(controller.profile)
        is Controller.Human -> Answerer.HumanUi
    }
}

fun GameEvent.kind(): ReactionTriggerKind = when (this) {
    is GameEvent.MoveStepped -> ReactionTriggerKind.MoveStepped
    is GameEvent.DamageTaken -> ReactionTriggerKind.DamageTaken
    is GameEvent.Died -> ReactionTriggerKind.Died
    is GameEvent.StatusApplied -> ReactionTriggerKind.StatusApplied
    is GameEvent.StatusExpired -> ReactionTriggerKind.StatusExpired
    is GameEvent.AttackRolled -> ReactionTriggerKind.AttackRolled
    is GameEvent.SaveRolled -> ReactionTriggerKind.SaveRolled
    is GameEvent.ResourcesSpent -> ReactionTriggerKind.ResourcesSpent
    is GameEvent.Fizzled -> ReactionTriggerKind.Fizzled
    is GameEvent.TurnStarted -> ReactionTriggerKind.TurnStarted
    is GameEvent.TurnEnded -> ReactionTriggerKind.TurnEnded
    is GameEvent.ConcentrationStarted -> ReactionTriggerKind.ConcentrationStarted
    is GameEvent.ConcentrationBroken -> ReactionTriggerKind.ConcentrationBroken
    is GameEvent.ConcentrationCheckRolled -> ReactionTriggerKind.ConcentrationCheckRolled
    is GameEvent.ResourcesReset -> ReactionTriggerKind.ResourcesReset
    is GameEvent.ReactionTriggered -> ReactionTriggerKind.ReactionTriggered
    is GameEvent.ActionStarted -> ReactionTriggerKind.ActionStarted
    is GameEvent.Healed -> ReactionTriggerKind.Healed
    is GameEvent.ManaRefilled -> ReactionTriggerKind.ManaRefilled
    is GameEvent.Downed -> ReactionTriggerKind.Downed
    is GameEvent.Revived -> ReactionTriggerKind.Revived
    is GameEvent.DamageRedirected -> ReactionTriggerKind.DamageRedirected
}

/** Which entities this event's reaction naturally concerns — feeds the reaction's ActionCtx.targets. */
internal fun targetsFor(event: GameEvent): List<EntityId> = when (event) {
    is GameEvent.MoveStepped -> listOf(event.who)
    is GameEvent.DamageTaken -> listOf(event.target)
    is GameEvent.Died -> listOf(event.target)
    is GameEvent.StatusApplied -> listOf(event.target)
    is GameEvent.StatusExpired -> listOf(event.target)
    is GameEvent.AttackRolled -> listOf(event.attacker, event.target)
    is GameEvent.SaveRolled -> listOf(event.target)
    is GameEvent.TurnStarted -> listOf(event.who)
    is GameEvent.TurnEnded -> listOf(event.who)
    is GameEvent.ConcentrationStarted -> listOf(event.who)
    is GameEvent.ConcentrationBroken -> listOf(event.who)
    is GameEvent.ConcentrationCheckRolled -> listOf(event.who)
    is GameEvent.ResourcesSpent -> listOf(event.who)
    is GameEvent.Fizzled -> emptyList()
    is GameEvent.ResourcesReset -> listOf(event.who)
    is GameEvent.ReactionTriggered -> listOf(event.who)
    is GameEvent.ActionStarted -> listOf(event.who)
    is GameEvent.Healed -> listOf(event.target)
    is GameEvent.ManaRefilled -> listOf(event.who)
    is GameEvent.Downed -> listOf(event.target)
    is GameEvent.Revived -> listOf(event.target)
    is GameEvent.DamageRedirected -> listOf(event.from, event.to)
}

/**
 * The trigger-kind-specific extra condition beyond "an eligible reaction
 * exists". Kept as rules code, not authored data — an opportunity attack
 * needs "did the mover leave my reach", which isn't expressible as a
 * generic ReactionTriggerKind match alone. Reuses the reaction ActionDef's
 * own targeting.range as its reach, rather than a separate reach field.
 */
private fun matchesGeometry(reactor: Entity, event: GameEvent, reach: Int): Boolean = when (event) {
    is GameEvent.MoveStepped -> {
        val reactorPos = reactor.pos
        if (reactor.id == event.who || reactorPos == null) {
            false
        } else {
            reactorPos.chebyshevDistanceTo(event.from) <= reach && reactorPos.chebyshevDistanceTo(event.to) > reach
        }
    }
    else -> true
}

/**
 * The first Reaction-cost action [reactor] could use in response to
 * [event], or null if none applies. Checked in archetype action order
 * (deterministic) — if an entity had multiple matching reactions, only the
 * first is offered; picking among several is a follow-up, not exercised by
 * any content yet.
 */
fun matchingReaction(reactor: Entity, event: GameEvent, cat: Catalog): ActionId? {
    if (reactor.resources?.reactionUsed == true) return null
    val health = reactor.health
    if (health != null && health.current <= 0) return null

    val archetype = cat.archetype(reactor.archetype)
    return archetype.actions
        .map { cat.actionDef(it) }
        .firstOrNull { def ->
            def.cost.action == ActionCost.Reaction &&
                def.reactionTrigger?.kind == event.kind() &&
                matchesGeometry(reactor, event, rangeInTiles(def.targeting.range))
        }
        ?.id
}

/**
 * docs/04-resolver.md's collectTriggers. Sorting by initiative index then
 * EntityId, the depth guard, and one-offer-per-entity-per-event are all
 * load-bearing — see the doc. Returns the updated `alreadyReacted` set
 * alongside the offers so the caller can thread it through the resolver.
 *
 * The depth guard throws rather than returning no offers — doc09 specifies
 * "throw rather than hang" for a mutually-triggering reaction pair, and a
 * silent empty return here is exactly the failure mode doc04's guard-rail
 * philosophy (MAX_STEPS, a stale resume() id) exists to avoid. See
 * KNOWN_ISSUES.md #5b.
 */
fun collectTriggers(
    state: GameState,
    events: List<GameEvent>,
    depth: Int,
    cat: Catalog,
    alreadyReacted: Set<ReactedKey>,
): Pair<List<Effect>, Set<ReactedKey>> {
    check(depth < MAX_REACTION_DEPTH) { "reaction depth exceeded MAX_REACTION_DEPTH=$MAX_REACTION_DEPTH — likely a mutual-reaction loop" }

    var reacted = alreadyReacted
    val offers = mutableListOf<Effect>()

    for (event in events) {
        val reactors = state.entities
            .filter { ReactedKey(it.id, event, state.version) !in reacted }
            .sortedWith(compareBy({ state.turn.order.indexOf(it.id) }, { it.id.raw }))

        for (reactor in reactors) {
            val actionId = matchingReaction(reactor, event, cat) ?: continue
            offers += Effect.OfferReaction(event, reactor.id, actionId)
            reacted = reacted + ReactedKey(reactor.id, event, state.version)
        }
    }

    return offers to reacted
}
