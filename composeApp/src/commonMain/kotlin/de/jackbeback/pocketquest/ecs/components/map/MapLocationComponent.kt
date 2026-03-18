package de.jackbeback.pocketquest.ecs.components.map

/** Overworld position — separate from battle PositionComponent. */
data class MapLocationComponent(
    val mapId: String,
    val x: Double,  // normalized 0.0–1.0 position on the overworld map
    val y: Double,
)
