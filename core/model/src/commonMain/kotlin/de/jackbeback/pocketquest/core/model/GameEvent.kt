package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Only the events pass 2's handlers actually emit — see [Effect]. */
@Serializable
sealed interface GameEvent {
    @Serializable @SerialName("damageTaken") data class DamageTaken(val target: EntityId, val amount: Int, val damageType: DamageType) : GameEvent
    @Serializable @SerialName("died") data class Died(val target: EntityId) : GameEvent
    @Serializable @SerialName("moveStepped") data class MoveStepped(val who: EntityId, val from: GridPos, val to: GridPos) : GameEvent
    @Serializable @SerialName("resourcesSpent") data class ResourcesSpent(val who: EntityId, val ap: Int, val mana: Int) : GameEvent
    @Serializable @SerialName("statusApplied") data class StatusApplied(val target: EntityId, val status: StatusId, val stacks: Int, val expiry: Expiry) : GameEvent
    @Serializable @SerialName("statusExpired") data class StatusExpired(val target: EntityId, val status: StatusId) : GameEvent
    @Serializable @SerialName("attackRolled") data class AttackRolled(val attacker: EntityId, val target: EntityId, val d20: Int, val mod: Int, val ac: Int, val hit: Boolean) : GameEvent
    @Serializable @SerialName("saveRolled") data class SaveRolled(val target: EntityId, val ability: Ability, val d20: Int, val mod: Int, val dc: Int, val success: Boolean) : GameEvent
    @Serializable @SerialName("turnStarted") data class TurnStarted(val who: EntityId, val round: Int) : GameEvent
    @Serializable @SerialName("turnEnded") data class TurnEnded(val who: EntityId) : GameEvent
    @Serializable @SerialName("resourcesReset") data class ResourcesReset(val who: EntityId, val ap: Int, val mana: Int) : GameEvent
    @Serializable @SerialName("reactionTriggered") data class ReactionTriggered(val who: EntityId, val actionId: ActionId) : GameEvent
    @Serializable @SerialName("actionStarted") data class ActionStarted(val who: EntityId, val actionId: ActionId) : GameEvent
    @Serializable @SerialName("concentrationStarted") data class ConcentrationStarted(val who: EntityId, val linkId: LinkId) : GameEvent
    @Serializable @SerialName("concentrationBroken") data class ConcentrationBroken(val who: EntityId, val linkId: LinkId) : GameEvent
    @Serializable @SerialName("concentrationCheckRolled") data class ConcentrationCheckRolled(val who: EntityId, val dc: Int, val roll: Int, val mod: Int, val success: Boolean) : GameEvent

    /** Emitted instead of throwing/silently no-opping when a handler's re-validation fails — see docs/04-resolver.md. */
    @Serializable @SerialName("fizzled") data class Fizzled(val effect: String, val reason: Rejection) : GameEvent
}
