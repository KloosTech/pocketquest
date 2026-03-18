package de.jackbeback.pocketquest.game.systems.combat

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
 * Damage is multiplied by the target's [DamageResistancesComponent] before being applied.
 */
class CombatSystem(world: World) : GameSystem {
    init {
        world.events().on<DamageEvent> { event ->
            if (!world.isAlive(event.target)) return@on
            val health = world.get<HealthComponent>(event.target) ?: return@on
            val resistance = world.get<DamageResistancesComponent>(event.target)
                ?.multipliers?.get(event.damageType) ?: 1.0f
            val actual = (event.amount * resistance).toInt().coerceAtLeast(0)
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
