# 09 — Test plan

Written before the engine, so the engine is built to satisfy it. This is the
implementation contract for the next step.

## Why the tests can be good here

Because `:core:rules` is pure, every test in this plan is a plain JVM unit test
with no emulator, no Robolectric, no coroutine test dispatcher, no mocks. They
run in milliseconds. `./gradlew :core:rules:desktopTest` is the inner loop.

The v1 tests (`CombatSystemTest`, `ConditionTickSystemTest`, …) each build a
`World`, register a system and assert on mutated components. They are fine as
far as they go, but they test one system at a time in isolation — none of them
can express "a move interrupted by an opportunity attack that breaks
concentration", because v1 cannot do that at all.

## Layers

```
1  Pure unit        dice, stats derivation, expiry matching, targeting geometry
2  Effect handler   one effect, one state transition
3  Resolver         multi-effect sequences, reactions, decisions
4  Scenario         full turns, hand-written encounters, golden event lists
5  Property         invariants over randomised command sequences
6  Serialization    round-trip and migration of checked-in snapshots
```

## Fixtures

A small DSL so scenarios read like the rules they encode. This is where
`content/dsl/` gets a second life.

```kotlin
fun scenario(block: ScenarioBuilder.() -> Unit): Scenario

val s = scenario {
    map(10, 10)
    seed(42)
    entity("lyra")  { at(2, 5); archetype("wizard"); hp(18); mana(9); ap(2) }
    entity("gobA")  { at(5, 5); archetype("goblin"); faction(ENEMY) }
    entity("gobB")  { at(7, 6); archetype("goblin"); faction(ENEMY) }
    status("lyra", "bless", concentration = true)
    initiative("lyra", "gobA", "gobB")
}
```

Rules for fixtures:
- Named ids (`"lyra"`) resolve to `EntityId`s; tests never write raw numbers.
- Every scenario is seeded. No test may depend on ambient randomness.
- `checkInvariants()` runs automatically after every scenario step.

## Layer 1 — pure units

**Dice and RNG**
- Same seed, same sequence, across two `RngState` instances.
- `RngState` advances on every roll (no accidental reuse).
- Advantage takes the higher of two rolls; disadvantage the lower.
- `resolveAdvantage`: adv + dis = normal, adv + adv + dis = normal, multiple
  adv = advantage. This is the table from [03](03-modifiers-and-status.md).

**Stats derivation**
- Order: `Add` then `Mul` then `Override`. A test with all three on one stat.
- Two `Override`s resolve deterministically by source order, not by set
  iteration.
- Removing a status recomputes `maxHp`; current HP clamps but does not silently
  heal.
- A ring granting `Add(MaxMana, 1)` raises the reset ceiling and nothing else.

**Expiry matching**
- `EndOfTurnOf(who, round)` fires at that entity's end of turn, not at the
  round end.
- `StartOfTurnOf` at round N does not fire at round N+1 (stale expiry is
  cleaned, not re-fired).
- `OnConcentrationLost` fires for every status sharing the `LinkId`, including
  ones on other entities.

**Targeting geometry**
- `Sphere`, `Cone`, `Line`, `Rect` against hand-drawn expected tile sets.
- `requiresLoS` excludes tiles behind a wall, using the ported
  `hasLineOfSight`.
- `legalTargets` and `affectedBy` agree: every target returned is inside the
  affected set for that point.

## Layer 2 — effect handlers

One test per primitive, each asserting on (new state, emitted events, spawned
effects):

- `DealDamage` reduces HP, emits `DamageTaken`, clamps at 0, emits `Died` at 0.
- `DealDamage` respects resistance and immunity from `Stats`.
- `SpendCost` deducts mana and marks `quickUsed`; fails with `NotEnoughMana`.
- `MoveAlong` moves one tile and re-pushes itself with `index + 1`.
- `ApplyStatus` honours each `StackPolicy` (four tests).
- `StartConcentration` ends any previous link on the same caster.
- Every handler re-validates: a handler given a dead target emits `Fizzled`
  rather than throwing or silently passing.

That last one gets a generic test that iterates all registered handlers.

## Layer 3 — resolver

This layer is where v1 had nothing.

- **Ordering.** `spawn + triggered + rest`: an effect that spawns two children
  runs them before the rest of the stack, in order.
- **Interruption.** `MoveAlong` over four tiles, an opportunity attack on step
  two, movement resumes and finishes on the correct tile.
- **Pause and resume.** `run()` returns `AwaitingInput`; `resume()` with the
  answer completes; the combined event list matches an uninterrupted run plus
  the reaction events.
- **Stale answers.** `resume()` with a `DecisionId` that is not `pending`
  throws. (Guards the double-tap case.)
- **AI answers inline.** A decision with `Controller.Ai` never returns
  `AwaitingInput`.
- **Reaction ordering** follows initiative index then `EntityId`, verified by
  constructing entities in reverse order.
- **One reaction per entity per event.**
- **Depth guard.** Two entities with a mutually triggering reaction hit
  `MAX_REACTION_DEPTH` and throw rather than hang.
- **Loop guard.** A deliberately self-spawning effect trips `MAX_STEPS`.

## Layer 4 — scenarios

The worked example from the design discussion, encoded verbatim, is the
flagship test:

> Round 3. Lyra is concentrating on *bless*, has a staff +1 and a ring. She
> moves four tiles past goblin A, takes the opportunity attack, fails the
> concentration save, then casts *web* on both goblins; A saves, B is
> restrained.

Asserted as a **golden event list** — the exact ordered sequence:

```
TurnStarted(lyra, 3)
StatusExpired(lyra, hasted)
ResourcesReset(lyra, ap=2, mana=9)          ← 9, not 10: hasted expired first
MoveStepped(lyra, (2,5)->(3,5))
MoveStepped(lyra, (3,5)->(4,5))
ReactionTriggered(gobA, opportunity_attack)
AttackRolled(gobA -> lyra, d20=14, mod=+4, ac=15, hit=true)
DamageTaken(lyra, 5, slashing)
ConcentrationCheckRolled(lyra, dc=10, roll=8, success=false)
ConcentrationBroken(lyra, L1)
StatusExpired(ally1, bless)
MoveStepped(lyra, (4,5)->(4,6))
MoveStepped(lyra, (4,6)->(5,6))
ActionStarted(lyra, web)
ResourcesSpent(lyra, ap=1, mana=3)
ConcentrationStarted(lyra, L2)
SaveRolled(gobA, DEX, d20=17, mod=+2, dc=14, success=true)
SaveRolled(gobB, DEX, d20=6,  mod=+2, dc=14, success=false)
StatusApplied(gobB, restrained, OnConcentrationLost(L2))
StatusExpired(gobA, marked)
TurnEnded(lyra)
```

Golden lists are the right tool here because **event order is the contract**
the animation layer depends on. A refactor that produces the same final state
in a different order is a breaking change, and only this kind of test catches
it.

Other scenarios worth encoding early:
- Turn boundary order: buff granting +1 AP expires at start of turn → reset
  gives base AP, not base + 1. (Issue found while walking through the example.)
- Concentration transfer: casting a second concentration spell ends the first
  and removes its statuses from all entities.
- Death mid-action: a multi-target spell where target 1 dies before target 2
  resolves; target 2 still resolves, no crash.
- Preview equals execution: run `preview()` in `Expected` mode and the real
  action on a fixed seed; assert the damage range from the preview contains the
  actual result.

## Layer 5 — properties

Randomised command sequences, invariants from
[02](02-state-model.md#invariants) checked after every step:

- No two entities share a tile, ever.
- HP, AP and mana stay inside their derived bounds.
- No entity in `turn.order` is missing from `entities`.
- The resolver always terminates in `Completed`, `AwaitingInput` or `Rejected`
  — never throws on a *legal* command.
- **Determinism**: the same seed and command sequence produce byte-identical
  final states across two independent runs.
- **Rejection soundness**: if `canPerform` returns empty, `perform` does not
  return `Rejected`. And the converse — a non-empty rejection list means the
  state is unchanged.

The last pair is the highest-value property in the suite: it is what keeps the
UI's greyed-out buttons honest.

## Layer 6 — serialization

- `GameState` round-trips: `decode(encode(s)) == s`, as a property over
  generated states.
- `Resolver` round-trips **with a non-empty stack and a pending decision** —
  the process-death case from [06](06-persistence.md).
- Every `Effect`, `GameEvent`, `Modifier` and `Expiry` subtype has an explicit
  `@SerialName`. Enforced by a reflection test that fails on a missing
  annotation, so a new subtype cannot be added without one.
- Golden snapshots: files under
  `core/rules/src/commonTest/resources/snapshots/` load through
  `SnapshotMigrations` and produce the expected state. One file per historical
  schema version, added whenever `CURRENT_SCHEMA` increments.

## Order of implementation

Follow this and each step has a green suite before the next begins:

1. Fixture DSL + `checkInvariants` (test infrastructure first)
2. `RngState` and dice — layer 1
3. `Stats` derivation and expiry — layer 1
4. Effect handlers for `DealDamage`, `MoveAlong`, `SpendCost`, `ApplyStatus` — layer 2
5. Resolver loop, `Ask`, `resume` — layer 3
6. Reaction collection and guards — layer 3
7. Targeting and `canPerform` — layer 1 + 2
8. The flagship scenario — layer 4
9. Properties — layer 5
10. Serialization — layer 6

## Coverage targets

Not a percentage. Two concrete rules:

- Every `Rejection` variant is produced by at least one test. An unreachable
  rejection is dead code or a missing check.
- Every `GameEvent` variant appears in at least one golden list. An event no
  test produces is an event the director will never be exercised against.
