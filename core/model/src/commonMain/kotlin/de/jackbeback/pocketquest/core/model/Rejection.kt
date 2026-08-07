package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Doc05's full list also has BlockedByStatus and MissingEquipment — both
 * skipped here because their prerequisites don't exist yet (no
 * action-blocking status data, no equip() validation rules). ActionAlreadyUsed
 * is also skipped: it needs a per-turn "main action used" gate that doesn't
 * exist on [Resources] yet (only `quickUsed` does) — see the pass-3 commit
 * for the reasoning on why that gate wasn't added this pass.
 */
@Serializable
sealed interface Rejection {
    @Serializable @SerialName("notEnoughMana") data class NotEnoughMana(val need: Int, val have: Int) : Rejection
    @Serializable @SerialName("notEnoughAp") data class NotEnoughAp(val need: Int, val have: Int) : Rejection
    @Serializable @SerialName("targetMissing") data class TargetMissing(val target: EntityId) : Rejection
    @Serializable @SerialName("blocked") data class Blocked(val pos: GridPos) : Rejection
    @Serializable @SerialName("notYourTurn") data object NotYourTurn : Rejection
    @Serializable @SerialName("quickAlreadyUsed") data object QuickAlreadyUsed : Rejection
    @Serializable @SerialName("outOfRange") data class OutOfRange(val distance: Int, val max: Int) : Rejection
    @Serializable @SerialName("noLineOfSight") data object NoLineOfSight : Rejection
    @Serializable @SerialName("noLegalTarget") data object NoLegalTarget : Rejection
    /** docs/17-engine-gaps.md 1.5 — a Downed (0 HP) entity cannot act, full stop, not even via a Free action. */
    @Serializable @SerialName("downed") data object Downed : Rejection
    /** docs/18-damage-pipeline.md — a DamageStep.Prevent matched (immunity, "ignore the first hit this round"). Not a precondition failure, but still reported rather than silently doing nothing — doc15's own warning against silent no-ops. */
    @Serializable @SerialName("prevented") data object Prevented : Rejection
}
