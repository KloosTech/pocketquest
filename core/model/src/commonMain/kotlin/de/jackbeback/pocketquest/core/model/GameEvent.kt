package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Only the events pass 2's handlers actually emit — see [Effect]. */
@Serializable
sealed interface GameEvent {
    @Serializable @SerialName("damageTaken") data class DamageTaken(val target: EntityId, val amount: Int, val type: DamageType) : GameEvent
    @Serializable @SerialName("died") data class Died(val target: EntityId) : GameEvent
    @Serializable @SerialName("moveStepped") data class MoveStepped(val who: EntityId, val from: GridPos, val to: GridPos) : GameEvent
    @Serializable @SerialName("resourcesSpent") data class ResourcesSpent(val who: EntityId, val ap: Int, val mana: Int) : GameEvent
    @Serializable @SerialName("statusApplied") data class StatusApplied(val target: EntityId, val status: StatusId, val stacks: Int, val expiry: Expiry) : GameEvent

    /** Emitted instead of throwing/silently no-opping when a handler's re-validation fails — see docs/04-resolver.md. */
    @Serializable @SerialName("fizzled") data class Fizzled(val effect: String, val reason: Rejection) : GameEvent
}
