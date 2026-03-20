package de.jackbeback.pocketquest.game.battle

import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.core.SystemRegistry
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.game.loop.TurnPhase
import de.jackbeback.pocketquest.game.snapshot.BattleLog
import de.jackbeback.pocketquest.game.systems.ai.AIDecisionSystem
import de.jackbeback.pocketquest.game.systems.combat.CombatSystem
import de.jackbeback.pocketquest.game.systems.combat.ConditionApplySystem
import de.jackbeback.pocketquest.game.systems.combat.ConditionTickSystem
import de.jackbeback.pocketquest.game.systems.combat.DeathSystem
import de.jackbeback.pocketquest.game.systems.combat.HazardSystem
import de.jackbeback.pocketquest.game.systems.combat.SkillResolverSystem
import de.jackbeback.pocketquest.game.systems.combat.TurnResetSystem
import de.jackbeback.pocketquest.game.systems.movement.MovementSystem

/** Builds a fully wired [SystemRegistry] for a battle world. Used by both DI and the designer. */
fun buildBattleSystemRegistry(
    world: World,
    skillRegistry: SkillRegistry,
    battleLog: BattleLog,
    tileMap: TileMap? = null,
): SystemRegistry {
    val registry = SystemRegistry()
    val log = battleLog::add

    val combatSystem         = CombatSystem(world)
    val movementSystem       = MovementSystem(world, tileMap)
    val skillResolverSystem  = SkillResolverSystem(world, skillRegistry, log, tileMap)
    val conditionApplySystem = ConditionApplySystem(world, log)
    val turnResetSystem      = TurnResetSystem()
    val deathSystem          = DeathSystem(log)
    val aiSystem             = AIDecisionSystem(skillRegistry, tileMap)
    val conditionTickSystem  = ConditionTickSystem(log)
    val hazardSystem         = HazardSystem(tileMap, log)

    registry.register(TurnPhase.PlayerPhase, movementSystem)
    registry.register(TurnPhase.PlayerPhase, skillResolverSystem)
    registry.register(TurnPhase.PlayerPhase, combatSystem)
    registry.register(TurnPhase.PlayerPhase, conditionApplySystem)
    registry.register(TurnPhase.PlayerPhase, deathSystem)

    registry.register(TurnPhase.EnemyPhase, turnResetSystem)
    registry.register(TurnPhase.EnemyPhase, aiSystem)
    registry.register(TurnPhase.EnemyPhase, skillResolverSystem)
    registry.register(TurnPhase.EnemyPhase, combatSystem)
    registry.register(TurnPhase.EnemyPhase, conditionApplySystem)
    registry.register(TurnPhase.EnemyPhase, deathSystem)

    registry.register(TurnPhase.EnvironmentPhase, hazardSystem)
    registry.register(TurnPhase.EnvironmentPhase, conditionTickSystem)
    registry.register(TurnPhase.EnvironmentPhase, combatSystem)
    registry.register(TurnPhase.EnvironmentPhase, conditionApplySystem)
    registry.register(TurnPhase.EnvironmentPhase, deathSystem)

    return registry
}
