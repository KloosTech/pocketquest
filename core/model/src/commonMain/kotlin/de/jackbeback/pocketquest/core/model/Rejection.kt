package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Only the variants pass 2's effect handlers actually produce. The full
 * list from docs/05-actions-and-effects.md (OutOfRange, NoLineOfSight,
 * BlockedByStatus, MissingEquipment, NotYourTurn, ...) arrives with
 * canPerform() in a later pass.
 */
@Serializable
sealed interface Rejection {
    @Serializable @SerialName("notEnoughMana") data class NotEnoughMana(val need: Int, val have: Int) : Rejection
    @Serializable @SerialName("notEnoughAp") data class NotEnoughAp(val need: Int, val have: Int) : Rejection
    @Serializable @SerialName("targetMissing") data class TargetMissing(val target: EntityId) : Rejection
    @Serializable @SerialName("blocked") data class Blocked(val pos: GridPos) : Rejection
}
