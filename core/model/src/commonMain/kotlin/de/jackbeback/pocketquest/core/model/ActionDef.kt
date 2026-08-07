package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Quick is NOT a cheaper Main — see docs/05-actions-and-effects.md. Each is a categorical per-turn gate, not an amount. */
@Serializable
sealed interface ActionCost {
    @Serializable @SerialName("main") data object Main : ActionCost
    @Serializable @SerialName("quick") data object Quick : ActionCost
    @Serializable @SerialName("reaction") data object Reaction : ActionCost
    @Serializable @SerialName("movement") data class Movement(val tiles: Int) : ActionCost
    @Serializable @SerialName("free") data object Free : ActionCost
}

@Serializable
data class Cost(val action: ActionCost, val mana: Int = 0, val charges: ItemId? = null, val hpCost: Int = 0)

/**
 * The authored, Ref-templated counterpart to [Effect] — resolved to concrete
 * effects by `EffectTemplate.instantiate()` in :core:rules. Only the
 * primitives with a handler exist as templates; `requirements`/`behavior`
 * from docs/05 are omitted — no concrete need for them yet, and nothing
 * checks them.
 */
@Serializable
sealed interface EffectTemplate {
    @Serializable @SerialName("dealDamage")
    data class DealDamage(val target: Ref, val amount: Int, val damageType: DamageType, val tags: Set<DamageTag> = emptySet()) : EffectTemplate

    @Serializable @SerialName("applyStatus")
    data class ApplyStatus(val target: Ref, val status: StatusId, val stacks: Int = 1, val expiry: Expiry) : EffectTemplate

    @Serializable @SerialName("rollAttack")
    data class RollAttack(
        val attacker: Ref,
        val target: Ref,
        val attackBonus: Int,
        val advantage: Set<AdvSide> = emptySet(),
        val damage: DiceSpec,
        val damageType: DamageType,
        val tags: Set<DamageTag> = emptySet(),
    ) : EffectTemplate

    @Serializable @SerialName("rollSave")
    data class RollSave(
        val target: Ref,
        val ability: Ability,
        val dc: Int,
        val advantage: Set<AdvSide> = emptySet(),
        val onSuccess: List<EffectTemplate> = emptyList(),
        val onFail: List<EffectTemplate> = emptyList(),
    ) : EffectTemplate

    /** doc17-engine-gaps.md 3.1: [direction] is computed at instantiate() time as [target] minus [awayFrom]'s position, not authored directly — "push away from me" is the actual content-authoring shape (doc05's Thunderwave example), a raw vector isn't. */
    @Serializable @SerialName("push")
    data class Push(val target: Ref, val awayFrom: Ref, val distance: Int) : EffectTemplate

    /** doc17-engine-gaps.md 3.1: the destination is the action's own targeted point (`ActionCtx.point`) — a Point-targeted teleport action's whole reason for existing, not a separate ref/slot. */
    @Serializable @SerialName("teleport")
    data class Teleport(val who: Ref) : EffectTemplate

    /** doc17-engine-gaps.md 3.1: position comes from `ActionCtx.point`, same reasoning as [Teleport] — a "summon" action is Point-targeted by nature. */
    @Serializable @SerialName("spawnEntity")
    data class SpawnEntity(val archetype: ArchetypeId, val faction: Faction, val controller: Controller) : EffectTemplate

    @Serializable @SerialName("destroyEntity")
    data class DestroyEntity(val target: Ref) : EffectTemplate
}

/** A pure declaration — no logic. Performing it pushes SpendCost then its instantiated effects onto the resolver stack. */
@Serializable
data class ActionDef(
    val id: ActionId,
    val name: String,
    val cost: Cost,
    val targeting: Targeting,
    val effects: List<EffectTemplate>,
    /** Only meaningful when cost.action is Reaction — which GameEvent kind offers this reaction. */
    val reactionTrigger: ReactionTrigger? = null,
)

data class PreviewResult(val state: GameState, val events: List<GameEvent>)
