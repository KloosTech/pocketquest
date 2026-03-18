package de.jackbeback.pocketquest.di

import de.jackbeback.pocketquest.content.definitions.RelicEffect
import de.jackbeback.pocketquest.content.definitions.allRelics
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
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.animation.AnimationEventCollector
import de.jackbeback.pocketquest.game.battle.buildBattleSystemRegistry
import de.jackbeback.pocketquest.game.loop.GameLoop
import de.jackbeback.pocketquest.game.overworld.OverworldEventRegistry
import de.jackbeback.pocketquest.game.run.RunStateHolder
import de.jackbeback.pocketquest.game.snapshot.BattleLog
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
    single { buildBattleSystemRegistry(get(), get(), get()) }
    single { GameLoop(get()) }
    single {
        val world          = get<World>()
        val battleLog      = get<BattleLog>()
        val runStateHolder = get<RunStateHolder>()
        BattleViewModel(
            world                = world,
            gameLoop             = get(),
            skillRegistry        = get(),
            battleLog            = battleLog,
            animCollector        = get(),
            tileCache            = get(),
            levelUpPreview       = { xp -> runStateHolder.previewGainExp(xp) },
            pickRelicCandidates  = {
                val heldIds = runStateHolder.run.value?.relics?.toSet() ?: emptySet()
                allRelics.filter { it.id !in heldIds }.shuffled().take(3)
            },
        ) { enemies ->
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
            val run = runStateHolder.run.value

            // Apply level-up max HP bonus (+5 per level above 1)
            val level = run?.level ?: 1
            if (level > 1) {
                val comp = world.get<HealthComponent>(playerId)
                if (comp != null) {
                    val newMax = comp.max + (level - 1) * 5
                    world.set(playerId, comp.copy(current = newMax, max = newMax))
                }
            }

            // Apply relic stat bonuses (BonusMaxHp, BonusMana — applied before HP/mana restore)
            val activeRelics = allRelics.filter { it.id in (run?.relics ?: emptyList()) }
            activeRelics.forEach { relic ->
                when (val effect = relic.effect) {
                    is RelicEffect.BonusMaxHp -> {
                        val comp = world.get<HealthComponent>(playerId) ?: return@forEach
                        val newMax = comp.max + effect.amount
                        world.set(playerId, comp.copy(max = newMax, current = newMax))
                    }
                    is RelicEffect.BonusMana -> {
                        val comp = world.get<ManaComponent>(playerId) ?: return@forEach
                        val newMax = comp.max + effect.amount
                        world.set(playerId, comp.copy(max = newMax, current = newMax))
                    }
                    else -> Unit
                }
            }

            // Restore saved HP/Mana (carry damage from previous encounters), capped to new max.
            // FullHpOnBattleStart / FullManaOnBattleStart relics skip the restore so player heals fully.
            val fullHp   = activeRelics.any { it.effect is RelicEffect.FullHpOnBattleStart }
            val fullMana = activeRelics.any { it.effect is RelicEffect.FullManaOnBattleStart }
            if (run != null) {
                if (!fullHp) {
                    run.playerHp?.let { savedHp ->
                        val comp = world.get<HealthComponent>(playerId) ?: return@let
                        world.set(playerId, comp.copy(current = savedHp.coerceIn(1, comp.max)))
                    }
                }
                if (!fullMana) {
                    run.playerMana?.let { savedMana ->
                        val comp = world.get<ManaComponent>(playerId) ?: return@let
                        world.set(playerId, comp.copy(current = savedMana.coerceIn(0, comp.max)))
                    }
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

