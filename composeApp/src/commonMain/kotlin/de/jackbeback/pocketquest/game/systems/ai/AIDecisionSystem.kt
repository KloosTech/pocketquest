package de.jackbeback.pocketquest.game.systems.ai

import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.MoveEvent
import de.jackbeback.pocketquest.ecs.components.combat.SkillUsedEvent
import de.jackbeback.pocketquest.ecs.components.core.AIComponent
import de.jackbeback.pocketquest.ecs.components.core.AIStrategy
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.MovementPointsComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.components.core.TurnStateComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS
import de.jackbeback.pocketquest.game.battle.chebyshevDistance
import de.jackbeback.pocketquest.game.pathfinding.findPath
import de.jackbeback.pocketquest.game.pathfinding.hasLineOfSight

/**
 * Positional enemy AI using A* pathfinding.
 *
 * Each Aggressive enemy:
 *  1. Selects the nearest walkable attack position adjacent to the player.
 *  2. Walks the A* path using all available movement points.
 *  3. Attacks the player if in range after movement.
 */
class AIDecisionSystem(
    private val skillRegistry: SkillRegistry,
    private val tileMap: TileMap? = null,
) : GameSystem {

    private val gridCols: Int get() = tileMap?.cols ?: BATTLE_COLS
    private val gridRows: Int get() = tileMap?.rows ?: BATTLE_ROWS

    override fun update(world: World, deltaMs: Long) {
        val playerEntity = world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()?.first ?: return
        if (!world.isAlive(playerEntity)) return

        val playerPos = world.get<PositionComponent>(playerEntity) ?: return

        val allPositions = world.query<PositionComponent>()
            .associate { (id, pos) -> id to pos }

        world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.ENEMY }
            .toList()
            .forEach { (enemyId, _) ->
                if (!world.isAlive(enemyId)) return@forEach
                val ai = world.get<AIComponent>(enemyId) ?: return@forEach
                if (ai.strategy != AIStrategy.Aggressive) return@forEach
                val turnState = world.get<TurnStateComponent>(enemyId) ?: return@forEach
                val mp = world.get<MovementPointsComponent>(enemyId) ?: return@forEach

                val enemyPos = world.get<PositionComponent>(enemyId) ?: return@forEach
                val attackRange = skillRegistry.find(ai.preferredSkillId)?.range ?: 1

                // --- Move phase ---
                var expectedPos = enemyPos
                if (mp.current > 0 && chebyshevDistance(enemyPos, playerPos) > attackRange) {
                    val occupied = allPositions
                        .filter { (id, _) -> id != enemyId }
                        .values.map { it.col to it.row }.toSet()

                    val isBlocked = { c: Int, r: Int ->
                        c !in 0 until gridCols || r !in 0 until gridRows
                            || tileMap?.isWalkable(c, r) == false
                            || (c to r) in occupied
                    }

                    val target = selectMoveTarget(
                        enemyPos, playerPos, attackRange,
                        gridCols, gridRows, isBlocked,
                    )

                    if (target != enemyPos) {
                        val path = findPath(
                            from = enemyPos,
                            to = target,
                            gridCols = gridCols,
                            gridRows = gridRows,
                            isBlocked = { c, r ->
                                // Allow the target cell even if player occupies it
                                if (c == target.col && r == target.row) false
                                else isBlocked(c, r)
                            },
                            moveCost = { c, r -> tileMap?.typeAt(c, r)?.movementCost ?: 1 },
                        )
                        if (path != null && path.isNotEmpty()) {
                            walkPath(enemyId, enemyPos, path, mp) { world.events().emit(it) }
                            expectedPos = path.last()
                        }
                    }
                }

                // --- Attack phase ---
                val distAfterMove = chebyshevDistance(expectedPos, playerPos)
                val hasLos = hasLineOfSight(expectedPos, playerPos, tileMap)
                if (!turnState.hasActed && distAfterMove <= attackRange && hasLos) {
                    world.events().emit(
                        SkillUsedEvent(user = enemyId, skillId = ai.preferredSkillId, target = playerEntity)
                    )
                }
            }
    }

    /**
     * Finds the best cell within [attackRange] of [playerPos] for the enemy to stand on.
     * Returns the closest such walkable cell to [enemyPos], or [playerPos] as fallback.
     */
    private fun selectMoveTarget(
        enemyPos: PositionComponent,
        playerPos: PositionComponent,
        attackRange: Int,
        gridCols: Int,
        gridRows: Int,
        isBlocked: (Int, Int) -> Boolean,
    ): PositionComponent {
        if (chebyshevDistance(enemyPos, playerPos) <= attackRange) return enemyPos

        var bestCandidate: PositionComponent? = null
        var bestDist = Int.MAX_VALUE

        for (dc in -attackRange..attackRange) {
            for (dr in -attackRange..attackRange) {
                val c = playerPos.col + dc
                val r = playerPos.row + dr
                if (c !in 0 until gridCols || r !in 0 until gridRows) continue
                if (isBlocked(c, r)) continue
                val candidate = PositionComponent(c, r)
                val dist = chebyshevDistance(enemyPos, candidate)
                if (dist < bestDist) {
                    bestDist = dist
                    bestCandidate = candidate
                }
            }
        }

        return bestCandidate ?: playerPos
    }

    /**
     * Walks the given [path] by emitting [MoveEvent]s, consuming MP each step.
     * Handles both long-range hops (grunt: range=3) and short multi-step walkers (wizard: range=1).
     */
    private fun walkPath(
        entityId: EntityId,
        startPos: PositionComponent,
        path: List<PositionComponent>,
        mp: MovementPointsComponent,
        emit: (MoveEvent) -> Unit,
    ) {
        var remainingMp = mp.current
        var currentPos = startPos
        var pathOffset = 0

        while (remainingMp > 0 && pathOffset < path.size) {
            // Find the farthest path cell within mp.range Chebyshev of currentPos
            var targetIdx = -1
            for (i in pathOffset until path.size) {
                if (chebyshevDistance(currentPos, path[i]) <= mp.range) {
                    targetIdx = i
                } else {
                    break
                }
            }
            if (targetIdx == -1) break

            val nextPos = path[targetIdx]
            val terrainCost = tileMap?.typeAt(nextPos.col, nextPos.row)?.movementCost ?: 1
            if (remainingMp < terrainCost) break

            emit(MoveEvent(entity = entityId, to = nextPos))
            remainingMp -= terrainCost
            currentPos = nextPos
            pathOffset = targetIdx + 1
        }
    }
}
