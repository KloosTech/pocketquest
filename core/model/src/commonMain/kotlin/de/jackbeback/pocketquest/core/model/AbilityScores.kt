package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AbilityScores(
    val str: Int,
    val dex: Int,
    val con: Int,
    val int: Int,
    val wis: Int,
    val cha: Int,
)

/** Flat, immutable, fully resolved. Computed once per state version — see Entity.stats() in :core:rules. */
@Serializable
data class Stats(
    val maxHp: Int,
    val armorClass: Int,
    val speedTiles: Int,
    val maxAp: Int,
    val maxMana: Int,
    val abilities: AbilityScores,
    val flags: Set<Flag>,
    val resistances: Map<DamageType, Resistance>,
    /**
     * Every `Modifier.Roll` contributed by an equipped/innate/active source, unresolved — matching
     * a grant's `RollContext` against the actual roll being made (e.g. "advantage vs Enemy" against
     * a specific attack's real target) is a :core:rules concern (`RollContext.matches`), not
     * something `Stats` itself decides. See KNOWN_ISSUES.md #11.
     */
    val rollGrants: List<Modifier.Roll> = emptyList(),
)
