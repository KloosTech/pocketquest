package de.jackbeback.pocketquest.designer.model

import kotlinx.serialization.Serializable

@Serializable
data class DesignerConfig(
    val recentCampaigns: List<String> = emptyList(),   // absolute paths
    val lastOpenedCampaignDir: String? = null,
)
