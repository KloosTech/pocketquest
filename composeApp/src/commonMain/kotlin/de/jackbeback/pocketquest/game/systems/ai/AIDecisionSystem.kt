package de.jackbeback.pocketquest.game.systems.ai

import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.MoveEvent
import de.jackbeback.pocketquest.ecs.components.combat.SkillUsedEvent
import de.jackbeback.pocketquest.ecs.components.core.AIComponent
import de.jackbeback.pocketquest.ecs.components.core.AIStrategy
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.MovementPointsComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.components.core.TurnStateComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.game.battle.chebyshevDistance
import de.jackbeback.pocketquest.game.battle.clampToGrid
import kotlin.math.sign

/**
 * Positional enemy AI.
 *
 * Each Aggressive enemy:
 *  1. Moves toward the player if not already in attack range (one move per turn).
 *  2. Attacks the player if in range (Chebyshev distance <= skill range).
 *
 * [SkillRegistry] is injected so the AI can check the attack range of the
 * skill it wants to use without hardcoding it.
 */
class AIDecisionSystem(private val skillRegistry: SkillRegistry) : GameSystem {

    override fun update(world: World, deltaMs: Long) {
        val playerEntity = world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()?.first ?: return
        if (!world.isAlive(playerEntity)) return

        val playerPos = world.get<PositionComponent>(playerEntity) ?: return

        world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.ENEMY }
            .toList()
            .forEach { (enemyId, _) ->
                if (!world.isAlive(enemyId)) return@forEach
                val ai = world.get<AIComponent>(enemyId) ?: return@forEach
                if (ai.strategy != AIStrategy.Aggressive) return@forEach
                val turnState = world.get<TurnStateComponent>(enemyId) ?: return@forEach
                val mp = world.get<MovementPointsComponent>(enemyId)

                val enemyPos = world.get<PositionComponent>(enemyId) ?: return@forEach
                val attackSkillId = ai.preferredSkillId
                val attackRange = skillRegistry.find(attackSkillId)?.range ?: 1

                val distToPlayer = chebyshevDistance(enemyPos, playerPos)

                // --- Move phase (use all available move actions to close the gap) ---
                val canMove = (mp?.current ?: 0) > 0
                if (canMove && distToPlayer > attackRange) {
                    val moveRange = mp?.range ?: 1
                    val targetPos = moveToward(enemyPos, playerPos, moveRange)
                    world.events().emit(MoveEvent(entity = enemyId, to = targetPos))
                }

                // --- Attack phase ---
                // Predict position after the pending move to decide if attack is in range
                val expectedPos = if (canMove && distToPlayer > attackRange) {
                    val moveRange = mp?.range ?: 1
                    moveToward(enemyPos, playerPos, moveRange)
                } else {
                    enemyPos
                }
                val distAfterMove = chebyshevDistance(expectedPos, playerPos)
                if (!turnState.hasActed && distAfterMove <= attackRange) {
                    world.events().emit(
                        SkillUsedEvent(user = enemyId, skillId = attackSkillId, target = playerEntity)
                    )
                }
            }
    }

    /**
     * Step from [from] toward [to] by at most [maxRange] tiles using diagonal movement.
     * Each step moves one tile diagonally/orthogonally until maxRange is exhausted or
     * the target is reached.
     */
    private fun moveToward(from: PositionComponent, to: PositionComponent, maxRange: Int): PositionComponent {
        var col = from.col
        var row = from.row
        repeat(maxRange) {
            if (col == to.col && row == to.row) return@repeat
            col += (to.col - col).sign
            row += (to.row - row).sign
        }
        return PositionComponent(col, row).clampToGrid()
    }
}
