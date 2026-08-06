# 06 — Persistence

## Room stays, but it is a sink, not a source

The tempting design is to keep game state in Room and observe it with a `Flow`.
It does not work here, for three reasons in increasing severity:

1. **It fights the animation layer.** The UI deliberately renders a state that
   *lags behind* the logical one. A database flow always emits the newest value,
   so a figure would appear at its destination before its move animates. You
   would have to throttle the flow artificially — fighting your own tooling.

2. **Every effect step would cross an IO boundary.** The resolver is a
   tail-recursive in-memory loop that runs hundreds of steps per turn. Routing
   each through SQLite is not merely slow; it turns a synchronous pure function
   into asynchronous state, which discards the determinism guarantee.

3. **`GameState` is not relational.** Sealed hierarchies, polymorphic effects,
   `Expiry` variants, the resolver stack. Normalising that is weeks of work, and
   it buys nothing, because we never issue a relational query against it. We
   never ask "all entities with HP below 5 across all save games".

## The split

```
GameSession (in memory)          ← single source of truth during play
   StateFlow<Resolver>
        │
        └── SaveRepository ──► Room ──► SQLite   (sink)
```

Room stores the **serialized resolver as a blob**, plus the metadata we
genuinely want to query:

```kotlin
@Entity(tableName = "save_slot")
data class SaveSlotRow(
    @PrimaryKey val id: String,
    val campaignId: String,
    val schemaVersion: Int,
    val updatedAt: Long,
    val label: String,              // "Round 3, the Crypt"
    val thumbnailPath: String?,
    val autosave: Boolean,
    val snapshot: ByteArray,        // kotlinx.serialization JSON, UTF-8
)
```

What Room is genuinely good for, and should keep doing: the save slot list,
campaign progress, settings, unlocked content, run statistics, the existing
`SeenHintDao`. All of those are queried and sorted.

## Persist the `Resolver`, not just the `GameState`

If the process dies while a reaction dialog is open, the effect stack and
`pending` must come back with it. Storing only `GameState` loses the
half-executed turn — the player would resume with a move that stopped in the
middle and no way to finish it.

This is why `Resolver` is `@Serializable` in [04](04-resolver.md).

## When to write

Not on every event — that is IO during animation. Write at:

- `StepResult.AwaitingInput` (the risky moment)
- end of turn
- `onStop()` of the host lifecycle
- explicit player save

In between, memory is sufficient. Autosaves go to a fixed slot id so they
overwrite rather than accumulate.

## The catalog does not go in the database

`Archetype`, `ActionDef`, `ItemDef`, `StatusDef` are static content that changes
only with an app update. They live as JSON text, parsed once into a `Catalog`
at startup by `:core:content`'s `CatalogLoader.parse()`. That module cannot
read the file itself — see [01](01-modules.md) rule 3 — so `:app`/`:data`
own getting the JSON text off disk/assets/bundle before handing it to the
parser.

Only put them in the database if mod support or live content updates arrive —
and then as a second layer that overlays the bundled catalog, not as a
replacement.

Consequence: snapshots reference `ArchetypeId` and never embed archetype data,
so balance changes apply to existing saves.

## Serialization gotchas

**Map keys must be strings in JSON.** `Map<EntityId, Entity>` and anything keyed
by `GridPos` will not round-trip cleanly. Store `List<Entity>` and rebuild the
index on load. This is why `GameState.entities` is a list and `byId` /
`occupancy` are `@Transient` — see [02](02-state-model.md).

**Derived fields must be `@Transient`.** Otherwise they are written to disk and
can be loaded back in a stale, self-contradicting form.

**Polymorphic effects need a stable discriminator.** Use explicit
`@SerialName` on every `Effect`, `GameEvent`, `Modifier` and `Expiry`
subtype. Class names refactor; serial names must not. In practice this
needs no `SerializersModule` registration anywhere: every sealed hierarchy
here is a closed `sealed interface` in `:core:model`, and
`kotlinx.serialization` resolves closed sealed hierarchies at compile time
from the `@Serializable`/`@SerialName` annotations alone. A
`SerializersModule` is only for *open* polymorphism (interfaces implemented
outside the module that declares them), which nothing in this codebase
needs. Watch for one real pitfall instead: a field literally named `type`
on a subtype collides with the default `"type"` discriminator key for its
sealed parent and throws on encode — rename the field (`Effect.DealDamage`
hit this; its `damageType` field used to be `type`).

**Use JSON, not a compact binary format.** Snapshots are a few tens of
kilobytes; the size saving is irrelevant next to being able to open a broken
save in a text editor and repair it by hand.

## Snapshot versioning

Room migrations do not help with blob *contents*. We need our own chain:

```kotlin
const val CURRENT_SCHEMA = 3

object SnapshotMigrations {
    fun migrate(json: JsonElement, from: Int): JsonElement =
        (from until CURRENT_SCHEMA).fold(json) { acc, v -> steps.getValue(v)(acc) }

    private val steps: Map<Int, (JsonElement) -> JsonElement> = mapOf(
        1 to ::v1ToV2,   // ...
        2 to ::v2ToV3,
    )
}
```

Rules:
- `schemaVersion` is a column, so a slot can be inspected without parsing.
- Migration steps operate on `JsonElement`, never on the current data classes —
  otherwise a step breaks the moment the class changes again.
- Every step gets a golden-file test: a checked-in old snapshot must load.
  See [09-test-plan.md](09-test-plan.md).
- Loading a snapshot newer than `CURRENT_SCHEMA` fails loudly rather than
  guessing.

## The command log bonus

Because RNG lives in the state and the resolver is deterministic, the full
command sequence is a complete description of a battle — a few kilobytes for an
entire fight, versus a snapshot per turn.

```kotlin
@Entity(tableName = "command_log")
data class CommandLogRow(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val battleId: String,
    val commandJson: String,
)
```

This gives replay, multi-turn undo (replay from the start of the turn with the
last command dropped) and reproducible bug reports.

**Do not use it as the only persistence.** The moment a rule changes, the same
log replays to a different result. Snapshot is the truth; the log is an extra.
Store the initial snapshot plus the log, and validate on replay by comparing a
state hash at the end.

## Testing hooks

- `GameState` round-trips: `decode(encode(s)) == s` as a property test.
- `Resolver` mid-decision round-trips, including a non-empty stack.
- A checked-in corpus of old snapshots under
  `commonTest/resources/snapshots/v1/…` must load through the migration chain.
