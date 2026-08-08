package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * doc16's Encounter editor produces this — "enemy composition, map reference, scaling" — but no
 * doc anywhere gives its actual shape (doc11 only ever references `EncounterSpec` by name; doc13,
 * where a real one would live, doesn't exist yet). Designed fresh: [mapId] plus WHAT spawns and HOW
 * MANY, not WHERE — the map's own [SpawnZone]s already say where, matching doc16's "reused across
 * party sizes" reasoning for why zones exist instead of fixed coordinates at all.
 */
@Serializable
data class EncounterSpec(
    val id: EncounterId,
    val name: String,
    val mapId: MapId,
    val enemies: List<EnemySpawn> = emptyList(),
    val scaling: EncounterScaling = EncounterScaling(),
)

@Serializable
data class EnemySpawn(val archetype: ArchetypeId, val role: SpawnRole = SpawnRole.Enemy, val count: Int = 1)

/**
 * doc11: "enemies instantiated from the EncounterSpec, scaled by act and party size" — no formula
 * given anywhere. Deliberately minimal (flat additive counts, not a curve/multiplier system) —
 * a placeholder worth revisiting once doc13 (encounters and events, not yet written) actually
 * specs this, not a speculative system built ahead of a real requirement.
 */
@Serializable
data class EncounterScaling(val extraEnemiesPerPartySize: Int = 0, val extraEnemiesPerAct: Int = 0)
