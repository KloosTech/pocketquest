# 39 — Corpses stop blocking movement

Bug report: an enemy that died could still permanently block its own tile, occasionally stranding
the party with no legal path (surrounded by walls/other entities and one dead body).

## Root cause

`Entity.blocksMovement: Boolean = true` already existed on the model but was never actually read
anywhere — `GameState.occupancy` includes every entity with a non-null `pos` regardless of health,
and every pathing/movement-legality check (`findPath`, `reachableTiles`, `Effect.MoveAlong`/
`Effect.Teleport`/`Effect.SpawnEntity`'s own walkability checks) tested `occupancy.containsKey(pos)`
directly. `pos` is never cleared on death either — `Died`/`Downed` fire, but nothing removes the
entity or frees its tile. So a corpse (or a downed ally, same 0-HP state) occupied its tile exactly
as solidly as a living combatant, forever, unless `Effect.DestroyEntity` happened to be explicitly
authored on it somewhere (nothing does this automatically).

## Fix: a second, movement-specific occupancy view

`GameState` gains `blockingOccupancy` alongside the existing `occupancy`:

```kotlin
val blockingOccupancy: Map<GridPos, EntityId> =
    entities.mapNotNull { e -> if (e.blocksMovement) e.pos?.let { it to e.id } else null }.toMap()
```

`occupancy` itself is untouched — targeting and tap-to-inspect still need to find a corpse (you can
target it with certain effects, tap it to see what killed your teammate, etc.), so it keeps meaning
"who is physically present on this tile," dead or alive. `blockingOccupancy` is the narrower "what
actually stops you walking here" view, and is what every movement/pathing call site now passes
instead: `Pathfinding.kt`'s `findPath`, `Range.kt`'s `reachableTiles` (and by extension `Threat.kt`'s
threatened-tile computation, `Targeting.kt`'s Move-action legal tiles, the AI's own `ChooseAction.kt`
reachability/pathing, `CanPerform.kt`/`Perform.kt`'s Point-targeted path checks), plus the direct
`occupancy.containsKey` walkability checks inside `Handlers.kt`'s `moveAlong`/`teleport`/
`spawnEntity` handlers. `:ui`'s `exploreMoveTo` inherits the fix for free — it just calls `findPath`.

`blocksMovement` itself flips in exactly two places, both in `Handlers.kt`, always set to
`newCurrent > 0` (never a separate "was this a fresh transition" check — simpler, and idempotent if
already correct):

- `dealDamage`'s Step 7 (Apply) — the same spot `Died`/`Downed` already fire from.
- `heal`'s revive path — mirrors `dealDamage`, so a revived ally blocks its tile again like any
  other combatant the instant it's back above 0 HP.

Both factions, symmetric — a downed ally stops blocking a corridor exactly like a dead enemy does,
not a special case scoped to just enemies. The underlying bug (an inert flag no code path ever set
or read) was identical for both; there was no reason to fix only half of it.

## Known limitation: a corpse and a living entity can now share a tile

`spawnEntity`/`moveAlong`/`teleport` only ever check `blockingOccupancy` before placing an entity —
a corpse's own `pos` is never cleared, so a living entity can now end up sharing a tile with a dead
one. `occupancy` (a plain `Map<GridPos, EntityId>`) can only ever report one id per position, so
whichever entity comes later in `state.entities`' own list order wins that lookup — tap-to-inspect
or a tile-targeted effect on that square could resolve to either. Purely a minor visual/lookup
ambiguity (two sprites drawn stacked on the same tile, "which one does inspect pick"), not a
crash or a blocked-movement regression — genuinely walking onto/through a corpse's tile was the
actual ask; deconflicting simultaneous occupants is a separate, smaller polish item if it ever
turns out to matter in practice.
