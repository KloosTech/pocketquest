package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.content.dsl.SkillEffect
import de.jackbeback.pocketquest.content.dsl.StatAttribute
import de.jackbeback.pocketquest.content.dsl.attributeModifier
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.ConditionAppliedEvent
import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.HealEvent
import de.jackbeback.pocketquest.ecs.components.combat.MissEvent
import de.jackbeback.pocketquest.ecs.components.combat.SkillUsedEvent
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.components.core.StatsComponent
import de.jackbeback.pocketquest.ecs.components.core.TurnStateComponent
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set

/**
 * Resolves [SkillUsedEvent]s end-to-end: mana check, optional hit roll, dice, and event emission.
 *
 * Player action economy: gated by mana only — unlimited actions per turn as long as mana allows.
 * Enemy action economy: gated by [TurnStateComponent.hasActed] (one skill per AI turn).
 */
class SkillResolverSystem(
    world: World,
    private val skillRegistry: SkillRegistry,
    private val onLog: (String) -> Unit = {},
    private val tileMap: TileMap? = null,
) : GameSystem {

    init {
        world.events().on<SkillUsedEvent> { event ->
            if (!world.isAlive(event.user)) return@on
            if (!world.isAlive(event.target)) return@on

            val skill = skillRegistry.find(event.skillId) ?: return@on

            // Enemies are limited to one skill per turn; players are mana-gated only
            val faction = world.get<FactionComponent>(event.user)?.faction
            val turnState = world.get<TurnStateComponent>(event.user)
            if (faction != Faction.PLAYER && turnState?.hasActed == true) return@on

            // Check mana
            val mana = world.get<ManaComponent>(event.user) ?: return@on
            if (mana.current < skill.manaCost) {
                onLog("Not enough mana to cast ${skill.name}.")
                return@on
            }

            val casterName = world.get<NameComponent>(event.user)?.displayName ?: "?"
            val targetName = world.get<NameComponent>(event.target)?.displayName ?: "?"

            // Deduct mana; mark enemies as acted (players may act again this turn)
            world.set(event.user, mana.copy(current = mana.current - skill.manaCost))
            if (faction != Faction.PLAYER && turnState != null) {
                world.set(event.user, turnState.copy(hasActed = true))
            }

            // Hit roll (if required) — uses DEX modifier with proper floor division
            if (skill.needsHitRoll) {
                val casterDex = world.get<StatsComponent>(event.user)?.dex ?: 10
                val dexMod = attributeModifier(casterDex)
                val targetAc = world.get<StatsComponent>(event.target)?.ac ?: 10
                val roll = (1..20).random() + dexMod
                if (roll < targetAc) {
                    onLog("$casterName casts ${skill.name} — missed! (rolled $roll vs AC $targetAc)")
                    world.events().emit(MissEvent(event.user, event.target))
                    return@on
                }
            }

            // StrengthUp bonus: +2 damage per stack, applied to every Damage effect this cast
            val strengthStacks = world.get<ConditionsComponent>(event.user)
                ?.active?.get(ConditionType.StrengthUp) ?: 0
            val strengthBonus = strengthStacks * 2

            // Attribute modifier for this skill (D&D formula: floor((stat - 10) / 2))
            val casterStats = world.get<StatsComponent>(event.user)
            val attrMod: Int = skill.attributeModifier?.let { attr ->
                val statValue = when (attr) {
                    StatAttribute.STR -> casterStats?.str
                    StatAttribute.DEX -> casterStats?.dex
                    StatAttribute.CON -> casterStats?.con
                    StatAttribute.INT -> casterStats?.intelligence
                    StatAttribute.WIS -> casterStats?.wis
                    StatAttribute.CHA -> casterStats?.cha
                } ?: 10
                attributeModifier(statValue)
            } ?: 0

            // Cover reduction (ranged skills only)
            val cover = if (skill.isRanged) coverReduction(world, event.target) else 0f

            // Resolve each effect
            for (effect in skill.effects) {
                when (effect) {
                    is SkillEffect.Damage -> {
                        val base = (effect.dice.roll() + strengthBonus + attrMod).coerceAtLeast(1)
                        val amount = if (cover > 0f) {
                            (base * (1f - cover)).toInt().coerceAtLeast(1)
                        } else base
                        val modStr = if (attrMod != 0) {
                            val sign = if (attrMod > 0) "+" else ""
                            " (${skill.attributeModifier?.name} $sign$attrMod)"
                        } else ""
                        val coverStr = if (cover > 0f) " [cover: ${(cover * 100).toInt()}% reduction]" else ""
                        onLog("$casterName casts ${skill.name} on $targetName for $amount ${effect.type.name} damage$modStr$coverStr.")
                        world.events().emit(DamageEvent(event.user, event.target, amount, effect.type))
                    }
                    is SkillEffect.Heal -> {
                        val amount = (effect.dice.roll() + attrMod).coerceAtLeast(1)
                        val modStr = if (attrMod != 0) {
                            val sign = if (attrMod > 0) "+" else ""
                            " (${skill.attributeModifier?.name} $sign$attrMod)"
                        } else ""
                        onLog("$casterName casts ${skill.name} and heals $targetName for $amount HP$modStr.")
                        world.events().emit(HealEvent(event.user, event.target, amount))
                    }
                    is SkillEffect.ApplyCondition -> {
                        onLog("$casterName applies ${effect.condition.name} (×${effect.stacks}) to $targetName.")
                        world.events().emit(ConditionAppliedEvent(event.target, effect.condition, effect.stacks))
                    }
                }
            }
        }
    }

    private fun coverReduction(world: World, targetId: EntityId): Float {
        val tm = tileMap ?: return 0f
        val pos = world.get<PositionComponent>(targetId) ?: return 0f
        val standingCover = tm.typeAt(pos.col, pos.row).coverValue
        val wallCover = tm.neighbours(pos.col, pos.row)
            .maxOfOrNull { if (!it.isWalkable && it.coverValue > 0f) it.coverValue else 0f } ?: 0f
        return maxOf(standingCover, wallCover).coerceIn(0f, 1f)
    }

    override fun update(world: World, deltaMs: Long) = Unit
}
