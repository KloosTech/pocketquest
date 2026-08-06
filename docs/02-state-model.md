# 02 — State model

## Static vs. runtime

The most expensive mistake to correct later is mixing per-kind data with
per-instance data. A goblin has values that apply to *every* goblin (max HP 7,
AC 15, its action list) and values that belong to this one (current HP,
position). The first kind must not end up in every save file.

```kotlin
@JvmInline @Serializable value class EntityId(val raw: Long)
@JvmInline @Serializable value class ArchetypeId(val raw: String)
@JvmInline @Serializable value class ActionId(val raw: String)
@JvmInline @Serializable value class StatusId(val raw: String)
@JvmInline @Serializable value class ItemId(val raw: String)

/** Static. Loaded from JSON at startup, never mutated at runtime. */
data class Archetype(
    val id: ArchetypeId,
    val name: String,
    val abilities: AbilityScores,      // STR DEX CON INT WIS CHA
    val baseMaxHp: Int,
    val baseAc: Int,
    val speedTiles: Int,
    val baseMaxAp: Int,
    val baseMaxMana: Int,
    val actions: List<ActionId>,
    val innateModifiers: List<Modifier> = emptyList(),
)
```

Saves store `ArchetypeId` references only. This keeps snapshots small and means
a balance patch applies retroactively to existing save files.

## The entity

Not inheritance (`class Player : Entity`), which breaks on the first charmed
NPC or destructible barrel. Not a `Map<KClass, Component>` either — at our
entity count that buys nothing but casts and null checks, and it is what makes
v1's `world.get<HealthComponent>(id)` return a nullable every single time.

Typed, nullable fields on one `data class`:

```kotlin
@Serializable
data class Entity(
    val id: EntityId,
    val archetype: ArchetypeId,
    val pos: GridPos?,                       // null = not on the map (reserve, dead)
    val health: Health?,                     // null = indestructible (wall, marker)
    val resources: Resources?,               // null = does not act (barrel)
    val actor: Actor?,                       // null = not in the initiative order
    val equipment: Equipment = Equipment.EMPTY,
    val statuses: List<ActiveStatus> = emptyList(),
    val blocksMovement: Boolean = true,
)

@Serializable data class Health(val current: Int, val temp: Int = 0)
@Serializable data class Resources(val ap: Int, val mana: Int, val quickUsed: Boolean)
@Serializable data class Actor(val faction: Faction, val controller: Controller)

@Serializable sealed interface Controller {
    @Serializable data object Human : Controller
    @Serializable data class Ai(val profile: AiProfileId) : Controller
}
```

Player and enemy differ **only** in `actor.faction` and `actor.controller` —
not in type. That is what makes a charmed enemy or an auto-piloted companion a
one-field change rather than a new class.

Compare with v1: `GameLoop.getActivePlayer()` picks the first entity with
`Faction.PLAYER`. That silently forbids a party of more than one, and it is not
a bug you can patch — it is the data model.

## Derived values are functions

`maxHp`, `armorClass` and `speed` change with equipment, buffs and conditions.
Stored as fields they will drift. In v1 `StatsComponent` is stored and
`ConditionsComponent` is separate, so a condition that should modify AC has
nowhere correct to write.

```kotlin
/** Flat, immutable, fully resolved. Computed once per state version. */
data class Stats(
    val maxHp: Int,
    val armorClass: Int,
    val speedTiles: Int,
    val maxAp: Int,
    val maxMana: Int,
    val abilities: AbilityScores,
    val flags: Set<Flag>,                 // CantAct, Prone, Flying, Invisible…
    val resistances: Map<DamageType, Resistance>,
)

fun Entity.stats(cat: Catalog): Stats
```

Nothing in the codebase reads `statuses` or `equipment` to answer a numeric
question. Everything goes through `stats()`. Derivation rules are in
[03-modifiers-and-status.md](03-modifiers-and-status.md).

**Caching.** `stats()` is pure, so memoise per `(EntityId, stateVersion)` in a
side table held by the resolver. Do not put a cache field inside `Entity` — it
would be serialized and could go stale across a save/load.

## Grid and occupancy

Position lives on the entity. The grid is an **index**, rebuilt on demand:

```kotlin
@Serializable data class GridPos(val col: Int, val row: Int)

data class GameState(
    val entities: List<Entity>,           // list, not map — see persistence note
    val map: BattleMap,
    val turn: TurnState,
    val rng: RngState,
    val version: Long = 0,
) {
    @Transient
    val byId: Map<EntityId, Entity> = entities.associateBy { it.id }

    @Transient
    val occupancy: Map<GridPos, EntityId> =
        entities.mapNotNull { e -> e.pos?.let { it to e.id } }.toMap()
}
```

Two entities are never written to the same tile, because tiles are not written
to at all. v1's `MovementSystem` has to scan all positions with
`world.query<PositionComponent>().any { ... }` on every move to detect
collisions; here the check is a map lookup and cannot desync.

`entities` is a `List` rather than a `Map` because JSON object keys must be
strings — see [06-persistence.md](06-persistence.md).

## RNG lives in the state

```kotlin
@Serializable data class RngState(val seed: Long, val calls: Long)

fun RngState.d20(): Pair<RngState, Int>
fun RngState.roll(dice: DiceSpec): Pair<RngState, RollResult>
```

Every roll returns a new `RngState` alongside its result. Consequences:

- The same command sequence on the same seed always produces the same outcome —
  bug reports become reproducible from a command log.
- Tests are deterministic without mocking anything.
- Save/load mid-combat cannot re-roll a different result.

Correspondingly, **`kotlin.random.Random` must never be called in
`:core:rules`.** Add a Konsist or Detekt rule for this once the module exists;
it is the sort of thing that creeps back in.

## Turn state

```kotlin
@Serializable data class TurnState(
    val round: Int,
    val order: List<EntityId>,          // rolled initiative, stable
    val activeIndex: Int,
    val phase: TurnPhase,               // Start, Main, End — not Player/Enemy
)
```

Note the difference from v1's `TurnPhase.PlayerPhase / EnemyPhase /
EnvironmentPhase`. Side-based phases are not D&D turn order, and they make an
interleaved initiative (goblin, player, goblin) impossible to express. The
active entity is `order[activeIndex]`; whether a human or an AI answers for it
is `actor.controller`, not a phase.

## Invariants

Enforced in a `checkInvariants(state)` helper used by tests and debug builds:

1. Every `EntityId` in `turn.order` exists in `entities`.
2. No two entities with a non-null `pos` share a `GridPos`.
3. Every `pos` is inside the map bounds and on a walkable tile.
4. `health.current in 0..stats.maxHp` for all entities with health.
5. `resources.ap in 0..stats.maxAp`, same for mana.
6. Every `ActiveStatus.sourceId`, if non-null, refers to an existing entity.
7. `activeIndex in order.indices` whenever `order` is non-empty.

These become the shared assertion block in the property tests described in
[09-test-plan.md](09-test-plan.md).
