# 17 — Engine gaps

Everything the game loop needs that `:core:rules` does not have yet, plus the
defects found reviewing the pass 1–7 implementation. Ordered by dependency, not
by size.

Each item names what breaks without it, so this can be triaged rather than
worked top to bottom.

---

## Tier 0 — bugs in what already exists

These are wrong today, independent of any new feature.

**0.1 `settle()` resurrects corpses.** `Reconciliation.settle` does
`v.alpha.snapTo(1f)` for every logical entity, but `Died` faded alpha to 0. Dead
tokens reappear after every drain. Skip alpha for entities at 0 HP. Same
function places entities with `pos == null` at `Offset.Zero`, drawing reserve
units in the top-left corner.

**0.2 Reactions bypass cost validation.** `acceptReaction()` never calls
`canPerform`. `SpendCost` then fizzles on insufficient mana, but a fizzled
effect does not abort the effects behind it, so the reaction resolves for free.
Either gate `acceptReaction` with `canPerform`, or make a failed `SpendCost`
cancel its sibling effects — the latter is the more general fix and applies to
`perform()` too.

**0.3 `reactedTo` dedupes on structural event equality.** Two occurrences of
`MoveStepped(lyra, (4,5), (4,6))` in one battle are the same key, so the second
silently gets no reaction offer. Add an ordinal — `state.version` in
`ReactedKey` is enough. The set is also never cleared and rides along in every
snapshot.

**0.4 `depth` only increases.** `depth = if (triggered.isNotEmpty()) r.depth + 1
else r.depth`, never decremented, never reset. It is a per-`run()` budget of 8
reaction *waves*, not a nesting depth, so a long turn can exhaust it and
silently stop offering reactions. `collectTriggers` returns empty at the limit;
doc 09 specified throwing.

**0.5 `ActionStarted` bypasses `collectTriggers`.** `perform()` seeds it into
`emitted` directly, so a counterspell-style reaction to `ActionStarted` can
never fire — even though `ReactionTriggerKind.ActionStarted` and
`targetsFor(ActionStarted)` both exist.

**0.6 `endTurn` divides by zero** on an empty `turn.order`, and dead entities
still take full turns with a resource reset. Skip entities at 0 HP.

**0.7 `answererFor` defaults to `HumanUi`** for a null controller. A barrel with
a reaction would stall the resolver waiting for input that never comes. Return a
"never reacts" answerer instead.

**0.8 `Fizzled` uses `effect::class.simpleName`.** R8 renames it in release
builds; golden tests pass in debug and the battle log turns to noise in
production. Use the `@SerialName`.

**0.9 Expected mode reports contradictions.** `rollD20` returns 10.5, the event
records `d20 = 10`, but the hit was decided on 10.5 — a preview can emit
`d20=10, mod=+4, ac=15, hit=true`. Expected mode also ignores advantage
entirely (should be ≈13.8), so previews systematically understate it.

**0.10 `Modifier.Roll` is dead code.** It and `RollContext` are modelled and
serialized, but `stats()` drops them and `Stats` has no advantage surface. A
status granting advantage is currently inert.

---

## Tier 1 — prerequisites for the loop

Nothing in docs 10–16 works without these.

**1.1 Mana becomes a per-encounter pool.**
`ResourcesReset` must stop restoring mana at the turn boundary; `finishEncounter`
restores it instead ([10-game-loop.md](10-game-loop.md)). Small change, large
consequence: without it no spell can be rationed across a fight.
*Test:* mana unchanged across a turn boundary, full after `finishEncounter`.

**1.2 Pathfinding.** A\* over walkable tiles with a cost budget. Never ported
from v1, where `AStarPathfinder.kt` already existed. Blocks the entire
tap-to-move interaction in [15-battle-ui.md](15-battle-ui.md) — nothing
currently computes the path `MoveAlong` consumes.

**1.3 Path-length movement cost.** `ActionCost.Movement(tiles)` is a static
number in the `ActionDef`, but tap-to-move costs whatever the chosen path costs.
Movement needs to price itself from the resolved path. Depends on 1.2.

**1.4 Terrain cost.** `BattleMap` is uniform walkable/blocked with no
`TileType`. Difficult terrain has nowhere to live. Depends on nothing; blocks 1.3
being meaningful.

**1.5 Downed state.** `health.current == 0` emits `Died` and that is all. Needs a
`Downed` condition: cannot act, still occupies its tile, revivable, and the run
fails only when every party member is down
([10-game-loop.md](10-game-loop.md)).

**1.6 `Entity.grantedActions`.** `Archetype.actions` is the only action source,
so a levelling hero cannot gain abilities. Needed by `Progression.features`
([11-run-state.md](11-run-state.md)).

**1.7 Feature modifiers in `stats()`.** Level features are a `ModifierSource` and
must join the fixed source order — after archetype innate, before equipment.
Depends on 1.6.

---

## Tier 2 — the tank

Two separate mechanisms that are easy to conflate. See
[18-damage-pipeline.md](18-damage-pipeline.md#taunt-is-a-different-mechanism--do-not-conflate-them).

**2.1 The damage pipeline.** `DealDamage` becomes an ordered chain —
retarget, prevent, convert, scale, reduce, absorb, apply, after — instead of one
step that writes HP immediately. Full spec in
[18-damage-pipeline.md](18-damage-pipeline.md). Also folds in two things that
are wrong or unused today: resistance is hardcoded in the handler, and
`Health.temp` is never read anywhere.

**2.2 `DamageRedirected` event.** Without it the animation director sees damage
land on a character nobody attacked. Depends on 2.1.

**2.3 Taunt.** A `Flag.Taunted` that narrows `:core:ai`'s candidate targets
before it picks an action. Independent of 2.1 — this constrains the enemy's
*choice*, the pipeline intercepts the *outcome*.

**2.4 Absorb pools.** `Health.temp` wired as the Absorb step's pool, plus the
rule for what happens when two shields overlap. Depends on 2.1.

---

## Tier 3 — content and quality of life

**3.1 Missing effect primitives.** `Push`, `Teleport`, `SpawnEntity`,
`DestroyEntity` from doc 05 are unimplemented.

**3.2 `Behavior` / `BehaviorId`.** Doc 03's escape hatch for content that cannot
be expressed as data. Entirely absent. The damage pipeline reduces how badly it
is needed, which is a point in the pipeline's favour.

**3.3 `saveEnds` / `SaveSpec`.** Declared on `ActiveStatus`, never consumed. No
repeat saves at a moment.

**3.4 Inventory.** Doc 03 says inventory is stored separately from `Equipment`;
nothing implements it. Needed by loot and potions.

**3.5 `stats()` memoisation.** Doc 02 asked for a per-`(EntityId, version)`
cache. It is called once per handler *and* once per entity in
`checkInvariants`. Not urgent, but it is the obvious hot spot when the board
grows.

**3.6 Crits and fumbles.** No natural-20 or natural-1 handling in `rollAttack`.

**3.7 `AnimationPlayer.skipAll()`.** Specified in doc 07, not implemented. Needed
before AI turns can be skippable, which doc 10's pacing assumes.

**3.8 `Answerer.Auto`.** Per-reaction policy ("never ask about opportunity
attacks"). Doc 04 called it a usability need; omitted because no content drove
it. The ward in doc 18 sidesteps this by being passive, but active intercepts
will want it.

**3.9 CI.** No `.github/workflows`, no runner. The whole point of a pure,
millisecond-fast rules suite is a gate on every push.

---

## Suggested order

Tier 0 first — 0.1 through 0.6 are small and several are silent, which makes
them expensive to find later. Then 1.1 (one-line change, unblocks the entire
resource design), then 1.2–1.4 as a block, since the battle UI cannot be built
without movement.

The damage pipeline (2.1) is the largest single item here and the one most worth
designing before typing. It touches every existing damage test, so land Tier 0
first and get the suite green.

3.9 is out of order on purpose: it costs an hour and pays back on everything
below it.
