# 50 — Runtime terrain mutation (`SetTerrain`)

docs/Campain_1's Broken Gate Pass has a "Rickety Bridge" over the East
Chasm and crumbling watchtowers; the Vault of Forgotten Souls has
"Blocked Passages"/"Debris"/"Crawlspaces." Both read as the same authoring
need: a specific tile's `TileType` changes mid-encounter — a bridge span
collapses into a chasm, a rubble pile clears into open floor — driven by the
same trigger/effect vocabulary docs/36 and docs/48 already established, not
a bespoke "bridge" or "rubble" system.

## Why this is a new effect, not a reuse of `OpenGate`

`OpenGate` (docs/48) flips one bit of *runtime state* (`GateId` in/out of
`GameState.openGates`) that `canCross` consults — the underlying `TileType`
never changes, and a gate's edges are never LoS-blocking either way. A
collapsing bridge is different: the tile genuinely becomes a different
`TileType` (walkable floor → unwalkable, possibly LoS-blocking, chasm), and
unlike a gate there's no natural "this one specific placement's state"
scope — an author wants to reshape arbitrary terrain at arbitrary points,
not toggle one authored object. Closer to `ShowMessage`/`SpawnEntity` in
shape (an effect that writes new content into `GameState`/`BattleMap`) than
to `OpenGate`.

## Model

```kotlin
// ActionDef.kt
/** No Ref — [at] is a literal authored position, not resolved from ActionCtx.point, so one trigger's effect list can reshape several different cells (unlike SpawnEntity/Teleport, which share the trigger's own point). */
data class EffectTemplate.SetTerrain(val at: GridPos, val tile: TileType) : EffectTemplate

// EffectTemplateInstantiate.kt
is EffectTemplate.SetTerrain -> listOf(Effect.SetTerrain(at, tile))

// Effect.kt
data class Effect.SetTerrain(val at: GridPos, val tile: TileType) : Effect

// Handlers.kt
is Effect.SetTerrain -> HandlerOutcome(
    state.copy(map = state.map.copy(terrain = state.map.terrain + (effect.at to effect.tile))),
    listOf(GameEvent.TerrainChanged(effect.at, effect.tile)),
)
```

`at` is a literal `GridPos`, exactly like `OpenGate`'s literal `GateId` —
not a `Ref`, nothing to resolve per-target. This is the deliberate design
choice over binding to `ActionCtx.point`: a trigger authoring "the whole
bridge collapses" is one `TriggerPlacement` with N `SetTerrain` effects,
each naming its own cell, not N separate trigger placements the way
`SpawnEntity`/`Teleport` are constrained to today (docs/36's own
constraint, explicitly not repeated here because nothing about
`SetTerrain` needs a `Ref`/`ActionCtx.point` to make sense).

`TileType.Wall`/`.Floor`/`.Difficult`/`.Hazard` (the existing presets,
`Grid.kt`) cover every case this pass needs — a chasm preset is just
`TileType(walkable = false, blocksLoS = false, hazard = true)`, expressible
today with zero `TileType` changes (see the Chasm authoring note below).

`GameEvent.TerrainChanged(at: GridPos, tile: TileType)` — `:ui`'s `Director`
gets a `Beat` that re-renders the affected tile with its new appearance
(instant, matching `OpenGate`'s "static swap, no animation" v1 scope — a
crumbling-into-rubble animation is a real future want but not this pass's).

## Behavior notes (not open questions — stated, not asked)

- **An entity standing on a tile that becomes unwalkable is not moved or
  hurt by `SetTerrain` itself.** It stays exactly where it is; the tile is
  simply no longer a legal *destination* for anyone else's future movement.
  A "the floor gives way under you" consequence is authored separately —
  e.g. a trigger placed one step ahead of the collapse that also carries a
  `DealDamage`/`Teleport`, same "compose existing primitives" pattern
  every other multi-effect trigger already uses.
- **No new WallEdge is created or removed.** A `SetTerrain` chasm sitting in
  open floor needs no edge geometry — LoS already passes freely over any
  non-`blocksLoS` tile, and `canCross`'s edge checks are independent of
  `TileType.walkable`. If an author-drawn scene genuinely wants edge walls
  around the new chasm too, that's a second, ordinary (static, authored at
  map-design time) `WallEdge`/Gate placement — `SetTerrain` only ever
  touches the one axis (`TileType`) its name says.
- **Fog of war interacts exactly like any other terrain read.** Because
  `blocksLoS`/`isWalkable` are plain `terrain` lookups, `visibleTilesFrom`
  naturally recomputes correctly on the very next `updateRevealedTiles`
  call after a `SetTerrain` fires — no fog-specific plumbing needed. A
  newly-opened rubble pile can reveal tiles beyond it the instant it clears,
  a newly-collapsed bridge can cut off sight the instant it doesn't, both
  for free.

## `:designer` authoring

`EffectTemplateEditor.kt`'s `EffectKind` enum gains `SetTerrain`, with a
row exposing: a `GridPos` picker (reuse whatever coordinate-entry control
`SpawnEntity`/`Teleport` already use for a point field, if one exists —
otherwise two `IntStepper`s, col/row) and a `TileType` picker reusing the
Map editor's own terrain-tool preset list (`Floor`/`Wall`/`Difficult`/
`Hazard` swatches, `descriptionFor(TileType)`'s existing mechanically-exact
copy) rather than inventing a second terrain vocabulary.

**Chasm authoring note:** no new `TileType` case is added — the Map
editor's terrain palette gains one more named swatch, `Chasm`
(`TileType(walkable = false, blocksLoS = false, hazard = true)`), sitting
alongside `Wall`/`Difficult`/`Hazard` in both the static terrain-paint tool
and this effect's picker. Purely a labeled preset over the existing
`TileType` axes — same "author sees a name, not four raw booleans" spirit
`descriptionFor(TileType)` already has for the other three.

## Non-goals (v1)

- No animation on the tile change (falling rubble, cracking ice) — instant
  swap, matching `OpenGate`.
- No effect-side consequence for whoever's standing on the tile — see
  "Behavior notes" above, compose it from existing effects instead.
- No new `WallEdge`-mutating effect — `SetTerrain` only ever touches
  `TileType`.
- No batch/region form (`SetTerrainRect`, a shape like actions have) — one
  effect per cell, same as `SpawnEntity`; a trigger authoring a wide
  collapse is a longer effect list, not a new primitive.
