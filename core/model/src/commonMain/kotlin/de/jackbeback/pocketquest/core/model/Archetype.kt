package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/** Static. Loaded from the catalog at startup, never mutated at runtime. */
@Serializable
data class Archetype(
    val id: ArchetypeId,
    val name: String,
    val abilities: AbilityScores,
    val baseMaxHp: Int,
    val baseAc: Int,
    val speedTiles: Int,
    val baseMaxAp: Int,
    val baseMaxMana: Int,
    val actions: List<ActionId> = emptyList(),
    val innateModifiers: List<Modifier> = emptyList(),
    val innateDamageSteps: List<DamageStep> = emptyList(),
    val innateHealSteps: List<HealStep> = emptyList(),
    /** docs/21-ai-behavior-spec.md: "different enemies, different AIs" is per-archetype, not per-spawn. `"standard"` is the always-ships-with-a-real-entry built-in default (see `Catalog.defaultAiProfile`), so an archetype that never sets this still behaves exactly as before this field existed. */
    val aiProfile: AiProfileId = AiProfileId("standard"),
)
