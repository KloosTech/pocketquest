package de.jackbeback.pocketquest.content.definitions

import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.content.dsl.Dice
import de.jackbeback.pocketquest.content.dsl.skill
import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.DamageType

val magicMissileSkill = skill("magic_missile") {
    name = "Magic Missile"
    manaCost = 2
    range = 6
    needsHitRoll = false
    spriteKey = "MagicMissile"
    animationType = AnimationType.PROJECTILE
    damage(Dice(1, 4, 1), DamageType.FORCE)
}

val basicHealSkill = skill("basic_heal") {
    name = "Basic Heal"
    manaCost = 2
    range = 1  // self-heal (target own cell)
    needsHitRoll = false
    needsTarget = false
    spriteKey = "BasicHeal"
    animationType = AnimationType.HEAL
    heal(Dice(1, 4, 2))
}

val thornWhipSkill = skill("thorn_whip") {
    name = "Thorn Whip"
    manaCost = 1
    range = 2
    spriteKey = "ThornWhip"
    animationType = AnimationType.MELEE
    damage(Dice(1, 4), DamageType.PIERCING)
    applyCondition(ConditionType.Poison)
}

val fireballSkill = skill("fireball") {
    name = "Fireball"
    manaCost = 3
    range = 5
    needsHitRoll = false
    spriteKey = "CrimsonArcana"
    animationType = AnimationType.PROJECTILE
    damage(Dice(1, 6, 2), DamageType.FIRE)
    applyCondition(ConditionType.Burn, 2)
}

val basicAttackSkill = skill("basic_attack") {
    name = "Attack"
    manaCost = 0
    range = 1
    needsHitRoll = true
    spriteKey = ""
    animationType = AnimationType.MELEE
    damage(Dice(1, 4), DamageType.BLUDGEONING)
}

val allSkills = listOf(magicMissileSkill, basicHealSkill, thornWhipSkill, fireballSkill, basicAttackSkill)
