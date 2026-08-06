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

/** Tracks "entity X was already offered a reaction to event Y" — dedup is by structural equality on the event itself, see the pass-4 commit. */
@Serializable
data class ReactedKey(val who: EntityId, val event: GameEvent)

sealed interface Answerer {
    data object HumanUi : Answerer
    data class Ai(val profile: AiProfileId) : Answerer
    // Auto(rule) from docs/04 omitted — no AutoRule content exists to drive it yet.
}

fun answererFor(entity: Entity): Answerer = when (val controller = entity.actor?.controller) {
    is Controller.Ai -> Answerer.Ai(controller.profile)
    is Controller.Human, null -> Answerer.HumanUi
}

fun GameEvent.kind(): ReactionTriggerKind = when (this) {
    is GameEvent.MoveStepped -> ReactionTriggerKind.MoveStepped
    is GameEvent.DamageTaken -> ReactionTriggerKind.DamageTaken
    is GameEvent.Died -> ReactionTriggerKind.Died
    is GameEvent.StatusApplied -> ReactionTriggerKind.StatusApplied
    is GameEvent.AttackRolled -> ReactionTriggerKind.AttackRolled
    is GameEvent.SaveRolled -> ReactionTriggerKind.SaveRolled
    is GameEvent.ResourcesSpent -> ReactionTriggerKind.ResourcesSpent
    is GameEvent.Fizzled -> ReactionTriggerKind.Fizzled
}

/** Which entities this event's reaction naturally concerns — feeds the reaction's ActionCtx.targets. */
internal fun targetsFor(event: GameEvent): List<EntityId> = when (event) {
    is GameEvent.MoveStepped -> listOf(event.who)
    is GameEvent.DamageTaken -> listOf(event.target)
    is GameEvent.Died -> listOf(event.target)
    is GameEvent.StatusApplied -> listOf(event.target)
    is GameEvent.AttackRolled -> listOf(event.attacker, event.target)
    is GameEvent.SaveRolled -> listOf(event.target)
    is GameEvent.ResourcesSpent -> listOf(event.who)
    is GameEvent.Fizzled -> emptyList()
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
 */
fun collectTriggers(
    state: GameState,
    events: List<GameEvent>,
    depth: Int,
    cat: Catalog,
    alreadyReacted: Set<ReactedKey>,
): Pair<List<Effect>, Set<ReactedKey>> {
    if (depth >= MAX_REACTION_DEPTH) return emptyList<Effect>() to alreadyReacted

    var reacted = alreadyReacted
    val offers = mutableListOf<Effect>()

    for (event in events) {
        val reactors = state.entities
            .filter { ReactedKey(it.id, event) !in reacted }
            .sortedWith(compareBy({ state.turn.order.indexOf(it.id) }, { it.id.raw }))

        for (reactor in reactors) {
            val actionId = matchingReaction(reactor, event, cat) ?: continue
            offers += Effect.OfferReaction(event, reactor.id, actionId)
            reacted = reacted + ReactedKey(reactor.id, event)
        }
    }

    return offers to reacted
}
