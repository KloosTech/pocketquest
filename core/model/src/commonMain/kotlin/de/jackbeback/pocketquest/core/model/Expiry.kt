package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Expiry {
    @Serializable @SerialName("permanent") data object Permanent : Expiry
    @Serializable @SerialName("endOfTurnOf") data class EndOfTurnOf(val who: EntityId, val round: Int) : Expiry
    @Serializable @SerialName("startOfTurnOf") data class StartOfTurnOf(val who: EntityId, val round: Int) : Expiry
    @Serializable @SerialName("endOfRound") data class EndOfRound(val round: Int) : Expiry
    @Serializable @SerialName("onConcentrationLost") data object OnConcentrationLost : Expiry
    /**
     * docs/41-status-duration-and-ability-mods.md: authoring-only — "expires after [n] rounds,"
     * the common case [EndOfRound]'s own absolute round number couldn't express at authoring time.
     * Never persists on a real [ActiveStatus]: `Handlers.kt`'s `applyStatus` resolves this into a
     * concrete `EndOfRound(state.turn.round + n)` the moment the status is actually applied, using
     * whatever round it happens to land on — so [matches] never needs to (and never does) fire for it.
     */
    @Serializable @SerialName("turns") data class Turns(val n: Int) : Expiry
}

@Serializable
enum class StackPolicy { Refresh, AddStacks, KeepStrongest, Independent }

@Serializable
data class SaveSpec(val ability: Ability, val dc: Int)
