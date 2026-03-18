package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.content.dsl.SkillEffect
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.ConditionAppliedEvent
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.HealEvent
import de.jackbeback.pocketquest.ecs.components.combat.SkillUsedEvent
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.components.core.StatsComponent
import de.jackbeback.pocketquest.ecs.components.core.TurnStateComponent
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
    private val onLog: (String) -> Unit = {}
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

            // Hit roll (if required)
            if (skill.needsHitRoll) {
                val casterDex = world.get<StatsComponent>(event.user)?.dex ?: 10
                val dexMod = (casterDex - 10) / 2
                val targetAc = world.get<StatsComponent>(event.target)?.ac ?: 10
                val roll = (1..20).random() + dexMod
                if (roll < targetAc) {
                    onLog("$casterName casts ${skill.name} — missed! (rolled $roll vs AC $targetAc)")
                    return@on
                }
            }

            // Resolve each effect
            for (effect in skill.effects) {
                when (effect) {
                    is SkillEffect.Damage -> {
                        val amount = effect.dice.roll()
                        onLog("$casterName casts ${skill.name} on $targetName for $amount ${effect.type.name} damage.")
                        world.events().emit(DamageEvent(event.user, event.target, amount, effect.type))
                    }
                    is SkillEffect.Heal -> {
                        val amount = effect.dice.roll()
                        onLog("$casterName casts ${skill.name} and heals $targetName for $amount HP.")
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

    override fun update(world: World, deltaMs: Long) = Unit
}
