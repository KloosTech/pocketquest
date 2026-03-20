package de.jackbeback.pocketquest.designer.model

import kotlinx.serialization.Serializable

enum class OverworldNodeType { START, BATTLE, REST, BOSS }

@Serializable
data class OverworldNodeDef(
    val id: String,
    val type: OverworldNodeType,
    val label: String,
    val x: Double,  // 0.0–1.0 normalized canvas position
    val y: Double,
    val encounterId: String? = null,   // BATTLE/BOSS only
    val healPercent: Float = 0.40f,    // REST only
)

@Serializable
data class OverworldEdgeDef(val fromId: String, val toId: String)

@Serializable
data class OverworldDef(
    val id: String,
    val name: String,
    val backgroundMapId: String? = null,
    val nodes: List<OverworldNodeDef> = emptyList(),
    val edges: List<OverworldEdgeDef> = emptyList(),
)

@Serializable
data class CampaignBundle(
    val id: String,
    val name: String,
    val description: String = "",
    val version: Int = 1,
    val overworldSequence: List<String> = emptyList(),
    val overworlds: List<OverworldDef> = emptyList(),
)
