package de.jackbeback.pocketquest.ui.overworld

import de.jackbeback.pocketquest.content.events.MapGraph
import de.jackbeback.pocketquest.content.events.OverworldEvent
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.hint.HintDefinition
import de.jackbeback.pocketquest.game.hint.HintManager
import de.jackbeback.pocketquest.game.overworld.OverworldEventRegistry
import de.jackbeback.pocketquest.game.run.RunScopedState
import de.jackbeback.pocketquest.game.run.RunStateHolder
import de.jackbeback.pocketquest.ui.navigation.BattleParams
import de.jackbeback.pocketquest.ui.navigation.Navigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OverworldViewModel(
    private val world: World,
    private val navigator: Navigator,
    private val eventRegistry: OverworldEventRegistry,
    private val runStateHolder: RunStateHolder,
    private val hintManager: HintManager? = null,
    /** The progression graph for this area. */
    val graph: MapGraph,
    /** All events keyed by their id — includes the start node and all encounters/rests. */
    val allEvents: Map<String, OverworldEvent>,
) {
    val runState: StateFlow<RunScopedState?> = runStateHolder.run

    private val _eventsRemaining = MutableStateFlow(eventRegistry.activeCount)
    val eventsRemaining: StateFlow<Int> = _eventsRemaining

    private val _pendingRest = MutableStateFlow<OverworldEvent.RestSite?>(null)
    val pendingRest: StateFlow<OverworldEvent.RestSite?> = _pendingRest

    private val _mapCleared = MutableStateFlow(false)
    val mapCleared: StateFlow<Boolean> = _mapCleared

    private val _playerHealth = MutableStateFlow(queryPlayerHealth())
    val playerHealth: StateFlow<HealthComponent> = _playerHealth

    private val _playerMana = MutableStateFlow(queryPlayerMana())
    val playerMana: StateFlow<ManaComponent> = _playerMana

    // ── Hint system ───────────────────────────────────────────────────────────
    private val hintQueue = ArrayDeque<HintDefinition>()
    private val _pendingHint = MutableStateFlow<HintDefinition?>(null)
    val pendingHint: StateFlow<HintDefinition?> = _pendingHint

    fun dismissHint() {
        _pendingHint.value = hintQueue.removeFirstOrNull()
    }

    private fun enqueueHint(id: String) {
        val hint = hintManager?.tryShow(id) ?: return
        if (_pendingHint.value == null) {
            _pendingHint.value = hint
        } else {
            hintQueue += hint
        }
    }

    init {
        enqueueHint("hint_overworld")
    }

    /**
     * Called by the UI when the player taps a graph node.
     * Only fires when the node is reachable (in the forward edges of the current node
     * AND the current node has been completed).
     */
    fun onNodeTap(nodeId: String) {
        val run = runStateHolder.run.value ?: return
        val available = if (run.currentNodeId in run.completedNodeIds)
            graph.nextOf(run.currentNodeId).toSet() else emptySet()
        if (nodeId !in available) return

        val event = allEvents[nodeId] ?: return
        runStateHolder.moveToNode(nodeId)

        when (event) {
            is OverworldEvent.BattleEncounter -> {
                if (event.isBoss) enqueueHint("hint_boss")
                navigator.goToBattle(BattleParams(eventId = event.id, enemies = event.enemies))
            }
            is OverworldEvent.RestSite -> {
                enqueueHint("hint_rest")
                _pendingRest.value = event
            }
            is OverworldEvent.StartNode -> Unit
        }
    }

    /**
     * Called by [App] after a battle screen reports victory.
     * Marks the completed event node and checks for map clear.
     */
    fun onBattleCompleted() {
        val eventId = navigator.currentBattle?.eventId ?: return
        runStateHolder.completeNode(eventId)
        eventRegistry.complete(eventId)
        _eventsRemaining.value = eventRegistry.activeCount
        _playerHealth.value = queryPlayerHealth()
        _playerMana.value   = queryPlayerMana()
        enqueueHint("hint_conditions")
        checkMapCleared()
    }

    /** Player confirmed resting — apply heal and complete the node. */
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
        completeRestNode(event)
    }

    /** Player dismissed the rest dialog without resting — still completes the node. */
    fun onRestDismissed() {
        val event = _pendingRest.value ?: run { _pendingRest.value = null; return }
        completeRestNode(event)
    }

    private fun completeRestNode(event: OverworldEvent.RestSite) {
        runStateHolder.completeNode(event.id)
        eventRegistry.complete(event.id)
        _pendingRest.value = null
        _eventsRemaining.value = eventRegistry.activeCount
        _playerHealth.value = queryPlayerHealth()
        _playerMana.value   = queryPlayerMana()
        checkMapCleared()
    }

    fun dismissMapClear() {
        _mapCleared.value = false
    }

    /** Called when a new run starts or the next area begins. */
    fun onRunReset() {
        _eventsRemaining.value = eventRegistry.activeCount
        _mapCleared.value = false
        _playerHealth.value = queryPlayerHealth()
        _playerMana.value   = queryPlayerMana()
    }

    private fun checkMapCleared() {
        if (eventRegistry.activeCount == 0) _mapCleared.value = true
    }

    private fun queryPlayerHealth(): HealthComponent =
        world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()
            ?.let { (id, _) -> world.get<HealthComponent>(id) }
            ?: HealthComponent(0, 0)

    private fun queryPlayerMana(): ManaComponent =
        world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()
            ?.let { (id, _) -> world.get<ManaComponent>(id) }
            ?: ManaComponent(0, 0)
}
