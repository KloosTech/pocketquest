# 48 — Gates and wander AI

Beast-pen-style encounter: a beast sits behind a closed portcullis, visible
and already fought (fog-of-war's existing "revealed enemy is engaged" rule)
but unreachable. It paces the pen on its own turns. A trigger later opens the
gate and the beast can path to the party like any other enemy.

Two independent primitives, each reusable well beyond this one map:

- **Gate**: authored wall geometry that blocks movement while closed but
  never blocks line of sight (bars, not a solid door) — a movement-only
  cousin of [WallEdge], with a runtime open/closed state.
- **Wander**: a new [AiGoal] for "no legal way to reach my target, so just
  move somewhere" — a trailing tier in the existing tiered AI profile, not a
  new condition or a replacement for [AiGoal.Approach].

## Decided with the user before implementation

- **What opens a gate**: a player-stepped [TriggerPlacement] (docs/36), same
  as everything else that fires effects — a new `OpenGate` effect kind added
  to the existing vocabulary, not a new interaction system. No lever/prop
  interaction verb, no turn-counter auto-open.
- **Gate width**: one `GatePlacement` can own a *list* of `WallEdge`s (a
  contiguous run, same orientation) sharing one id and one open/closed state
  — a 2-tile-wide portcullis like the reference image is one gate, one
  trigger opens the whole span.
- **Reversibility**: one-way. `OpenGate` only; no `CloseGate`. Matches the
  portcullis fiction (raised once, stays raised) and keeps
  `GameState.openGates` a monotonic set, same shape as `firedTriggers`/
  `engagedEnemies`.
- **Wander target selection**: pick a random reachable tile (within the
  entity's own move budget) each turn it can't reach anything, not an
  authored patrol path. No new placement UI, no per-entity patrol-index
  runtime state.
- **Visual**: static closed/open sprite swap on `GateOpened`, no raise
  animation in this pass.
- **Scope**: engine + `:designer` authoring only. No demo Beast Pen map
  content authored as part of this — the user places it by hand afterward.

## Why a gate can't just be a `WallEdge`

`hasLineOfSight` (`core/rules/targeting/LineOfSight.kt`) currently treats
every `WallEdge` as sight-blocking unconditionally — it's checked as a
straight geometric intersection test against every edge on the map, with no
state/flag read at all. A `WallEdge` is also pure authored geometry with no
runtime state of its own (unlike `TriggerPlacement`, which pairs authored
data with `GameState.firedTriggers`). Retrofitting "sometimes blocks LoS,
sometimes doesn't, and has an open/closed flag" onto `WallEdge` would touch
every existing caller of `wallEdges` for a shape only gates need. Cleaner as
a sibling type that composes with the wall system at the two call sites that
actually need it (`canCross`, never `hasLineOfSight`) than as a modification
to `WallEdge` itself.

## Model

```kotlin
// Ids.kt
@JvmInline @Serializable value class GateId(val raw: String)

// MapDef.kt
@Serializable
data class GatePlacement(
    val id: GateId,
    val edges: List<WallEdge>, // contiguous, same Side — one visual/logical unit
    val closedSprite: String? = null,
    val openSprite: String? = null,
)
```

- `BattleMapDef.gates: List<GatePlacement> = emptyList()` — authored, same
  sibling shape as `triggers`/`props`.
- `BattleMap.gates: List<GatePlacement> = emptyList()` — carried through by
  `toBattleMap()`. IS a rules-engine consumer (movement legality), same
  category as `triggers`/`fogOfWar`.
- `GameState.openGates: Set<GateId> = emptySet()` — runtime-only, monotonic,
  same shape as `firedTriggers`. A fresh `GameState` starts empty; no reset
  logic needed.

### Movement: `BattleMap.canCross`

`canCross(from, to)` currently returns `!hasWallEdge(from, side)` per
direction. A gate edge coincident with the same `(pos, side)` overrides that
check: blocked while the owning `GatePlacement.id` is not in
`GameState.openGates`, passable once it is. `canCross` doesn't currently take
a `GameState`/open-set parameter — every call site (`findPath`,
`reachableTiles`, `legalTargets`, the exploration hop loop) needs to thread
`openGates` through, same as they already thread `blockingOccupancy`. A gate
edge is *never* also a plain `WallEdge` in `wallEdges` — the two lists are
disjoint, `canCross` checks gates first, falls back to `hasWallEdge` for
everything else.

### Line of sight: unchanged

`hasLineOfSight` reads `map.wallEdges` and `map.terrain`'s `blocksLoS` only —
`GatePlacement.edges` are never added to `wallEdges` and gate tiles keep
`TileType.Floor` (walkable=true from either side once open, sight was never
blocked to begin with). No change needed to `LineOfSight.kt` at all; this is
the entire reason gates are a separate list rather than a flag on
`WallEdge`.

## Effect: `OpenGate`

Exact same three-hop shape `ShowMessage` established in docs/36:

- `EffectTemplate.OpenGate(gate: GateId)` (`ActionDef.kt`) — no `Ref`, the
  gate id is static authored content, nothing to resolve per-target.
- `EffectTemplateInstantiate.kt`: `is EffectTemplate.OpenGate -> listOf(Effect.OpenGate(gate))`.
- `Effect.OpenGate(gate: GateId)` (`Effect.kt`) — handler:
  `is Effect.OpenGate -> HandlerOutcome(state.copy(openGates = state.openGates + effect.gate), listOf(GameEvent.GateOpened(effect.gate)))`.
- `GameEvent.GateOpened(gate: GateId)` (`GameEvent.kt`) — `:ui`'s `Director`
  gets a `Beat` that swaps the rendered sprite for that `GatePlacement` from
  `closedSprite` to `openSprite` (no travel-time hold, unlike
  `MessageShown` — this is instant per the "static swap" decision).

An author places `OpenGate` in any `TriggerPlacement.effects` list exactly
like `ShowMessage`/`DealDamage`/`SpawnEntity` — no new firing mechanism, the
existing `fireTriggerIfAny` (docs/36) already runs whatever effect list is
authored there, in both exploration and combat.

## AI: `AiGoal.Wander`

```kotlin
// AiProfile.kt
sealed interface AiGoal {
    // ...existing cases unchanged...
    @Serializable @SerialName("wander") data object Wander : AiGoal
}
```

`resolveGoal` gains `AiGoal.Wander -> resolveWander(state, entityId, cat)`.

- Uses the entity's own Path-mode move action exactly like
  `resolveMoveRelativeToNearestEnemy` does (same `budget = minOf(rangeInTiles(...), ap)`
  formula), so it never proposes a move `canPerform` would reject.
- Candidate destinations: `reachableTiles(pos, budget, state.map, state.blockingOccupancy) - pos`.
  Empty (fully boxed in) returns null — falls through to `defaultScoredChoice`,
  same "nothing legal, pass the turn" outcome every other exhausted goal has.
- Picks one candidate via a **deterministic** pseudo-random index seeded from
  `(state.version, entityId.raw)` — not `GameState.rng`. `chooseAction`'s
  signature is pure (`GameState -> AiDecision?`, no state returned), so it
  has no way to persist an advanced `RngState.calls` counter back onto the
  state the caller holds; reusing `rng` here would silently pick the *same*
  tile every call until some unrelated dice roll happens to advance
  `calls`, reading as a beast frozen in place rather than wandering. `version`
  already changes on every applied effect (each hop of movement included),
  so successive turns naturally see a different seed with no plumbing
  changes to `chooseAction`, `resolveGoal`, or any call site.
- Same splitmix-style mix `Dice.kt`'s `rngFor` uses (not `RngState` itself,
  just the same bit-mixing technique) to turn `(version, entityId)` into an
  index — avoids a biased low-bit modulo pick.

### Authoring a wander fallback

No new `AiCondition` needed. An author adds a trailing tier to the beast's
`AiProfileDef`:

```
tier 1: { condition: Always, goal: Approach }
tier 2: { condition: Always, goal: Wander   }
```

`chooseAction`'s existing loop (`ChooseAction.kt`) already falls through to
the next tier when a goal resolves to null — `Approach` returns null when
`findPath` finds no route to the nearest enemy (gate closed), so tier 2
fires instead. Once a trigger's `OpenGate` effect makes the gate passable,
`Approach` starts succeeding again on the beast's next turn — no extra
plumbing to "wake up" the beast, initiative order already gave it turns the
whole time (fog-of-war's `updateEngagedEnemies` engaged it the moment the
party first saw it through the bars).

## `:designer` authoring

`MapEditorPanel.kt`'s `PaintTool` sealed interface gets a `Gate` case,
painted edge-by-edge exactly like `PaintTool.Wall` (`nearestSide` + a
toggle), but into a separate edge set rather than `wallEdges`. Grouping into
`GatePlacement`s happens the same way `TerrainRun` already turns painted
cells into runs (`compressTerrainToRuns`) rather than requiring an explicit
"start/finish this gate" click-mode: a pure grouping pass walks the painted
gate-edge set, buckets maximal contiguous same-`Side` runs into one
`GatePlacement` each, assigning a fresh `GateId` (UUID, `:designer` is
JVM-only same as `TriggerId`) the first time a run appears and preserving an
existing id for a run that still shares at least one edge with it after an
edit (same "id is stable authoring metadata, never shown to the player"
contract `TriggerId` already has).

The gate's inline editor (opened by clicking an already-painted gate edge,
same pattern `Trigger`'s inline editor uses) exposes:

- `closedSprite`/`openSprite` pickers — same `ManifestAsset` picker
  `PaintTool.Prop` already uses.

Nothing else — a gate has no author-typed fields beyond its geometry and its
two sprites. `EffectTemplateEditor.kt`'s `EffectKind` enum gains one new
case, `OpenGate`, with a dropdown of `GateId`s sourced from the current
`BattleMapDef.gates` (displayed as "Gate 1"/"Gate 2" by list index, same
"opaque id, human-facing index label" precedent `TriggerPlacement` itself
has no name for either) — every other `EffectKind` case is unchanged.

Gate cells render on the canvas as a distinct ink glyph/bar pattern (closed
sprite, since `:designer` has no live `GameState`/`openGates` to preview an
open one) in both `:ui`'s Board and `:designer`'s Map editor canvas, matching
docs/36's "author sees what the player sees" precedent for the map's
resting/closed state.

`AiProfilePanel` (wherever `AiTier`/`AiGoal` are currently authored) gains
`Wander` as a selectable goal alongside `UseAction`/`Retreat`/`Approach` —
no new fields, it's a bare `data object`.

## Amendment: secret doors are a Gate, not a new primitive

docs/Campain_1's Broken Gate Pass labels two rooms "Secret Passage Hidden" —
a wall that looks solid until something else reveals it, distinct from a
portcullis' visibly-barred "closed" state. Discussed with the user and
decided: this is a rendering choice on `GatePlacement`, not a new engine
concept. A gate whose `closedSprite` is left `null` renders as plain
matching wall texture (indistinguishable from `hasWallEdge`'s ordinary
wall rendering) instead of a bars/door sprite — everything else (blocks
`canCross` while closed, never blocks `hasLineOfSight`, opened by an
`OpenGate` effect from a trigger placed elsewhere — a lever, a bookshelf,
a floor plate across the room) is identical to a normal gate. No new
model field, no new effect.

The one addition: `:designer`'s Map editor renders every `GatePlacement`
with `closedSprite == null` as a distinct dashed/tinted outline (author-only,
never shown in `:ui`'s Board) so the author can still find and edit a secret
door they placed — the "author sees what the player sees" rule from docs/36
deliberately does NOT apply to this one authoring-time affordance, since the
whole point of a secret door is that the player does *not* see what the
author sees.

## Amendment: multi-trigger unlock

Some puzzles ("light all three braziers") need a gate that opens only once
several independent triggers have all fired, not any single one. Adding a
condition to `GatePlacement` rather than teaching `TriggerPlacement.effects`
any N-of-M boolean logic — docs/36 already declared conditional/branching
triggers a non-goal, and this doesn't need to reopen that.

```kotlin
// MapDef.kt
data class GatePlacement(
    val id: GateId,
    val edges: List<WallEdge>,
    val closedSprite: String? = null,
    val openSprite: String? = null,
    val requiredTriggers: Set<TriggerId> = emptySet(), // NEW
)
```

Empty (default) changes nothing — a gate opens only via an explicit
`OpenGate` effect targeting it, exactly as specced above. Non-empty adds a
second, independent way to open the *same* gate: after `fireTriggerIfAny`
(docs/36) updates `state.firedTriggers`, a derived check runs for every gate
with a non-empty `requiredTriggers` — if every id in that set is now present
in `firedTriggers`, the gate's id is added to `openGates` too. The two
mechanisms union, they don't replace each other: an author can wire braziers
to `requiredTriggers` AND also drop an `OpenGate` effect on a master lever
targeting the same gate, either path opens it. Once open, stays open
(one-way, same as every gate) — a brazier being "un-lit" is not a thing v1
models.

No `:designer` UI beyond a multi-select of `TriggerId`s (same "Gate
1"/"Gate 2" index-label precedent, applied to `TriggerPlacement` this time)
in the gate's existing inline editor, alongside its sprite pickers.

## Non-goals (v1)

- No `CloseGate` effect — see "Decided with the user" above.
- No lever/interactable-object trigger source — only the existing
  step-on-a-cell `TriggerPlacement`.
- No raise/lower animation — instant sprite swap on `GateOpened`.
- No authored patrol paths/waypoints for `Wander` — pure random-reachable-tile
  choice, re-rolled every turn the entity can't otherwise act.
- No demo Beast Pen catalog content authored in this pass.
- Diagonal or non-contiguous multi-edge gates — a `GatePlacement`'s `edges`
  are assumed to form one straight contiguous run; the `:designer` grouping
  pass never merges edges of different `Side`s into one gate.
