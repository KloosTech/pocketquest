package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.DamageResistancesComponent
import de.jackbeback.pocketquest.ecs.components.combat.HealEvent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set

/**
 * Applies [DamageEvent]s and [HealEvent]s to [HealthComponent]s.
 *
 * Damage pipeline:
 *  1. Resistance multiplier applied
 *  2. Block stacks absorb damage (reduces by stack count); 1 stack consumed per hit
 *  3. Remaining damage applied to HP
 */
class CombatSystem(world: World) : GameSystem {
    init {
        world.events().on<DamageEvent> { event ->
            if (!world.isAlive(event.target)) return@on
            val health = world.get<HealthComponent>(event.target) ?: return@on

            // 1. Resistance
            val resistance = world.get<DamageResistancesComponent>(event.target)
                ?.multipliers?.get(event.damageType) ?: 1.0f
            var actual = (event.amount * resistance).toInt().coerceAtLeast(0)

            // 2. Block absorption — consume 1 stack, reduce damage by the stack count
            val conditions = world.get<ConditionsComponent>(event.target)
            val blockStacks = conditions?.active?.get(ConditionType.Block) ?: 0
            if (blockStacks > 0) {
                actual = (actual - blockStacks).coerceAtLeast(0)
                val remaining = blockStacks - 1
                val updatedActive = if (remaining > 0)
                    conditions!!.active + (ConditionType.Block to remaining)
                else
                    conditions!!.active - ConditionType.Block
                world.set(event.target, conditions.copy(active = updatedActive))
            }

            // 3. Apply to HP
            world.set(event.target, health.copy(current = (health.current - actual).coerceAtLeast(0)))
        }

        world.events().on<HealEvent> { event ->
            if (!world.isAlive(event.target)) return@on
            val health = world.get<HealthComponent>(event.target) ?: return@on
            world.set(event.target, health.copy(current = (health.current + event.amount).coerceAtMost(health.max)))
        }
    }

    override fun update(world: World, deltaMs: Long) = Unit
}
