package de.jackbeback.pocketquest.ecs.core

/** Marker interface for all game events. Open so subclasses can live in any package. */
interface GameEvent

/**
 * Two-phase event bus: systems emit events during [update], then [flush] dispatches them
 * to all registered handlers after each system tick. This prevents infinite loops and
 * makes event ordering deterministic.
 */
class EventBus {
    @PublishedApi internal val queue = ArrayDeque<GameEvent>()
    @PublishedApi internal val handlers = mutableListOf<(GameEvent) -> Unit>()

    /** Queue an event for dispatch on the next [flush]. */
    fun emit(event: GameEvent) {
        queue.add(event)
    }

    /** Drain the queue and call all matching handlers. Called by GameLoop after each system. */
    fun flush() {
        val snapshot = queue.toList()
        queue.clear()
        snapshot.forEach { event -> handlers.forEach { it(event) } }
    }

    /**
     * Register a persistent handler for events of type [T]. Called once during system
     * initialisation — the handler fires on every matching event during [flush].
     */
    inline fun <reified T : GameEvent> on(crossinline handler: (T) -> Unit) {
        handlers.add { event -> if (event is T) handler(event) }
    }
}
