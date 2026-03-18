package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.ecs.components.combat.DeathEvent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query

/**
 * Scans all entities with [HealthComponent] ≤ 0, emits a [DeathEvent], and schedules them
 * for removal via [World.destroyEntity]. Actual removal happens on [World.flushDestroys].
 */
class DeathSystem(private val onLog: (String) -> Unit = {}) : GameSystem {
    override fun update(world: World, deltaMs: Long) {
        world.query<HealthComponent>()
            .filter { (_, health) -> health.current <= 0 }
            .map { (id, _) -> id }
            .toList() // collect before mutating world
            .forEach { id ->
                val name = world.get<NameComponent>(id)?.displayName ?: "Unknown"
                onLog("$name has fallen.")
                world.events().emit(DeathEvent(entity = id))
                world.destroyEntity(id)
            }
    }
}
