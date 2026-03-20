package de.jackbeback.pocketquest.game.systems.movement

import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.ecs.components.combat.MoveEvent
import de.jackbeback.pocketquest.ecs.components.core.MovementPointsComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.battle.chebyshevDistance

/**
 * Handles [MoveEvent]s.
 *
 * Each move action costs [TileType.movementCost] from [MovementPointsComponent.current].
 * The hop must be within [MovementPointsComponent.range] tiles (Chebyshev).
 * When [current] reaches 0 no further moves are allowed until [TurnResetSystem] restores it.
 * Non-walkable tiles (walls, water, void) block movement entirely.
 */
class MovementSystem(world: World, private val tileMap: TileMap? = null) : GameSystem {
    init {
        world.events().on<MoveEvent> { event ->
            if (!world.isAlive(event.entity)) return@on
            val mp   = world.get<MovementPointsComponent>(event.entity) ?: return@on
            if (mp.current <= 0) return@on
            val from = world.get<PositionComponent>(event.entity) ?: return@on
            if (chebyshevDistance(from, event.to) > mp.range) return@on

            // Reject move to non-walkable terrain
            if (tileMap?.isWalkable(event.to.col, event.to.row) == false) return@on

            // Reject move if destination is already occupied by another entity
            val occupied = world.query<PositionComponent>()
                .any { (id, pos) -> id != event.entity && pos == event.to }
            if (occupied) return@on

            val terrainCost = tileMap?.typeAt(event.to.col, event.to.row)?.movementCost ?: 1
            if (mp.current < terrainCost) return@on

            world.set(event.entity, event.to)
            world.set(event.entity, mp.copy(current = mp.current - terrainCost))
        }
    }

    override fun update(world: World, deltaMs: Long) = Unit
}
