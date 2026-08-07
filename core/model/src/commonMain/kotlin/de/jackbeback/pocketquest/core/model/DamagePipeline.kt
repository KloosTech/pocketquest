package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** docs/18-damage-pipeline.md's DamageInstance.tags — hand-authored on the effect/template that deals damage, never inferred (e.g. from Range.Melee vs Tiles). */
@Serializable
enum class DamageTag { Melee, Ranged, Spell, Aoe, Critical }

/**
 * docs/18-damage-pipeline.md: statuses, equipment, archetypes and features contribute these
 * through their catalog definitions — nothing new stored on the entity, same `ModifierSource`
 * shape as [Modifier]. Collected in the same fixed source order `stats()` uses.
 */
@Serializable
sealed interface DamageStep {
    @Serializable @SerialName("retarget")
    data class Retarget(val to: StepRef, val condition: StepCondition = StepCondition()) : DamageStep

    /** "Share the pain" — spawns a separate DealDamage at [to] for `amount * fraction`; the remaining `1 - fraction` stays on the original target, un-retargeted. */
    @Serializable @SerialName("split")
    data class Split(val to: StepRef, val fraction: Float, val condition: StepCondition = StepCondition()) : DamageStep

    @Serializable @SerialName("prevent")
    data class Prevent(val condition: StepCondition = StepCondition()) : DamageStep

    @Serializable @SerialName("convert")
    data class Convert(val from: DamageType?, val to: DamageType) : DamageStep

    @Serializable @SerialName("scale")
    data class Scale(val factor: Float, val onlyType: DamageType? = null) : DamageStep

    @Serializable @SerialName("reduce")
    data class Reduce(val flat: Int, val onlyType: DamageType? = null) : DamageStep

    @Serializable @SerialName("absorb")
    data class Absorb(val pool: AbsorbPool) : DamageStep

    @Serializable @SerialName("reflect")
    data class Reflect(val fraction: Float, val type: DamageType? = null) : DamageStep
}

/** doc18's HealInstance — "the same shape, but much shorter": no retarget/absorb/reflect, healing has no redirect mechanic anyone asked for. */
@Serializable
sealed interface HealStep {
    @Serializable @SerialName("preventHeal")
    data class Prevent(val condition: StepCondition = StepCondition()) : HealStep

    @Serializable @SerialName("scaleHeal")
    data class Scale(val factor: Float) : HealStep
}

/** Resolved against the status/item/feature that contributed the step. */
@Serializable
sealed interface StepRef {
    /** Whoever applied the status carrying this step — the tank that cast the ward, not the ally wearing it. */
    @Serializable @SerialName("statusSource") data object StatusSource : StepRef
    @Serializable @SerialName("attacker") data object Attacker : StepRef
    @Serializable @SerialName("fixed") data class Fixed(val id: EntityId) : StepRef
}

/**
 * doc18 names `Health.temp` as the only pool this pipeline has today ("It becomes the Absorb
 * pool") but never spells out `AbsorbPool`'s own shape — [TargetTemp] is the direct reading of
 * that sentence: the only variant needed until a second pool (a real "shield" resource distinct
 * from temp HP) exists to justify a second one.
 */
@Serializable
sealed interface AbsorbPool {
    @Serializable @SerialName("targetTemp") data object TargetTemp : AbsorbPool
}

/**
 * `maxPerRound` is declared but not enforced yet — doing so needs a per-entity, per-step,
 * per-round usage counter that nothing in [GameState] tracks today, and doc18's own worked
 * example/test list never exercises it. Flagged rather than half-built: a step author can write
 * `maxPerRound`, but nothing currently limits how often it fires.
 */
@Serializable
data class StepCondition(
    val refWithinTiles: Int? = null,
    val refMustBeHealthy: Boolean = true,
    val requiresTags: Set<DamageTag> = emptySet(),
    val excludesTags: Set<DamageTag> = emptySet(),
    val maxPerRound: Int? = null,
)
