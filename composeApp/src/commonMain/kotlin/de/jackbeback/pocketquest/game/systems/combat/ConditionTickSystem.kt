package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.set

/**
 * Runs during EnvironmentPhase: ticks all active conditions, emits damage events,
 * and decrements stacks (removes when they reach 0).
 */
class ConditionTickSystem(private val onLog: (String) -> Unit = {}) : GameSystem {

    override fun update(world: World, deltaMs: Long) {
        world.query<ConditionsComponent>()
            .filter { (_, c) -> c.active.isNotEmpty() }
            .toList()
            .forEach { (id, conditions) ->
                tickConditions(world, id, conditions)
            }
    }

    private fun tickConditions(world: World, id: EntityId, conditions: ConditionsComponent) {
        val name = world.get<NameComponent>(id)?.displayName ?: "?"
        val updated = mutableMapOf<ConditionType, Int>()

        for ((type, stacks) in conditions.active) {
            val (damage, damageType) = when (type) {
                ConditionType.Burn   -> stacks * 2 to DamageType.FIRE
                ConditionType.Poison -> stacks * 1 to DamageType.POISON
                ConditionType.Cold   -> stacks * 1 to DamageType.FORCE
            }
            onLog("$name suffers ${type.name} ($damage ${damageType.name} damage, $stacks stacks).")
            world.events().emit(DamageEvent(source = id, target = id, amount = damage, damageType = damageType))

            val remaining = stacks - 1
            if (remaining > 0) updated[type] = remaining
        }

        world.set(id, conditions.copy(active = updated))
    }
}
