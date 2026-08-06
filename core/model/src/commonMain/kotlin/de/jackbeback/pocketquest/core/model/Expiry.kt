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
}

@Serializable
enum class StackPolicy { Refresh, AddStacks, KeepStrongest, Independent }

@Serializable
data class SaveSpec(val ability: Ability, val dc: Int)
