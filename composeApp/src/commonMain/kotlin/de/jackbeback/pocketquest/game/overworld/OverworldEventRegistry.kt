package de.jackbeback.pocketquest.game.overworld

import de.jackbeback.pocketquest.content.events.OverworldEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds all active overworld events for a session.
 *
 * Events are keyed by [OverworldEvent.id]. Calling [complete] removes the event
 * permanently so its map marker disappears and it cannot be triggered again.
 *
 * The [active] flow lets the UI react to changes (e.g. marker removal).
 */
class OverworldEventRegistry(events: List<OverworldEvent>) {
    private val _active = MutableStateFlow(events.associateBy { it.id })
    val active: StateFlow<Map<String, OverworldEvent>> = _active

    fun complete(eventId: String) {
        _active.value = _active.value - eventId
    }

    /** Resets the registry to [events] — call at the start of each new run. */
    fun reset(events: List<OverworldEvent>) {
        _active.value = events.associateBy { it.id }
    }

    fun eventsForMap(mapId: String): List<OverworldEvent> =
        _active.value.values.filter { it.mapId == mapId }

    val activeCount: Int get() = _active.value.size
}
