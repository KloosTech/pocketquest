package de.jackbeback.pocketquest.designer.model

import kotlinx.serialization.Serializable

/**
 * Flat serializable representation of a skill effect.
 * [type] is one of: "damage", "heal", "condition".
 */
@Serializable
data class EffectDto(
    val type: String,
    // Dice fields (used for damage and heal)
    val count: Int = 1,
    val sides: Int = 6,
    val bonus: Int = 0,
    // Damage-specific
    val damageType: String = "BLUDGEONING",
    // Condition-specific
    val conditionType: String = "Burn",
    val stacks: Int = 1,
)
