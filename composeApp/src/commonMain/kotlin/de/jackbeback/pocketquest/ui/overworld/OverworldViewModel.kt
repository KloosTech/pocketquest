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
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.game.overworld.OverworldEventRegistry
import de.jackbeback.pocketquest.game.run.RunStateHolder
import de.jackbeback.pocketquest.game.run.RunScopedState
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
    private val runStateHolder: RunStateHolder,
) {
    val mapState: MapState = buildMapState(mapConfig)

    private val _state = MutableStateFlow(world.snapshotOverworld())
    val state: StateFlow<OverworldUiState> = _state

    /** Current roguelike run state; null between runs. */
    val runState: StateFlow<RunScopedState?> = runStateHolder.run

    /** Number of uncleared events remaining on this map. */
    private val _eventsRemaining = MutableStateFlow(eventRegistry.activeCount)
    val eventsRemaining: StateFlow<Int> = _eventsRemaining

    /** Non-null when the player tapped a RestSite marker and hasn't confirmed/dismissed yet. */
    private val _pendingRest = MutableStateFlow<OverworldEvent.RestSite?>(null)
    val pendingRest: StateFlow<OverworldEvent.RestSite?> = _pendingRest

    init {
        syncUnitMarkersToMap()
        syncEventMarkersToMap()

        mapState.onMarkerClick { id, _, _ ->
            val event = eventRegistry.active.value[id]
            when (event) {
                is OverworldEvent.BattleEncounter -> {
                    navigator.goToBattle(BattleParams(eventId = event.id, enemies = event.enemies))
                }
                is OverworldEvent.RestSite -> {
                    _pendingRest.value = event
                }
                null -> { /* unit marker or unknown — ignore */ }
            }
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
        _state.value = world.snapshotOverworld()
        _eventsRemaining.value = eventRegistry.activeCount
    }

    /** Player confirmed resting at the pending rest site — apply heal and remove marker. */
    fun onRestConfirmed() {
        val event = _pendingRest.value ?: return
        world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()
            ?.let { (id, _) ->
                val hp = world.get<HealthComponent>(id) ?: return@let
                val healAmount = (hp.max * event.healPercent).toInt().coerceAtLeast(1)
                val newHp = (hp.current + healAmount).coerceAtMost(hp.max)
                world.set(id, hp.copy(current = newHp))
                val mana = world.get<ManaComponent>(id)?.current ?: 0
                runStateHolder.savePlayerState(newHp, mana)
            }
        eventRegistry.complete(event.id)
        mapState.removeMarker(event.id)
        _pendingRest.value = null
        _state.value = world.snapshotOverworld()
        _eventsRemaining.value = eventRegistry.activeCount
    }

    /** Player dismissed the rest dialog without resting. */
    fun onRestDismissed() {
        _pendingRest.value = null
    }

    /**
     * Called by [App] when a new run starts.
     * Re-adds all event markers and refreshes the player marker.
     */
    fun onRunReset(events: List<OverworldEvent>) {
        // Remove stale event markers from previous run (best-effort — ignore unknown ids)
        eventRegistry.active.value.keys.forEach { id -> runCatching { mapState.removeMarker(id) } }
        events.filter { it.mapId == mapConfig.id }.forEach { event ->
            mapState.addMarker(event.id, event.x, event.y) { EventMapMarker(event) }
        }
        _state.value = world.snapshotOverworld()
        _eventsRemaining.value = events.count { it.mapId == mapConfig.id }
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
