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
    val goldMin: Int = 0,
    val goldMax: Int = 0,
    /** docs/37-lootable-containers.md: replaces the old auto-granted `loot: List<LootEntry>` — which [LootDef] containers fill which rarity-tier spawn tiles, mirroring [enemies]/[EnemySpawn] exactly. */
    val lootSpawns: List<LootSpawn> = emptyList(),
)

@Serializable
data class EnemySpawn(val archetype: ArchetypeId, val role: SpawnRole = SpawnRole.Enemy, val count: Int = 1)

/** docs/37-lootable-containers.md: "this many copies of this container, filling this rarity role's pooled tiles" — the loot-placement counterpart to [EnemySpawn]. */
@Serializable
data class LootSpawn(val loot: LootId, val role: SpawnRole, val count: Int = 1)

/**
 * docs/37-lootable-containers.md: owned by a [LootDef]'s own `table`, reusable across every
 * encounter/map a container appears on. docs/38-loot-reveal-screen.md: [weight] used to be an
 * independent-Bernoulli `chance` (each entry rolled on its own, 0..N hits possible) — changed to a
 * single weighted pick across the whole table (`RngState.pickWeighted`, `core/rules/Dice.kt`) so a
 * container yields exactly one item, the shape a slot-machine reveal needs. Not normalized: a table
 * summing to 1.0 always yields something; one summing below 1.0 has a real chance of "nothing" (the
 * unclaimed remainder); one summing past 1.0 makes its tail entries partly/fully unreachable — an
 * authoring mistake, not an engine error.
 */
@Serializable
data class LootEntry(val item: ItemId, val weight: Double = 1.0)

/**
 * doc11: "enemies instantiated from the EncounterSpec, scaled by act and party size" — no formula
 * given anywhere. Deliberately minimal (flat additive counts, not a curve/multiplier system) —
 * a placeholder worth revisiting once doc13 (encounters and events, not yet written) actually
 * specs this, not a speculative system built ahead of a real requirement.
 */
@Serializable
data class EncounterScaling(val extraEnemiesPerPartySize: Int = 0, val extraEnemiesPerAct: Int = 0)
