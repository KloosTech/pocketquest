# 08 — What v1 got stuck on, and what replaces it

Written against the state of `main` at the time this branch was created. This
is not a criticism of the PoC — it did its job, which was to find these walls.

## The twelve issues

### 1. Mutable `World`, no state snapshots

`ecs/core/World.kt` holds `HashMap`-backed component stores mutated in place.
There is no "before" to compare against and no way to copy the state cheaply.

Blocks: undo, replay, save mid-action, action preview, AI lookahead.
Every one of those is a feature we want.

**→** Immutable `GameState` + reducer ([02](02-state-model.md)).

### 2. Systems register handlers in `init` and mutate inside them

`MovementSystem`, `CombatSystem` etc. subscribe in their constructors and do
their work inside the callback; `update()` is `= Unit` for several of them. The
control flow is invisible — you cannot read the code and know what happens in
what order.

Worse, the ordering is *load-bearing and implicit*. From
`AnimationEventCollector`:

> `// Unit movement: registered BEFORE MovementSystem so pre-move position is still in world`

The correctness of the animation depends on constructor call order in
`BattleSystemFactory`. Nothing enforces it and nothing fails if it changes.

**→** Explicit effect stack; order is data you can print
([04](04-resolver.md)).

### 3. Silent rejection

`MovementSystem` has six `return@on` paths: dead entity, no MP, no position,
out of range, unwalkable tile, occupied tile, insufficient MP for terrain cost.
All of them do nothing, log nothing and tell the player nothing.

**→** `canPerform(): List<Rejection>` before execution, `Fizzled(effect,
reason)` events during ([05](05-actions-and-effects.md)).

### 4. The `pendingProjectile` correlation hack

`AnimationEventCollector` holds a single nullable `pendingProjectile` because
`SkillUsedEvent` does not know whether the attack hit; it waits for a following
`DamageEvent`, `HealEvent` or `MissEvent`.

This is broken today for `PlayerAction.UseSkillOnTargets`, which emits N
`SkillUsedEvent`s into a one-slot field — only the last survives.

**→** `AttackRolled` carries `hit`, roll, modifier and target AC. Nothing to
correlate ([07](07-animation.md)).

### 5. No reactions, no interruption

`GameLoop.runPlayerTurn()` emits, runs the phase's systems, flushes and
returns. There is no mechanism to pause mid-action and ask a *different* entity
for input. Opportunity attacks, counterspell, shield and readied actions are
all unimplementable.

**→** `StepResult.AwaitingInput` + serializable resolver ([04](04-resolver.md)).

### 6. Canvas coordinates baked into logic events

`AnimationEvent.UnitMove` carries `fromNormX: Float` etc., computed inside
`AnimationEventCollector` via `gridToNormX()`. The collector holds mutable
`gridCols` / `gridRows` that `BattleViewModel` writes to whenever the tile map
changes.

Game logic should not know a canvas exists, and a mutable field poked from
outside is a race waiting for a second caller.

**→** Events carry `GridPos`; conversion happens at render time
([07](07-animation.md)).

### 7. Single player character is baked into the model

`GameLoop.getActivePlayer()` and `snapshotBattle()` both do
`.filter { it.faction == PLAYER }.firstOrNull()`. A party of two is not a
feature to add — it is a data model change.

**→** `TurnState.order` with `activeIndex`; `Controller` decides who answers
([02](02-state-model.md)).

### 8. Side phases instead of initiative

`TurnPhase.PlayerPhase / EnemyPhase / EnvironmentPhase` cannot express
"goblin, player, goblin" ordering, which is what D&D initiative actually
produces.

**→** `TurnPhase` becomes `Start / Main / End` *within* one entity's turn; the
order across entities is `TurnState.order`.

### 9. Stats stored, not derived

`StatsComponent` is a stored component and `ConditionsComponent` is separate. A
condition that should change AC has nowhere correct to write, so it either
mutates the stored stats (unrecoverable) or is ignored.

**→** `Stats` derived from archetype + equipment + statuses on read
([03](03-modifiers-and-status.md)).

### 10. Preview and execution are separate implementations

`snapshotBattle()` computes `attackableTiles` with its own range check,
`hasLineOfSight` and mana comparison. `SkillResolverSystem` decides
independently whether the skill resolves. Two implementations of one rule; they
will drift.

Same story for `reachableTiles`, which reimplements the movement legality rules
that `MovementSystem` also owns.

**→** `legalTargets()` and `canPerform()` are the single source; preview runs
the real resolver in `RngMode.Expected` ([05](05-actions-and-effects.md)).

### 11. No RNG in state

Dice come from ambient randomness. Battles cannot be reproduced, tests need
mocking, and a mid-combat save/load can re-roll a different result.

**→** `RngState` in `GameState`; every roll returns a new one
([02](02-state-model.md)).

### 12. One module, no enforced boundary

Everything is in `:composeApp`. `AnimationEventCollector` sitting in
`game/animation/` while computing view coordinates is the symptom.

**→** `:core:model` / `:core:rules` as Compose-free KMP modules
([01](01-modules.md)).

## What is worth keeping as-is

Not everything needs replacing. These port over with little or no change:

| Keep | Notes |
| --- | --- |
| `game/pathfinding/AStarPathfinder.kt` | Pure function already. Move to `:core:rules`, swap `PositionComponent` for `GridPos`. |
| `game/pathfinding/LineOfSight.kt` | Same. `computeVisibleTiles` becomes a rules query used by both FoW and targeting. |
| `content/map/TileMap.kt`, `TileType.kt` | Good shape. Becomes `BattleMap` in `:core:model`. |
| The map JSON and the designer | Format is fine. Only the action/enemy schema changes. |
| `content/dsl/*` | Demote to test fixtures; excellent for that. |
| `data/db/*`, `SeenHintDao` | Room usage is already sensible for metadata. |
| `game/hint/*`, overworld, navigation | Untouched by this rewrite. |
| `BattleTileCache` | Rendering concern, unaffected. |

The rewrite is scoped to the combat core. The overworld, campaign, designer and
asset pipeline are out of scope.

## Migration order

Each step leaves `main` playable.

1. **Modules.** Create `:core:model` and `:core:rules`, empty, wired into
   `settings.gradle.kts`. No dependents.
2. **Model + tests.** `GameState`, `Entity`, `Stats`, `RngState`,
   `checkInvariants`. Tests first — see [09](09-test-plan.md).
3. **Resolver + tests.** Effect stack, `MoveAlong`, `DealDamage`, `Ask`,
   reaction collection. Still no UI.
4. **Actions + catalog.** `ActionDef`, targeting, `canPerform`, preview.
   Port two or three real skills from `content/definitions/Skills.kt`.
5. **Port pathfinding and LoS** into `:core:rules`.
6. **Persistence.** `Resolver` serialization, `SaveSlotRow`, migration harness.
7. **Second battle screen** behind a debug flag, new engine, new Director.
   Both engines coexist and can be compared side by side.
8. **Content migration.** Convert enemy and skill JSON to the new schema; update
   the designer's writers.
9. **Delete** `ecs/`, `game/systems/`, `game/loop/`, `game/animation/`, the old
   `BattleScreen` path.

Steps 2–4 are pure Kotlin with no emulator in the loop, which is why they are
fast. Step 7 is the point where this stops being a rewrite with nothing to show.

## A note on the name `ecs`

The new design is not an entity-component-system: there are no systems
iterating component arrays, and components are typed nullable fields rather
than a dynamic map. Keeping the `ecs` package name would mislead every future
reader, including us. New packages are `core.model` / `core.rules`
([01](01-modules.md#package-naming)).
