package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A resolved list of modifiers contributed by one source (archetype innate,
 * a worn item, an active status). `ActiveStatus`/`ItemInstance` do NOT
 * implement this directly — they only carry a def id. `stats(cat)` in
 * :core:rules resolves each instance through the [Catalog] into one of
 * these at derivation time, which is what lets a balance patch to a
 * `StatusDef`/`ItemDef` apply retroactively to existing saves.
 */
interface ModifierSource {
    val modifiers: List<Modifier>
}

@Serializable
sealed interface Modifier {
    @Serializable @SerialName("add") data class Add(val stat: Stat, val value: Int) : Modifier
    @Serializable @SerialName("mul") data class Mul(val stat: Stat, val factor: Float) : Modifier
    @Serializable @SerialName("override") data class Override(val stat: Stat, val value: Int) : Modifier
    @Serializable @SerialName("grant") data class Grant(val flag: Flag) : Modifier
    @Serializable @SerialName("resist") data class Resist(val type: DamageType, val level: Resistance) : Modifier
    @Serializable @SerialName("roll") data class Roll(val ctx: RollContext, val side: AdvSide) : Modifier
}

@Serializable
enum class AdvSide { Advantage, Disadvantage }

enum class RollMode { Normal, Advantage, Disadvantage }

@Serializable
sealed interface RollContext {
    @Serializable @SerialName("attack") data class AttackRoll(val vs: Faction? = null) : RollContext
    @Serializable @SerialName("save") data class SavingThrow(val ability: Ability) : RollContext
    @Serializable @SerialName("check") data class AbilityCheck(val skill: Skill) : RollContext
}
