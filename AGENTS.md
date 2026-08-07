# AGENTS.md — orientation for a coding agent

Read this first. Then read the two or three specs relevant to your task. Do not
read all of `docs/` up front; it is a reference, not a preamble.

## What this is

PocketQuest is a mobile D&D-rules roguelike: top-down grid tactics, short
sessions (5–30 min), runs with meta-progression. Compose Multiplatform —
Android, iOS, Desktop JVM.

The combat core was rewritten from an ECS proof of concept after that design hit
structural walls. `docs/08-migration-from-poc.md` lists the twelve reasons; read
it if you are tempted to reintroduce anything that looks like the old design.

## Repo layout

```
:core:model     GameState, Entity, Effect, GameEvent — pure data
:core:rules     Resolver, handlers, actions, stats, targeting — pure functions
:core:content   JSON catalog loading and validation
:core:ai        Consumer of the resolver; picks actions for AI-controlled entities
:data           Room persistence, snapshot migrations — the only module doing IO
:ui             Compose, Director, AnimationPlayer, VisualWorld
:app            Entry points, DI wiring
```

Dependencies flow strictly downward. **No module may depend on `:ui`.** If
something in `:core` seems to need a UI concept, it is in the wrong place.

## Seven rules that are not negotiable

Breaking any of these is a review rejection, not a style preference. Each exists
because the v1 design broke it and paid for it.

1. **No Compose, no Android, no coroutines, no IO in `:core:model` or
   `:core:rules`.** Enforced by the module graph — keep it that way.
2. **No `kotlin.random.Random` in `:core:rules`.** Every roll goes through
   `RngState` in the state and returns a new one. This is what makes battles
   reproducible from a seed.
3. **No wall-clock time below `:core:meta`.** Idle accrual takes elapsed time as
   an explicit input, computed once on app open.
4. **State is immutable; the engine is a reducer.** `GameState` in, new
   `GameState` plus events out.
5. **Nothing derivable is stored.** `maxHp`, `armorClass`, `speed`, grid
   occupancy are all functions. Position lives on the entity; the grid is an
   index.
6. **Events are past tense and never rejected; effects are imperative and may
   fail.** `DealDamage` is an effect, `DamageTaken` is an event. Do not invent a
   present-tense "about to happen" event — if you need interception, that is the
   damage pipeline (`docs/18`).
7. **Never fail silently.** A rejected action returns `List<Rejection>` with the
   reason. A failed effect emits `Fizzled(effect, reason)`. A bare `return` that
   does nothing is the single worst pattern in the old codebase.

## One code path per question

The UI asks "which tiles can I target?", the AI asks "what would this do?", the
engine asks "is this legal?". All three go through `legalTargets()`,
`canPerform()` and `preview()`. Do not write a second implementation of a rule
for rendering — v1 did exactly that and the highlight and the resolver
disagreed.

`preview()` runs the real resolver in `RngMode.Expected` against an immutable
state and discards the result. That is why preview and execution cannot diverge.

## Which spec to read

| Task | Read |
| --- | --- |
| Anything touching state shape | `02-state-model` |
| Statuses, equipment, buffs, advantage | `03-modifiers-and-status` |
| Resolver, reactions, decisions, turn boundaries | `04-resolver` |
| Actions, targeting, costs, preview | `05-actions-and-effects` |
| Saving, migrations | `06-persistence` |
| Anything animated | `07-animation` |
| Writing tests | `09-test-plan` |
| Run structure, resources, party rules | `10-game-loop`, `11-run-state` |
| Battle screen, targeting UX | `15-battle-ui` |
| Art, tile scaling, map format, designer | `16-art-direction` |
| **What to work on** | `17-engine-gaps` |
| Damage interception, wards, shields | `18-damage-pipeline` |
| Missing art | `19-placeholders` |

`docs/README.md` is the index and lists the five core principles.

## Build and test

```bash
./gradlew :core:rules:desktopTest     # the inner loop — pure JVM, milliseconds
./gradlew :core:model:desktopTest
./gradlew :data:desktopTest           # Room, needs sqlite-bundled
./gradlew :app:run                    # desktop demo
```

`:core:rules` has no Android or emulator dependency by design. If a rules test
needs one, something is wired wrong.

There is no CI yet (gap 3.9). Run the suite before you commit.

## Current state

Passes 1–7 of the spec plus `:core:content`, `:data`, `:ui`, `:core:ai` and the
animation pipeline are implemented — roughly 194 tests, all in place.

What is **not** done, in priority order, is `docs/17-engine-gaps.md`. Summary:

- **Tier 0** — seven real defects in shipped code. Several are silent. Start
  here and get the suite green before anything else.
- **Tier 1** — mana as a per-encounter pool, pathfinding, path-length movement
  cost, terrain cost, downed state, granted actions. Prerequisites for the
  battle UI.
- **Tier 2** — the damage pipeline and taunt.
- **Tier 3** — content primitives, inventory, CI, quality of life.

The recommended next milestone is a **vertical slice**: Tier 0, then 1.1–1.5,
then placeholders plus a random map, then one playable battle on a phone with
three heroes and three enemies. Specification beyond that point has diminishing
returns until someone has actually played a turn.

## Conventions

**Deviating from a spec is allowed, documenting it is mandatory.** The existing
code does this well — see the comment in `Resolver.run()` explaining why
`triggered` goes before `spawn` even though `docs/04` shows the opposite. Follow
that pattern: a comment at the deviation naming the doc and the reason.

**If implementation proves a doc wrong, fix the doc in the same commit.** Three
of the specs have already been corrected this way. A doc that quietly disagrees
with the code is worse than no doc.

**Every `Effect`, `GameEvent`, `Modifier` and `Expiry` subtype needs an explicit
`@SerialName`.** Class names get refactored; serial names must not. A reflection
test enforces this — do not disable it.

**Event order is a contract.** The animation director depends on it. A refactor
that produces the same final state in a different order is a breaking change,
which is why the scenario tests assert golden event lists.

**Package naming: do not use `ecs`.** The design is not an entity-component
system, and the old name misleads. New code goes in `core.model` / `core.rules`.

## Assets

Normalised to a 64 px logical tile: characters upscaled 2× NEAREST from 32 px,
ink props downscaled 70→64 with LANCZOS. `assets.json` is the manifest — frame
size, sheet layout, facings, per-prop footprint. Do not hardcode sheet layouts.

`tools/normalize_assets.py` regenerates the normalised set from the source packs.

Missing art (enemies, ability icons, status pips) is drawn procedurally at
runtime from the content id — see `docs/19`. Never add placeholder PNGs to the
repo.

Character sheets: `walk` and `sprint` are 4×4, four facings (S/W/E/N) × four
frames. `idle` is one frame per facing.

## The v1 reference

The old proof of concept was removed from the tree but is reachable from the
baseline commit (`git show e6e22baf:composeApp`). Worth consulting for the
desktop designer's panel layout and file I/O, and for `AStarPathfinder.kt` and
`LineOfSight.kt`, which need porting (gap 1.2). Nothing else carries over — the
schemas changed completely.

## Things people get wrong here

- Reading `PartyMember.hp` to draw a health bar during combat. The `Entity` is
  authoritative while an encounter is live (`docs/11`, invariant 8).
- Showing a decision prompt before the animation queue drains. The resolver
  returns `AwaitingInput` immediately; the UI must wait (`docs/07`).
- Inferring walkability from artwork. A prop is art; terrain is data
  (`docs/16`).
- Adding a per-frame recomposition to the board. One `Canvas`, culled to the
  viewport, entities positioned via the lambda form of `graphicsLayer`.
- Treating taunt and damage redirection as the same feature. One constrains the
  enemy's choice, the other intercepts the outcome (`docs/18`).
