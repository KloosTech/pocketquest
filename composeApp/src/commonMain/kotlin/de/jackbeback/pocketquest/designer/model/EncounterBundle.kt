package de.jackbeback.pocketquest.designer.model

import kotlinx.serialization.Serializable

/**
 * Top-level serializable encounter file.
 * Saved as JSON on desktop; loadable on any platform from bundled resources.
 */
@Serializable
data class EncounterBundle(
    val id: String,
    val name: String,
    val description: String = "",
    val playerSpawnCol: Int = 2,
    val playerSpawnRow: Int = 4,
    val enemies: List<EnemyDefinition> = emptyList(),
    /** Skills defined specifically for this encounter (referenced by id in enemies). */
    val customSkills: List<SkillDefinition> = emptyList(),
    /** Optional map to load for this encounter. Null means use the default throneroom. */
    val mapId: String? = null,
)
