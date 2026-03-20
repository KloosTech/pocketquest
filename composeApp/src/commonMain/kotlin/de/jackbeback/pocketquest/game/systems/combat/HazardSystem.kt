package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query

/**
 * Deals hazard damage to every unit standing on a [TileType.HAZARD] tile at the start of
 * the environment phase. Registered in [BattleSystemFactory] under [TurnPhase.EnvironmentPhase].
 */
class HazardSystem(
    private val tileMap: TileMap?,
    private val onLog: (String) -> Unit,
) : GameSystem {

    override fun update(world: World, deltaMs: Long) {
        val tm = tileMap ?: return
        world.query<PositionComponent>()
            .filter { (_, pos) -> tm.typeAt(pos.col, pos.row).hasHazard }
            .toList() // materialise to avoid ConcurrentModification during event dispatch
            .forEach { (id, pos) ->
                val dmg = tm.typeAt(pos.col, pos.row).hazardDamage
                val name = world.get<NameComponent>(id)?.displayName ?: "?"
                onLog("$name takes $dmg hazard damage.")
                world.events().emit(DamageEvent(id, id, dmg, DamageType.FIRE))
            }
    }
}
