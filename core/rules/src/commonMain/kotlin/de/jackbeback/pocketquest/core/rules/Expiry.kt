package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.LinkId

/** A concrete moment the turn boundary sweep asks "which expiries match this?" about. */
sealed interface TurnMoment {
    data class EndOfTurn(val who: EntityId, val round: Int) : TurnMoment
    data class StartOfTurn(val who: EntityId, val round: Int) : TurnMoment
    data class EndOfRound(val round: Int) : TurnMoment
}

/**
 * Pure predicate: does this expiry fire at this moment? [Expiry.OnConcentrationLost]
 * is never a turn-boundary moment — it fires when concentration breaks
 * (see [statusesLinkedTo]), which is resolver territory (pass 2).
 */
fun Expiry.matches(moment: TurnMoment): Boolean = when (this) {
    is Expiry.Permanent -> false
    is Expiry.OnConcentrationLost -> false
    is Expiry.EndOfTurnOf -> moment is TurnMoment.EndOfTurn && moment.who == who && moment.round == round
    is Expiry.StartOfTurnOf -> moment is TurnMoment.StartOfTurn && moment.who == who && moment.round == round
    is Expiry.EndOfRound -> moment is TurnMoment.EndOfRound && moment.round == round
    // Never actually stored — Handlers.kt's applyStatus resolves this into a concrete EndOfRound
    // the moment a status is applied, so this branch exists only for exhaustiveness.
    is Expiry.Turns -> false
}

/** Every status sharing [link], across every entity — concentration breaking removes all of them at once. */
fun GameState.statusesLinkedTo(link: LinkId): List<Pair<EntityId, ActiveStatus>> =
    entities.flatMap { e -> e.statuses.filter { it.linkId == link }.map { e.id to it } }
