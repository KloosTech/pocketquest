package de.jackbeback.pocketquest.game.snapshot

import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.components.core.RenderComponent
import de.jackbeback.pocketquest.ecs.components.map.MapLocationComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ui.overworld.OverworldUiState
import de.jackbeback.pocketquest.ui.overworld.OverworldUnitUiState

fun World.snapshotOverworld(): OverworldUiState {
    val units = query<NameComponent, FactionComponent>()
        .mapNotNull { (id, name, faction) ->
            val loc = get<MapLocationComponent>(id) ?: return@mapNotNull null
            OverworldUnitUiState(
                entityId = id,
                name = name.displayName,
                faction = faction.faction,
                mapX = loc.x,
                mapY = loc.y,
                spriteKey = get<RenderComponent>(id)?.spriteKey ?: "",
                health = get<HealthComponent>(id) ?: HealthComponent(0, 0),
                mana = get<ManaComponent>(id) ?: ManaComponent(0, 0),
            )
        }
        .toList()
    return OverworldUiState(units)
}
