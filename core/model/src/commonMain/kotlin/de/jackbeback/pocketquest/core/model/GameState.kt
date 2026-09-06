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
    /**
     * docs/36-map-triggers.md: [TriggerId]s that have already fired, for the life of one encounter
     * — monotonic, only ever grows, same shape as [revealedTiles]. A fresh [GameState] (a new
     * encounter, a replay of the same map) naturally starts with an empty set; no separate reset
     * logic exists or is needed.
     */
    val firedTriggers: Set<TriggerId> = emptySet(),
    /**
     * docs/37-lootable-containers.md: every lootable container resolved into this encounter,
     * populated ONCE in `startEncounter`'s `buildEncounterState` — the loot-placement counterpart
     * to spawning an `Entity` for an `EnemySpawn`, except a container isn't an `Entity` (no HP, no
     * turn, no faction).
     */
    val lootPlacements: List<LootPlacement> = emptyList(),
    /** docs/37-lootable-containers.md: which [lootPlacements] positions have been opened — monotonic, only ever grows, same shape as [firedTriggers]/[revealedTiles]. */
    val openedLoot: Set<GridPos> = emptySet(),
    /** docs/48-gates-and-wander-ai.md: [GateId]s currently open — monotonic, one-way (no `CloseGate` effect exists), same shape as [firedTriggers]. Read by [BattleMap.canCross] via `findPath`/`reachableTiles`. */
    val openGates: Set<GateId> = emptySet(),
) {
    @Transient
    val byId: Map<EntityId, Entity> = entities.associateBy { it.id }

    /**
     * Every tile something is physically standing on — targeting/inspection reads this; a dead
     * entity still occupies its tile here, it just doesn't [blockingOccupancy] anymore.
     *
     * docs/39-corpse-movement.md's own predicted follow-up: a corpse's `pos` is never cleared, so
     * a living entity can end up sharing a tile with a dead one once it walks there (corpses don't
     * block, see [blockingOccupancy]). `entities.associate`/`toMap()` can only keep one id per
     * `GridPos` and picks whichever entry comes last in `entities`' own (spawn-order, never
     * reshuffled) list — arbitrary with respect to which one is actually alive, which broke both
     * tap-to-select/inspect and `SingleEntity` targeting's `requireAlive` filter (the corpse
     * winning the lookup made the tile silently untappable). Sorting alive-last before building the
     * map means an alive occupant always wins a contested tile, falling back to the corpse only
     * when every entity there is dead — `sortedBy` is stable, so an uncontested tile's single
     * occupant is completely unaffected.
     */
    @Transient
    val occupancy: Map<GridPos, EntityId> =
        entities.sortedBy { e -> if ((e.health?.current ?: 1) > 0) 1 else 0 }
            .mapNotNull { e -> e.pos?.let { it to e.id } }
            .toMap()

    /**
     * docs/39-corpse-movement.md: the subset of [occupancy] that actually blocks movement —
     * [Entity.blocksMovement] false once an entity hits 0 HP (`Handlers.kt`'s `dealDamage`/`heal`),
     * true again if revived. Pathfinding/reachability/move-legality checks use this instead of
     * [occupancy] directly; targeting and tap-to-inspect still use [occupancy] itself, since a
     * corpse should still be findable/inspectable, just no longer an obstacle.
     */
    @Transient
    val blockingOccupancy: Map<GridPos, EntityId> =
        entities.mapNotNull { e -> if (e.blocksMovement) e.pos?.let { it to e.id } else null }.toMap()
}
