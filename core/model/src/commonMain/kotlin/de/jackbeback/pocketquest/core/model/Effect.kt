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

    /**
     * docs/18-damage-pipeline.md: [target]/[amount]/[damageType] are the RAW request — the actual
     * 8-step pipeline (retarget, prevent, convert, scale, reduce, absorb, apply, after) runs
     * inside the handler, entirely synchronously, with hops tracked as a local variable rather
     * than a field here — nothing about an in-progress retarget chain needs to survive a process
     * death mid-chain, since the whole chain resolves within one handler call. [fromReflect]
     * exists purely so a Reflect step's spawned counter-damage can't itself trigger another
     * Reflect — internal bookkeeping, never set by content authoring (EffectTemplate has no
     * matching field).
     */
    @Serializable @SerialName("dealDamage")
    data class DealDamage(
        val target: EntityId,
        val amount: Int,
        val damageType: DamageType,
        val source: EntityId? = null,
        val tags: Set<DamageTag> = emptySet(),
        val fromReflect: Boolean = false,
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
        val tags: Set<DamageTag> = emptySet(),
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

    /** Doc04's 7-step turn boundary, done atomically: no player decision happens mid-transition, so nothing needs a separate stack entry. */
    @Serializable @SerialName("endTurn")
    data class EndTurn(val who: EntityId) : Effect

    /** Ends [caster]'s previous concentration (if any) before starting this one — "one LinkId per entity at a time". */
    @Serializable @SerialName("startConcentration")
    data class StartConcentration(val caster: EntityId, val linkId: LinkId) : Effect

    /** Auto-spawned by DealDamage when its target is concentrating — never authored directly. */
    @Serializable @SerialName("concentrationCheck")
    data class ConcentrationCheck(val who: EntityId, val dc: Int) : Effect

    /** DealDamage's inverse — clamps at derived maxHp rather than 0. */
    @Serializable @SerialName("heal")
    data class Heal(val target: EntityId, val amount: Int, val source: EntityId? = null) : Effect

    /** No-op (not a Fizzled precondition failure) if the status isn't present — mirrors ApplyStatus's KeepStrongest drop case. */
    @Serializable @SerialName("removeStatus")
    data class RemoveStatus(val target: EntityId, val status: StatusId) : Effect

    /** Pure authoring convenience: unpacks into its effects with no state change or event of its own. */
    @Serializable @SerialName("composite")
    data class Composite(val effects: List<Effect>) : Effect

    /**
     * Mana is a per-encounter pool (docs/10-game-loop.md), not a per-turn one — `endTurn` no
     * longer touches it. This is the only thing that refills it, pushed by whoever decides an
     * encounter is over (`:app` today; `:core:run`'s `finishEncounter` once that module exists —
     * see KNOWN_ISSUES.md 1.1). Refill everyone at once via `Composite(entities.map { RefillMana(it.id) })`.
     */
    @Serializable @SerialName("refillMana")
    data class RefillMana(val who: EntityId) : Effect
}
