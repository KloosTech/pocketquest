package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * `entities` is a List, not a Map — JSON object keys must be strings, and
 * this keeps a plain data class serializable without a custom key
 * serializer. [byId] and [occupancy] are derived indices, rebuilt on
 * access from the list; they are never themselves persisted or mutated.
 */
@Serializable
data class GameState(
    val entities: List<Entity>,
    val map: BattleMap,
    val turn: TurnState,
    val rng: RngState,
    val version: Long = 0,
    /** Monotonic source for fresh DecisionIds (e.g. when offering a reaction) — never reused, never reset. */
    val nextDecisionId: Long = 0,
    /** Monotonic source for fresh LinkIds (e.g. when starting concentration) — never reused, never reset. */
    val nextLinkId: Long = 0,
    /** doc17-engine-gaps.md 3.1: monotonic source for a freshly [Effect.SpawnEntity]'d entity's id — never reused, never reset, same pattern as [nextDecisionId]/[nextLinkId]. */
    val nextEntityId: Long = 0,
    /**
     * Fog of war — every tile any living [Faction.Player] entity has ever had line-of-sight to.
     * Monotonic: only ever grows, never shrinks, for the life of one encounter. Empty on a map with
     * [BattleMapDef.fogOfWar] off (nothing is ever dark, so revealing is a no-op there). A new field
     * with a default value decodes old snapshots as-is (`SnapshotMigrations`' own bump rule is "the
     * shape changes in a way old snapshots CAN'T decode as-is") — no `CURRENT_SCHEMA` bump needed.
     */
    val revealedTiles: Set<GridPos> = emptySet(),
    /**
     * EntityIds of every [Faction.Enemy] the party has ever spotted (alive, standing on a
     * [revealedTiles] tile) — monotonic bookkeeping, an id is never removed even once that enemy
     * dies or retreats out of sight. Whether combat is *currently* active is a derived question,
     * [de.jackbeback.pocketquest.core.rules.targeting.inCombat]: true while any entry here is still
     * alive, so one engaged enemy retreating into shadow doesn't end combat while another engaged
     * enemy is still a live threat — but once every entry here is dead and nothing new has been
     * spotted, combat really is over and `:ui` drops back into free-roam exploration until
     * [de.jackbeback.pocketquest.core.rules.targeting.updateEngagedEnemies] adds a freshly-spotted
     * enemy to this set again. Same no-bump-needed reasoning as [revealedTiles].
     */
    val engagedEnemies: Set<EntityId> = emptySet(),
) {
    @Transient
    val byId: Map<EntityId, Entity> = entities.associateBy { it.id }

    @Transient
    val occupancy: Map<GridPos, EntityId> =
        entities.mapNotNull { e -> e.pos?.let { it to e.id } }.toMap()
}
