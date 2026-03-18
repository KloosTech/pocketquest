package de.jackbeback.pocketquest.content.dsl

import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.DamageType

/** Represents a dice roll: [count]d[sides] + [bonus]. */
data class Dice(val count: Int, val sides: Int, val bonus: Int = 0) {
    fun roll(): Int = (1..count).sumOf { (1..sides).random() } + bonus
}

sealed class SkillEffect {
    data class Damage(val dice: Dice, val type: DamageType) : SkillEffect()
    data class Heal(val dice: Dice) : SkillEffect()
    data class ApplyCondition(val condition: ConditionType, val stacks: Int) : SkillEffect()
}

enum class AnimationType { PROJECTILE, MELEE, HEAL, INSTANT }

/** Builder for skill definitions. */
class SkillTemplate(val id: String) {
    var name: String = ""
    var description: String = ""
    var manaCost: Int = 0
    var range: Int = 1
    var needsTarget: Boolean = true
    var needsHitRoll: Boolean = true
    var spriteKey: String = ""
    var animationType: AnimationType = AnimationType.INSTANT
    /** How many separate targets this skill can be applied to in one action (default 1). */
    var maxTargets: Int = 1
    val effects = mutableListOf<SkillEffect>()

    fun damage(dice: Dice, type: DamageType) { effects += SkillEffect.Damage(dice, type) }
    fun heal(dice: Dice) { effects += SkillEffect.Heal(dice) }
    fun applyCondition(condition: ConditionType, stacks: Int = 1) {
        effects += SkillEffect.ApplyCondition(condition, stacks)
    }
}

fun skill(id: String, block: SkillTemplate.() -> Unit): SkillTemplate =
    SkillTemplate(id).apply(block)
