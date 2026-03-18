package de.jackbeback.pocketquest.game.loop

import de.jackbeback.pocketquest.ecs.components.combat.MoveEvent
import de.jackbeback.pocketquest.ecs.components.combat.SkillUsedEvent
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.SystemRegistry
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.query

/**
 * Orchestrates system execution across all [TurnPhase]s.
 *
 * Player actions are translated to events, then phases run in order:
 * PlayerPhase → EnemyPhase → EnvironmentPhase, with [World.flushDestroys] after each.
 */
class GameLoop(private val systemRegistry: SystemRegistry) {

    private fun getActivePlayer(world: World): EntityId =
        world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()?.first ?: EntityId.INVALID

    fun runPlayerTurn(world: World, action: PlayerAction) {
        val playerId = getActivePlayer(world)
        when (action) {
            is PlayerAction.MoveTo ->
                world.events().emit(
                    MoveEvent(entity = playerId, to = PositionComponent(col = action.col, row = action.row))
                )
            is PlayerAction.UseSkill ->
                world.events().emit(
                    SkillUsedEvent(user = playerId, skillId = action.skillId, target = action.targetId)
                )
            is PlayerAction.UseSkillOnTargets ->
                action.targetIds.forEach { targetId ->
                    world.events().emit(
                        SkillUsedEvent(user = playerId, skillId = action.skillId, target = targetId)
                    )
                }
            is PlayerAction.EndTurn -> Unit
        }
        runSystems(world, TurnPhase.PlayerPhase)
        world.flushDestroys()
    }

    fun runEnemyTurns(world: World) {
        runSystems(world, TurnPhase.EnemyPhase)
        world.flushDestroys()
    }

    fun runEnvironmentTick(world: World) {
        runSystems(world, TurnPhase.EnvironmentPhase)
        world.flushDestroys()
    }

    private fun runSystems(world: World, phase: TurnPhase) {
        systemRegistry.systemsFor(phase).forEach { system ->
            system.update(world, 0L)
            world.events().flush()
        }
    }
}
