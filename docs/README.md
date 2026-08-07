# PocketQuest — Core Architecture v2

This directory is the design reference for the rewrite of the combat core.
It is written **before** the implementation on purpose: the v1 proof of concept
(`ecs/`, `game/systems/`, `game/loop/`) works, but it hit a set of structural
walls that cannot be fixed incrementally. See
[08-migration-from-poc.md](08-migration-from-poc.md) for the concrete list.

Read in order:

| Doc | Topic |
| --- | --- |
| [01-modules.md](01-modules.md) | Gradle modules, dependency rules, what may import what |
| [02-state-model.md](02-state-model.md) | `GameState`, `Entity`, derived stats |
| [03-modifiers-and-status.md](03-modifiers-and-status.md) | Equipment and status effects as one mechanism |
| [04-resolver.md](04-resolver.md) | The effect stack, reactions, decision handling |
| [05-actions-and-effects.md](05-actions-and-effects.md) | Actions as data, targeting, preview |
| [06-persistence.md](06-persistence.md) | Snapshots, Room's actual role, versioning |
| [07-animation.md](07-animation.md) | Director, beats, reconciliation |
| [08-migration-from-poc.md](08-migration-from-poc.md) | v1 problems → v2 answers, migration order |
| [09-test-plan.md](09-test-plan.md) | What we test and how, before writing engine code |

Game design and product layer:

| Doc | Topic |
| --- | --- |
| [10-game-loop.md](10-game-loop.md) | Run structure, the three layers, resource economy, party rules |
| [11-run-state.md](11-run-state.md) | `RunState` above `GameState`, encounter handoff, resume |
| [15-battle-ui.md](15-battle-ui.md) | Screen anatomy, targeting state machine, prompts, camera |
| [16-art-direction.md](16-art-direction.md) | Visual language, scaling rules, map resources, the designer |
| [17-engine-gaps.md](17-engine-gaps.md) | Everything the loop needs that the engine lacks, by dependency |
| [18-damage-pipeline.md](18-damage-pipeline.md) | Interception chain: redirect, absorb, reflect, resistance |

| [19-placeholders.md](19-placeholders.md) | Generated placeholder art and the missing-asset registry |

Not yet written: 12 (progression), 13 (encounters and events), 14 (UI shell).

---

## The five principles

Everything in these documents follows from these. When a design question comes
up that is not covered here, resolve it by asking which principle applies.

### 1. The rules engine knows nothing about time, Compose, or IO

`:core:rules` is a pure function library. No coroutines, no `Flow`, no
`androidx.*`, no clock, no file access. It takes a state and a command and
returns a new state plus a list of events. This is enforced by the module
graph, not by discipline — see [01-modules.md](01-modules.md).

### 2. State is immutable; the engine is a reducer

`GameState` is an immutable `data class`. Every transition produces a new
instance. This buys undo, replay, save/load, AI lookahead and trivial tests in
one move. A mutable `World` cannot do any of these, which is the single
biggest limitation of v1.

### 3. Animation is driven by events, not by diffing states

The engine emits an ordered list of `GameEvent`s describing *what happened, in
what order*. Diffing "before" and "after" loses causality: you can see that HP
dropped, but not why, by whom, or in which order relative to a move. The event
list is the animation script.

### 4. Nothing derivable is stored

`maxHp`, `armorClass`, `speed`, and the grid occupancy map are all functions of
other state. Storing them creates a second source of truth that will drift.
Position lives on the entity; the grid is an index built on read.

### 5. One code path per question

The UI asks "which tiles can I target?", the AI asks "what would this action
do?", and the engine asks "is this legal?". All three go through the *same*
functions (`legalTargets`, `canPerform`, the resolver in preview mode). Two
implementations of the same rule will diverge — v1 already has this bug, where
`snapshotBattle` reimplements targeting separately from `SkillResolverSystem`.

---

## The pipeline, end to end

```
  User taps a tile
        │
        ▼
  Command (data)                       :ui
        │
        ▼
  canPerform() ──► List<Rejection>     :core:rules   (no state change)
        │ ok
        ▼
  Resolver loop over an effect stack   :core:rules   (pure, deterministic)
        │
        ├──► StepResult.AwaitingInput ──► decision dialog ──► resume()
        │
        ▼
  StepResult.Completed(state, events)
        │
        ├──► GameState  ──► SaveRepository ──► Room     :data
        │
        └──► List<GameEvent>
                  │
                  ▼
             choreograph()  ──► List<Beat>              :ui  (Director)
                  │
                  ▼
             AnimationPlayer ──► VisualWorld ──► Compose :ui
                  │
                  ▼
             settle(logicalState)     ← self-healing reconciliation
```

Note what is *not* in this diagram: the database is a sink, never a source
during play. Compose never reads `GameState` directly for entity positions —
it reads `VisualWorld`, which lags behind on purpose.

---

## Vocabulary

These words have exactly one meaning in this codebase. Using them loosely is
how the v1 `AnimationEvent` / `GameEvent` confusion happened.

| Term | Meaning |
| --- | --- |
| **Command** | A player or AI *intent*. May be rejected. `MoveTo`, `UseAction`. |
| **Effect** | One atomic rules operation on the stack. `DealDamage`, `MoveAlong`. |
| **GameEvent** | A record that something *did* happen. Past tense. Never rejected. |
| **Beat** | A unit of animation with a timing mode. Produced from a `GameEvent`. |
| **Decision** | An answer to a `DecisionRequest` the engine paused for. |
| **Archetype** | Static per-kind data (a goblin's base stats). Never mutated. |
| **Entity** | A runtime instance with position, current HP, statuses. |
| **Stats** | A derived, flattened snapshot of an entity's effective numbers. |

Tense is the quick test: commands and effects are imperative
(`DealDamage`), events are past tense (`DamageTaken`).
