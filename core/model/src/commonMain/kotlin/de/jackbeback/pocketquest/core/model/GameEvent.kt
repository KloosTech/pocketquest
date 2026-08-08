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
    /** docs/17-engine-gaps.md 3.6: [critical] is a natural 20 — always a hit, doubled damage dice. A natural 1 is always a miss (no separate flag needed — it just folds into [hit] being false). */
    @Serializable @SerialName("attackRolled") data class AttackRolled(val attacker: EntityId, val target: EntityId, val d20: Int, val mod: Int, val ac: Int, val hit: Boolean, val critical: Boolean = false) : GameEvent
    /** The individual damage-dice results behind a [DamageTaken]'s final `amount` — found live: two hits with the same weapon landed for different totals and there was no way to tell dice variance from a resistance/modifier swing without this. Emitted only for a real (`RngMode.Live`) roll; `RngMode.Expected`'s averaged preview/AI-scoring rolls have no discrete dice to show. */
    @Serializable @SerialName("damageRolled") data class DamageRolled(val attacker: EntityId, val target: EntityId, val rolls: List<Int>, val modifier: Int, val damageType: DamageType) : GameEvent
    @Serializable @SerialName("saveRolled") data class SaveRolled(val target: EntityId, val ability: Ability, val d20: Int, val mod: Int, val dc: Int, val success: Boolean) : GameEvent
    @Serializable @SerialName("turnStarted") data class TurnStarted(val who: EntityId, val round: Int) : GameEvent
    @Serializable @SerialName("turnEnded") data class TurnEnded(val who: EntityId) : GameEvent
    @Serializable @SerialName("resourcesReset") data class ResourcesReset(val who: EntityId, val ap: Int, val mana: Int) : GameEvent
    @Serializable @SerialName("reactionTriggered") data class ReactionTriggered(val who: EntityId, val actionId: ActionId) : GameEvent
    @Serializable @SerialName("actionStarted") data class ActionStarted(val who: EntityId, val actionId: ActionId) : GameEvent
    @Serializable @SerialName("concentrationStarted") data class ConcentrationStarted(val who: EntityId, val linkId: LinkId) : GameEvent
    @Serializable @SerialName("concentrationBroken") data class ConcentrationBroken(val who: EntityId, val linkId: LinkId) : GameEvent
    @Serializable @SerialName("concentrationCheckRolled") data class ConcentrationCheckRolled(val who: EntityId, val dc: Int, val roll: Int, val mod: Int, val success: Boolean) : GameEvent
    @Serializable @SerialName("healed") data class Healed(val target: EntityId, val amount: Int, val source: EntityId? = null) : GameEvent
    @Serializable @SerialName("manaRefilled") data class ManaRefilled(val who: EntityId, val mana: Int) : GameEvent

    /**
     * Fires alongside (not instead of) [Died] on the >0 -> 0 HP transition — docs/17-engine-gaps.md
     * 1.5 / docs/10-game-loop.md's "Downed, not dead": a party member reaching 0 HP is revivable,
     * not removed from play, and needs its own hook distinct from Died for canPerform/animation/AI
     * to key off. Died is untouched (still what concentration-break-on-death and the turn-boundary
     * dead-skip react to) — no existing behavior depends on this event yet.
     */
    @Serializable @SerialName("downed") data class Downed(val target: EntityId) : GameEvent

    /** The 0 -> >0 HP transition via Heal — the counterpart to [Downed]. */
    @Serializable @SerialName("revived") data class Revived(val target: EntityId) : GameEvent

    /**
     * docs/18-damage-pipeline.md: emitted by the Retarget/Split damage-pipeline steps, positioned
     * before the resulting DamageTaken — without it the animation director sees damage land on a
     * character nobody attacked. [by] is the status that carried the redirecting step, if any.
     */
    @Serializable @SerialName("damageRedirected") data class DamageRedirected(val from: EntityId, val to: EntityId, val by: StatusId? = null) : GameEvent

    /** Emitted instead of throwing/silently no-opping when a handler's re-validation fails — see docs/04-resolver.md. */
    @Serializable @SerialName("fizzled") data class Fizzled(val effect: String, val reason: Rejection) : GameEvent

    /** doc17-engine-gaps.md 3.1: [Effect.Teleport]'s own event, distinct from [MoveStepped] — an instant blink, not a walked step. */
    @Serializable @SerialName("teleported") data class Teleported(val who: EntityId, val from: GridPos, val to: GridPos) : GameEvent

    /** doc17-engine-gaps.md 3.1: [Effect.SpawnEntity]'s event. */
    @Serializable @SerialName("entitySpawned") data class EntitySpawned(val entityId: EntityId, val archetype: ArchetypeId, val pos: GridPos) : GameEvent

    /** doc17-engine-gaps.md 3.1: [Effect.DestroyEntity]'s event. */
    @Serializable @SerialName("entityDestroyed") data class EntityDestroyed(val target: EntityId) : GameEvent
}
