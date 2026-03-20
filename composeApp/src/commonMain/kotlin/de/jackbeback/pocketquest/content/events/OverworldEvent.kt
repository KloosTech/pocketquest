package de.jackbeback.pocketquest.content.events

import de.jackbeback.pocketquest.content.dsl.UnitTemplate

/**
 * Describes a point-of-interest on the overworld progression graph.
 * Every event has a unique [id] matching a [MapNode.id] and a normalised position
 * used to draw the node on the ProgressionGraph canvas.
 */
sealed class OverworldEvent {
    abstract val id: String
    abstract val mapId: String
    /** Normalised x position (0.0–1.0) used for graph layout. */
    abstract val x: Double
    /** Normalised y position (0.0–1.0) used for graph layout (0 = top, 1 = bottom). */
    abstract val y: Double
    abstract val label: String

    /** Invisible entry node — the player starts here; no event fires. */
    data class StartNode(
        override val id: String,
        override val mapId: String,
        override val x: Double,
        override val y: Double,
        override val label: String = "Start",
    ) : OverworldEvent()

    /**
     * A combat encounter. Tapping starts a battle.
     * After winning the event is marked complete, unlocking connected forward nodes.
     */
    data class BattleEncounter(
        override val id: String,
        override val mapId: String,
        override val x: Double,
        override val y: Double,
        override val label: String,
        val enemies: List<UnitTemplate>,
        val isBoss: Boolean = false,
    ) : OverworldEvent()

    /**
     * A rest site. Tapping opens a confirmation dialog.
     * Completing (confirm or dismiss) marks the node done and unlocks forward nodes.
     */
    data class RestSite(
        override val id: String,
        override val mapId: String,
        override val x: Double,
        override val y: Double,
        override val label: String,
        val healPercent: Float = 0.40f,
    ) : OverworldEvent()
}
