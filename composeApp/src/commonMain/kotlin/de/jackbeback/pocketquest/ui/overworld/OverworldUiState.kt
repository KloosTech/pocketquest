package de.jackbeback.pocketquest.ui.overworld

import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.core.EntityId

data class OverworldUiState(
    val units: List<OverworldUnitUiState>
)

data class OverworldUnitUiState(
    val entityId: EntityId,
    val name: String,
    val faction: Faction,
    val mapX: Double,
    val mapY: Double,
    val spriteKey: String,
)
