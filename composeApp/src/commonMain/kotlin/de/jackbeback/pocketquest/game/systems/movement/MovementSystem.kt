package de.jackbeback.pocketquest.game.systems.movement

import de.jackbeback.pocketquest.ecs.components.combat.MoveEvent
import de.jackbeback.pocketquest.ecs.components.core.MovementPointsComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.battle.chebyshevDistance

/**
 * Handles [MoveEvent]s.
 *
 * Each move action costs 1 from [MovementPointsComponent.current].
 * The hop must be within [MovementPointsComponent.range] tiles (Chebyshev).
 * When [current] reaches 0 no further moves are allowed until [TurnResetSystem] restores it.
 */
class MovementSystem(world: World) : GameSystem {
    init {
        world.events().on<MoveEvent> { event ->
            if (!world.isAlive(event.entity)) return@on
            val mp   = world.get<MovementPointsComponent>(event.entity) ?: return@on
            if (mp.current <= 0) return@on
            val from = world.get<PositionComponent>(event.entity) ?: return@on
            if (chebyshevDistance(from, event.to) > mp.range) return@on

            world.set(event.entity, event.to)
            world.set(event.entity, mp.copy(current = mp.current - 1))
        }
    }

    override fun update(world: World, deltaMs: Long) = Unit
}
