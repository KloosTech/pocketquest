package de.jackbeback.pocketquest.ui.overworld

import de.jackbeback.pocketquest.content.events.OverworldEvent
import de.jackbeback.pocketquest.content.map.MapConfig
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.map.MapLocationComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.overworld.OverworldEventRegistry
import de.jackbeback.pocketquest.game.snapshot.snapshotOverworld
import de.jackbeback.pocketquest.ui.navigation.BattleParams
import de.jackbeback.pocketquest.ui.navigation.Navigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.Buffer
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.moveMarker
import ovh.plrapps.mapcompose.api.onMarkerClick
import ovh.plrapps.mapcompose.api.onTap
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.state.MapState
import pocketquest.composeapp.generated.resources.Res

class OverworldViewModel(
    private val world: World,
    private val navigator: Navigator,
    private val eventRegistry: OverworldEventRegistry,
    private val mapConfig: MapConfig,
) {
    val mapState: MapState = buildMapState(mapConfig)

    private val _state = MutableStateFlow(world.snapshotOverworld())
    val state: StateFlow<OverworldUiState> = _state

    init {
        syncUnitMarkersToMap()
        syncEventMarkersToMap()

        mapState.onMarkerClick { id, _, _ ->
            // Event markers take priority
            val event = eventRegistry.active.value[id]
            if (event is OverworldEvent.BattleEncounter) {
                navigator.goToBattle(BattleParams(eventId = event.id, enemies = event.enemies))
                return@onMarkerClick
            }
            // Unit marker clicks (player) are currently ignored
        }

        mapState.onTap { x, y -> movePlayer(x, y) }
    }

    /**
     * Called by [App] after the battle screen reports victory.
     * Completes the event that triggered the battle and removes its map marker.
     */
    fun onBattleCompleted() {
        val eventId = navigator.currentBattle?.eventId ?: return
        eventRegistry.complete(eventId)
        mapState.removeMarker(eventId)
    }

    private fun movePlayer(x: Double, y: Double) {
        world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()
            ?.let { (id, _) ->
                val mapId = world.get<MapLocationComponent>(id)?.mapId ?: mapConfig.id
                world.set(id, MapLocationComponent(mapId, x, y))
                mapState.moveMarker(id.id.toString(), x, y)
            }
        _state.value = world.snapshotOverworld()
    }

    private fun syncUnitMarkersToMap() {
        _state.value.units.forEach { unit ->
            mapState.addMarker(unit.entityId.id.toString(), unit.mapX, unit.mapY) {
                UnitMapMarker(unit)
            }
        }
    }

    private fun syncEventMarkersToMap() {
        eventRegistry.eventsForMap(mapConfig.id).forEach { event ->
            mapState.addMarker(event.id, event.x, event.y) {
                EventMapMarker(event)
            }
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
private fun buildMapState(config: MapConfig): MapState {
    val tileSize = minOf(config.tileWidth, config.tileHeight)
    val state = MapState(
        levelCount  = config.levelCount,
        fullWidth   = config.fullWidthPx,
        fullHeight  = config.fullHeightPx,
        tileSize    = tileSize,
    )
    val provider = TileStreamProvider { row, col, _ ->
        try {
            val bytes = Res.readBytes("files/tiles/${config.id}/$col-$row.png")
            Buffer().also { it.write(bytes) }
        } catch (e: Exception) {
            null
        }
    }
    state.addLayer(provider)
    return state
}
