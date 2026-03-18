package de.jackbeback.pocketquest.di

import de.jackbeback.pocketquest.content.definitions.allSkills
import de.jackbeback.pocketquest.content.definitions.wizardTemplate
import de.jackbeback.pocketquest.content.dsl.spawnIntoWorld
import de.jackbeback.pocketquest.content.events.allOverworldEvents
import de.jackbeback.pocketquest.content.map.kaerMorhenConfig
import de.jackbeback.pocketquest.content.map.throneRoomConfig
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.game.battle.BattleTileCache
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.core.SystemRegistry
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.animation.AnimationEventCollector
import de.jackbeback.pocketquest.game.loop.GameLoop
import de.jackbeback.pocketquest.game.loop.TurnPhase
import de.jackbeback.pocketquest.game.overworld.OverworldEventRegistry
import de.jackbeback.pocketquest.game.run.RunStateHolder
import de.jackbeback.pocketquest.game.snapshot.BattleLog
import de.jackbeback.pocketquest.game.systems.ai.AIDecisionSystem
import de.jackbeback.pocketquest.game.systems.combat.CombatSystem
import de.jackbeback.pocketquest.game.systems.combat.ConditionApplySystem
import de.jackbeback.pocketquest.game.systems.combat.ConditionTickSystem
import de.jackbeback.pocketquest.game.systems.combat.DeathSystem
import de.jackbeback.pocketquest.game.systems.combat.SkillResolverSystem
import de.jackbeback.pocketquest.game.systems.combat.TurnResetSystem
import de.jackbeback.pocketquest.game.systems.movement.MovementSystem
import de.jackbeback.pocketquest.ui.battle.BattleViewModel
import de.jackbeback.pocketquest.ui.navigation.Navigator
import de.jackbeback.pocketquest.ui.overworld.OverworldViewModel
import org.koin.dsl.module

val gameModule = module {
    // World starts with only the player — enemies are spawned per-encounter
    single {
        World().also { world ->
            wizardTemplate.spawnIntoWorld(world)
        }
    }
    single { SkillRegistry(allSkills) }
    single { BattleLog() }
    single { Navigator() }
    single { RunStateHolder() }
    single { OverworldEventRegistry(allOverworldEvents) }
    // Pre-loads Throneroom tiles in the background; ready before battle starts
    single { BattleTileCache(throneRoomConfig) }
    single { AnimationEventCollector(get(), get()) }
    single { buildSystemRegistry(get(), get(), get()) }
    single { GameLoop(get()) }
    single {
        val world          = get<World>()
        val battleLog      = get<BattleLog>()
        val runStateHolder = get<RunStateHolder>()
        BattleViewModel(world, get(), get(), battleLog, get(), get()) { enemies ->
            // Save player HP/Mana before destroying so the run persists damage between encounters
            world.query<FactionComponent>()
                .filter { (_, f) -> f.faction == Faction.PLAYER }
                .firstOrNull()
                ?.let { (id, _) ->
                    val hp   = world.get<HealthComponent>(id)
                    val mana = world.get<ManaComponent>(id)
                    if (hp != null && mana != null) runStateHolder.savePlayerState(hp.current, mana.current)
                }

            // Destroy all entities and re-spawn the player
            world.allEntities().toList().forEach { world.destroyEntity(it) }
            world.flushDestroys()
            val playerId = wizardTemplate.spawnIntoWorld(world)

            // Restore saved HP/Mana (carry damage from previous encounters)
            val run = runStateHolder.run.value
            if (run != null) {
                run.playerHp?.let { savedHp ->
                    val comp = world.get<HealthComponent>(playerId) ?: return@let
                    world.set(playerId, comp.copy(current = savedHp.coerceIn(1, comp.max)))
                }
                run.playerMana?.let { savedMana ->
                    val comp = world.get<ManaComponent>(playerId) ?: return@let
                    world.set(playerId, comp.copy(current = savedMana.coerceIn(0, comp.max)))
                }
            }

            // Scale enemy HP by difficulty (30% per completed encounter)
            val diffMult = 1f + (run?.difficultyCounter ?: 0) * 0.30f
            enemies.forEach { template ->
                val enemyId = template.spawnIntoWorld(world)
                val comp = world.get<HealthComponent>(enemyId) ?: return@forEach
                val scaledHp = (comp.max * diffMult).toInt()
                world.set(enemyId, comp.copy(current = scaledHp, max = scaledHp))
            }

            battleLog.clear()
        }
    }
    single { OverworldViewModel(get(), get(), get(), kaerMorhenConfig, get()) }
}

private fun buildSystemRegistry(
    world: World,
    skillRegistry: SkillRegistry,
    battleLog: BattleLog
): SystemRegistry {
    val registry = SystemRegistry()
    val log = battleLog::add

    // Event-driven systems (handlers registered in constructor)
    val combatSystem        = CombatSystem(world)
    val movementSystem      = MovementSystem(world)
    val skillResolverSystem = SkillResolverSystem(world, skillRegistry, log)
    val conditionApplySystem = ConditionApplySystem(world, log)

    // Polling systems
    val turnResetSystem    = TurnResetSystem()
    val deathSystem        = DeathSystem(log)
    val aiSystem           = AIDecisionSystem(skillRegistry)
    val conditionTickSystem = ConditionTickSystem(log)

    // PlayerPhase: movement → skill resolution → combat → conditions → death
    // (No reset here — player MP persists across multiple actions within the same turn)
    registry.register(TurnPhase.PlayerPhase, movementSystem)
    registry.register(TurnPhase.PlayerPhase, skillResolverSystem)
    registry.register(TurnPhase.PlayerPhase, combatSystem)
    registry.register(TurnPhase.PlayerPhase, conditionApplySystem)
    registry.register(TurnPhase.PlayerPhase, deathSystem)

    // EnemyPhase: reset → AI (move + attack) → skill resolution → combat → conditions → death
    // MovementSystem is event-handler based — its on<MoveEvent> handler fires in any phase's flush()
    registry.register(TurnPhase.EnemyPhase, turnResetSystem)
    registry.register(TurnPhase.EnemyPhase, aiSystem)
    registry.register(TurnPhase.EnemyPhase, skillResolverSystem)
    registry.register(TurnPhase.EnemyPhase, combatSystem)
    registry.register(TurnPhase.EnemyPhase, conditionApplySystem)
    registry.register(TurnPhase.EnemyPhase, deathSystem)

    // EnvironmentPhase: condition tick → combat → conditions → death
    registry.register(TurnPhase.EnvironmentPhase, conditionTickSystem)
    registry.register(TurnPhase.EnvironmentPhase, combatSystem)
    registry.register(TurnPhase.EnvironmentPhase, conditionApplySystem)
    registry.register(TurnPhase.EnvironmentPhase, deathSystem)

    return registry
}
