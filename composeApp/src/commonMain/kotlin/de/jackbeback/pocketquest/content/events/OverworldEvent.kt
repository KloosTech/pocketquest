package de.jackbeback.pocketquest.content.events

import de.jackbeback.pocketquest.content.dsl.UnitTemplate

/**
 * Describes a point-of-interest on the overworld map.
 * Each event has a unique [id], a position on a specific [mapId], and a visual [label].
 * When an event is completed it is removed from the [OverworldEventRegistry] and its marker
 * disappears from the map.
 *
 * Add new event types by adding subclasses here — the rest of the system picks them up automatically.
 */
sealed class OverworldEvent {
    abstract val id: String
    abstract val mapId: String
    /** Normalised x position (0.0–1.0) on the map. */
    abstract val x: Double
    /** Normalised y position (0.0–1.0) on the map. */
    abstract val y: Double
    abstract val label: String

    /**
     * A combat encounter. Tapping the marker starts a battle with [enemies].
     * After the last enemy is defeated the event is marked complete.
     */
    data class BattleEncounter(
        override val id: String,
        override val mapId: String,
        override val x: Double,
        override val y: Double,
        override val label: String,
        /** Enemy unit templates to spawn when this encounter starts. */
        val enemies: List<UnitTemplate>,
    ) : OverworldEvent()
}
