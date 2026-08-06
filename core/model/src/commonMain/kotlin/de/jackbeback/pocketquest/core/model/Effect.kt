package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Only the 4 primitives docs/09-test-plan.md names for this pass
 * (DealDamage, MoveAlong, SpendCost, ApplyStatus) plus Ask, which the
 * resolver loop itself needs. The other ~10 primitives from
 * docs/05-actions-and-effects.md arrive alongside their handlers in later
 * passes.
 */
@Serializable
sealed interface Effect {
    /** Never reaches a handler — intercepted by run() itself, see :core:rules. */
    @Serializable @SerialName("ask")
    data class Ask(val request: DecisionRequest) : Effect

    @Serializable @SerialName("dealDamage")
    data class DealDamage(
        val target: EntityId,
        val amount: Int,
        val type: DamageType,
        val source: EntityId? = null,
    ) : Effect

    /** Self-continuing: the handler re-pushes with index+1 rather than looping — see docs/04-resolver.md. */
    @Serializable @SerialName("moveAlong")
    data class MoveAlong(val who: EntityId, val path: List<GridPos>, val index: Int = 0) : Effect

    /**
     * Deliberately not built from docs/05's Cost/ActionCost — that needs
     * ActionDef, which doesn't exist yet. Amounts are spelled out directly
     * so this primitive stands alone until actions arrive.
     */
    @Serializable @SerialName("spendCost")
    data class SpendCost(
        val who: EntityId,
        val ap: Int = 0,
        val mana: Int = 0,
        val markQuickUsed: Boolean = false,
        val markReactionUsed: Boolean = false,
    ) : Effect

    @Serializable @SerialName("applyStatus")
    data class ApplyStatus(
        val target: EntityId,
        val status: StatusId,
        val stacks: Int = 1,
        val expiry: Expiry,
        val sourceId: EntityId? = null,
        val linkId: LinkId? = null,
    ) : Effect

    /** d20 + attackBonus vs target's AC. On hit, rolls [damage] itself and spawns DealDamage — dice never roll outside a handler. */
    @Serializable @SerialName("rollAttack")
    data class RollAttack(
        val attacker: EntityId,
        val target: EntityId,
        val attackBonus: Int,
        val advantage: Set<AdvSide> = emptySet(),
        val damage: DiceSpec,
        val damageType: DamageType,
    ) : Effect

    /** d20 + ability modifier vs dc. Spawns whichever branch wins — see docs/05's Slot example; this is the simpler direct-branch shape. */
    @Serializable @SerialName("rollSave")
    data class RollSave(
        val target: EntityId,
        val ability: Ability,
        val dc: Int,
        val advantage: Set<AdvSide> = emptySet(),
        val onSuccess: List<Effect> = emptyList(),
        val onFail: List<Effect> = emptyList(),
    ) : Effect

    /** Consults the reactor's Answerer before pushing an Ask — see docs/04-resolver.md's collectTriggers. */
    @Serializable @SerialName("offerReaction")
    data class OfferReaction(val trigger: GameEvent, val who: EntityId, val actionId: ActionId) : Effect

    /** The continuation pushed alongside Ask when a human must decide; reads the answer and either spawns the reaction's effects or does nothing. */
    @Serializable @SerialName("resolveReaction")
    data class ResolveReaction(val decisionId: DecisionId, val trigger: GameEvent, val who: EntityId, val actionId: ActionId) : Effect
}
