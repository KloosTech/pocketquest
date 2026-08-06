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
    data class DealDamage(val target: Ref, val amount: Int, val damageType: DamageType) : EffectTemplate

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
