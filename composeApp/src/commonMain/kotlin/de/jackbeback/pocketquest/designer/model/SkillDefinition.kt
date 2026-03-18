package de.jackbeback.pocketquest.designer.model

import kotlinx.serialization.Serializable

/** Fully serializable skill definition — suitable for JSON save/load. */
@Serializable
data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val manaCost: Int = 0,
    val range: Int = 1,
    val needsTarget: Boolean = true,
    val needsHitRoll: Boolean = true,
    val spriteKey: String = "",
    /** One of: PROJECTILE, MELEE, HEAL, INSTANT */
    val animationType: String = "INSTANT",
    val maxTargets: Int = 1,
    val effects: List<EffectDto> = emptyList(),
)
