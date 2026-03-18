package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.ecs.components.combat.ConditionAppliedEvent
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set

/**
 * Handles [ConditionAppliedEvent]: adds or increments stacks in the target's [ConditionsComponent].
 */
class ConditionApplySystem(
    world: World,
    private val onLog: (String) -> Unit = {}
) : GameSystem {

    init {
        world.events().on<ConditionAppliedEvent> { event ->
            if (!world.isAlive(event.target)) return@on
            val existing = world.get<ConditionsComponent>(event.target) ?: ConditionsComponent()
            val newStacks = (existing.active[event.type] ?: 0) + event.stacks
            world.set(event.target, existing.copy(active = existing.active + (event.type to newStacks)))
        }
    }

    override fun update(world: World, deltaMs: Long) = Unit
}
