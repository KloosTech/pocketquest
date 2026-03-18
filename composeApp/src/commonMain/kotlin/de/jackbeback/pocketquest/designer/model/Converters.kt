package de.jackbeback.pocketquest.designer.model

import de.jackbeback.pocketquest.content.definitions.allSkills
import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.content.dsl.Dice
import de.jackbeback.pocketquest.content.dsl.SkillEffect
import de.jackbeback.pocketquest.content.dsl.SkillTemplate
import de.jackbeback.pocketquest.content.dsl.UnitTemplate
import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.core.AIStrategy
import de.jackbeback.pocketquest.ecs.components.core.Faction

// ── SkillDefinition ↔ SkillTemplate ──────────────────────────────────────────

fun SkillDefinition.toSkillTemplate(): SkillTemplate = SkillTemplate(id).also { t ->
    t.name          = name
    t.description   = description
    t.manaCost      = manaCost
    t.range         = range
    t.needsTarget   = needsTarget
    t.needsHitRoll  = needsHitRoll
    t.spriteKey     = spriteKey
    t.animationType = runCatching { AnimationType.valueOf(animationType) }.getOrDefault(AnimationType.INSTANT)
    t.maxTargets    = maxTargets
    effects.forEach { dto ->
        when (dto.type) {
            "damage" -> {
                val dmgType = runCatching { DamageType.valueOf(dto.damageType) }.getOrDefault(DamageType.BLUDGEONING)
                t.effects += SkillEffect.Damage(Dice(dto.count, dto.sides, dto.bonus), dmgType)
            }
            "heal" -> t.effects += SkillEffect.Heal(Dice(dto.count, dto.sides, dto.bonus))
            "condition" -> {
                val cond = runCatching { ConditionType.valueOf(dto.conditionType) }.getOrDefault(ConditionType.Burn)
                t.effects += SkillEffect.ApplyCondition(cond, dto.stacks)
            }
        }
    }
}

fun SkillTemplate.toSkillDefinition(): SkillDefinition = SkillDefinition(
    id           = id,
    name         = name,
    description  = description,
    manaCost     = manaCost,
    range        = range,
    needsTarget  = needsTarget,
    needsHitRoll = needsHitRoll,
    spriteKey    = spriteKey,
    animationType = animationType.name,
    maxTargets   = maxTargets,
    effects      = effects.map { effect ->
        when (effect) {
            is SkillEffect.Damage ->
                EffectDto("damage", effect.dice.count, effect.dice.sides, effect.dice.bonus, effect.type.name)
            is SkillEffect.Heal ->
                EffectDto("heal", effect.dice.count, effect.dice.sides, effect.dice.bonus)
            is SkillEffect.ApplyCondition ->
                EffectDto("condition", conditionType = effect.condition.name, stacks = effect.stacks)
        }
    },
)

// ── EnemyDefinition ↔ UnitTemplate ───────────────────────────────────────────

fun EnemyDefinition.toUnitTemplate(): UnitTemplate = UnitTemplate(id).also { t ->
    t.name            = name
    t.faction         = Faction.ENEMY
    t.sprite          = sprite
    t.maxHealth       = maxHealth
    t.maxMana         = maxMana
    t.manaRegen       = manaRegen
    t.maxAp           = maxAp
    t.movesPerTurn    = movesPerTurn
    t.moveRange       = moveRange
    t.str             = stats.str
    t.dex             = stats.dex
    t.con             = stats.con
    t.intelligence    = stats.intelligence
    t.wis             = stats.wis
    t.cha             = stats.cha
    t.ac              = stats.ac
    t.initiative      = initiative
    t.aiStrategy      = runCatching { AIStrategy.valueOf(aiStrategy) }.getOrDefault(AIStrategy.Aggressive)
    t.preferredSkillId = preferredSkillId
    t.spawnCol        = spawnCol
    t.spawnRow        = spawnRow
    t.skillIds.addAll(skillIds)
    damageResistances.forEach { (typeStr, mult) ->
        runCatching { DamageType.valueOf(typeStr) }.getOrNull()?.let { t.damageResistances[it] = mult }
    }
}

fun UnitTemplate.toEnemyDefinition(): EnemyDefinition = EnemyDefinition(
    id          = id,
    name        = name,
    sprite      = sprite,
    stats       = StatBlock(str, dex, con, intelligence, wis, cha, ac),
    maxHealth   = maxHealth,
    maxMana     = maxMana,
    manaRegen   = manaRegen,
    maxAp       = maxAp,
    movesPerTurn = movesPerTurn,
    moveRange   = moveRange,
    initiative  = initiative,
    aiStrategy  = aiStrategy.name,
    preferredSkillId = preferredSkillId,
    spawnCol    = spawnCol,
    spawnRow    = spawnRow,
    skillIds    = skillIds.toList(),
    damageResistances = damageResistances.mapKeys { it.key.name },
)

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Built-in skills as [SkillDefinition] for seeding the designer. */
val builtInSkillDefinitions: List<SkillDefinition>
    get() = allSkills.map { it.toSkillDefinition() }
