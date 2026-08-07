# Known issues

Tracked findings from an external spec-analysis pass, each independently
verified against the current code (not taken on faith). Status legend:

- **Confirmed** — reproduced by reading the exact code path; real bug.
- **Confirmed (scope corrected)** — real, but the report's framing overstated
  or misdescribed the scope; corrected description included.
- **Confirmed, latent** — real gap, but currently unreachable because nothing
  in the codebase yet exercises the precondition. Will bite the first feature
  that does.
- **Deliberate tradeoff** — already a known, comment-documented simplification
  from an earlier pass, not a fresh oversight. Still worth reconsidering, but
  not "someone forgot this."

Fix as their own passes, not bundled — each has independent test-coverage
implications.

---

## 1. Movement never animates

**Status: Fixed.** `choreograph()` now has a `MoveStepped` branch
(`Beat(Timing.Blocking) { it.walk(event.who, event.to) }`), animating
`pos` via `animateTo`. Regression test:
`DirectorTest.moveSteppedProducesABlockingWalkBeat`.

`choreograph()` in `ui/src/commonMain/kotlin/de/jackbeback/pocketquest/ui/Director.kt`
has no `GameEvent.MoveStepped` branch — it falls through `else -> emptyList()`.
`settle()` (`Reconciliation.kt`) snaps a moved token straight to its final
tile with no interpolation in between.

Was not a regression when reported — no pass had implemented it yet
(doc07's animation pass covered Attack/Damage/Heal/Died/StatusApplied
only). It was the one animation the whole `VisualEntity.pos:
Animatable<Offset>` design exists for.

---

## 2. Corpses come back, and reserve units render in the corner

**Status: Fixed** (both 2a and 2b). `VisualWorld.settle()` now forces
`alpha` from `health.current` instead of snapping it to `1f`
unconditionally, and both `settle()` and `VisualWorld`'s init block skip
entities with `pos == null` entirely instead of defaulting to
`Offset.Zero`. Regression tests:
`ReconciliationTest.settleFadesADeadEntityEvenIfNoDeathBeatEverRan`,
`settleRestoresAliveAlphaEvenIfLeftMidFade`,
`reserveEntityWithNullPosGetsNoVisualEntity`,
`settleRemovesAVisualEntityThatMovedToReserve`.

Original findings (two related bugs in `Reconciliation.kt`/`App.kt`):

**2a — resurrection.** `VisualWorld.settle()`
(`ui/src/commonMain/kotlin/de/jackbeback/pocketquest/ui/Reconciliation.kt:18`)
does:

```kotlin
v.alpha.snapTo(1f)
```

unconditionally for every entity still in `logical.entities` — which
includes dead ones, since nothing removes a dead entity from `GameState.entities`
(no `DestroyEntity` effect exists yet). The `Died` beat
(`Director.kt`) fades `alpha` to `0f`; the very next `settle()` call (which
runs after every drain, per doc07) snaps it back to `1f`, undoing the fade.
Introduced in the doc07 animation pass — never exercised because the demo
scenario has no `Died` event, exactly the "nothing has tested this path yet"
trap this project has hit before (pass 7's serialization bug, same shape).

**Fix direction:** skip the `alpha.snapTo(1f)` (and probably the `pos`/`hp`
snaps too) for entities with `health.current <= 0`, or drive a dead entity's
final alpha from its health instead of a blanket reset.

**2b — reserve units in the corner.** Both `VisualWorld`'s init block
(`VisualWorld.kt:52`) and `settle()` (`Reconciliation.kt:14`) do
`e.pos?.toOffset(tilePx) ?: Offset.Zero`. doc02 states `pos: GridPos? ...
null = not on the map (reserve, dead)` — so a reserve unit is a real,
documented state, and it would currently render at the top-left corner of
the board instead of not rendering at all.

**Status detail: latent.** Nothing in the codebase currently produces an
entity with `pos == null` at runtime (no reserve-roster feature, and death
doesn't null out `pos` either) — this is correct-per-doc but unreachable
until reserves or off-map entities exist.

**Fix direction:** skip drawing (and skip creating a `VisualEntity` for)
any entity whose `pos` is null, rather than defaulting to a fake position.

---

## 3. Reactions are free when you can't pay

**Status: Fixed.** `acceptReaction()` now checks the reactor's mana
against the reaction's cost *before* spawning `spend + instantiated`,
returning a `Fizzled` outcome (via the same `fizzle()` helper `spendCost`
itself uses) instead of committing to effects it can't pay for.
`canPerform` itself couldn't be reused here — its `NotYourTurn` check
assumes the caster is the active entity, which a reactor by definition
never is. Mana is the only cost a Reaction-cost action can have (a
Reaction's `SpendCost` never carries an `ap` component — see
`Perform.kt`'s `initialStack`), so that's the only check needed.
Regression tests:
`ReactionsTest.acceptReactionFizzlesWithoutRunningEffectsWhenReactorCannotAffordTheCost`
and `...StillRunsWhenReactorCanAffordTheCost`.

Original finding: `acceptReaction()` (`Handlers.kt:306`) never called `canPerform`:

```kotlin
private fun acceptReaction(state: GameState, who: EntityId, actionId: ActionId, trigger: GameEvent, cat: Catalog): HandlerOutcome {
    val def = cat.actionDef(actionId)
    val ctx = ActionCtx(who, targetsFor(trigger))
    val spend = Effect.SpendCost(who, mana = def.cost.mana, markReactionUsed = true)
    val instantiated = def.effects.flatMap { it.instantiate(ctx, cat) }
    return HandlerOutcome(state, spawn = listOf(spend) + instantiated)
}
```

`matchingReaction()` (`Reactions.kt:105`) only checks `reactionUsed`,
`health.current > 0`, and targeting geometry — never mana. `spendCost()`
(`Handlers.kt:167`) rejects on insufficient mana by calling `fizzle()`
(`Handlers.kt:96`), which just emits a `Fizzled` event and returns the
state **unchanged** — it does not remove the rest of the stack.
`instantiated` (the reaction's actual damage/effect) is sitting right
behind `spend` on the stack and runs on the very next step regardless of
whether `spend` fizzled.

`perform()`'s own comment
(`core/rules/src/commonMain/kotlin/de/jackbeback/pocketquest/core/rules/action/Perform.kt:19`)
states the precondition this relies on: *"Cost-as-first-effect only works
because perform() gates with canPerform first."* The reaction path
(`offerReaction`/`acceptReaction`) has no equivalent gate.

**Fix direction:** `acceptReaction` needs its own legality check (at
minimum a mana check) before spawning `spend + instantiated` — either a
`canPerform`-style call adapted for reactions, or an explicit
`if (resources.mana < def.cost.mana) return HandlerOutcome(state)` guard.

---

## 4. `reactedTo` dedups on structural equality of the event

**Status: Confirmed (scope corrected).**

`ReactedKey(val who: EntityId, val event: GameEvent)`
(`Reactions.kt:23`) uses the raw `GameEvent` data class for equality.
Two structurally-identical events for the same entity — not just
`MoveStepped` with the same from/to, any event type with the same field
values — collide and the second occurrence silently gets no reaction
offer, because `collectTriggers` (`Reactions.kt:141`) filters reactors via
`ReactedKey(it.id, event) !in reacted`.

**Scope correction:** the report frames this as "twice in a battle." In
the current architecture that's narrower: `Resolver.reactedTo` starts
`emptySet()` on every fresh `Resolver(...)` construction, and every
`perform()`/`Effect.EndTurn` call builds a **new** `Resolver`
(`Perform.kt:36`, `:app`'s `Main.kt` turn loop) — so the dedup set is
scoped to **one action's full effect resolution** (including any chained
reactions within it), not the whole battle. The collision is real but
requires the *same entity* to produce *two structurally-identical events*
within *one resolver run* — plausible (a repeating/looping effect, two
separate but coincidentally-identical `MoveAlong` segments, a multi-hit
weapon dealing the exact same damage twice) but not "any repeat anywhere
in the fight."

Separately, the report's ride-along-in-every-snapshot point: `reactedTo`
is real serialized state on `Resolver` (`Resolver.kt:40`), so yes it's
part of a persisted mid-decision snapshot — but that's actually
*necessary*, not incidental: a resumed reaction dialog must not re-offer
a reaction already dedup'd before the process died. This only becomes a
real growth concern if a future change makes one `Resolver` span multiple
actions/turns; today it resets every `perform()` call.

**Fix direction:** add an ordinal to `ReactedKey` — `state.version` at the
moment of the event is already threaded everywhere and would work.

---

## 5. Reaction depth: counts total waves, not nesting, and doesn't throw at the limit

**Status: two sub-findings, different severity.**

**5a — `depth` is a per-run() wave counter, not nesting depth.** This part
is a **deliberate, already-documented tradeoff**, not a fresh oversight —
`Resolver.kt:24`'s own doc comment says so explicitly: *"a single
resolver-wide counter bumped once per wave of newly-offered reactions — a
simplification of doc04's per-OfferReaction depth field, still sufficient
to stop a mutual-reaction loop."* The real risk the report identifies is
legitimate, though: a single big multi-target action (say, one fireball
hitting five independent reactors, each triggering unrelated downstream
reactions) could rack up several waves without any actual runaway loop,
and `MAX_REACTION_DEPTH = 8` (`Reactions.kt:19`) is shared budget across
the whole call, not per reaction-chain branch. Worth reconsidering if
content ever gets that reaction-dense; not urgent today since nothing in
the current catalog comes close.

**5b — silently stops instead of throwing.** This part is **confirmed, no
documented justification found.** `collectTriggers`
(`Reactions.kt:134`):

```kotlin
if (depth >= MAX_REACTION_DEPTH) return emptyList<Effect>() to alreadyReacted
```

doc09's own test-plan spec (`docs/09-test-plan.md`, Layer 3) says: *"Depth
guard. Two entities with a mutually triggering reaction hit
MAX_REACTION_DEPTH and throw rather than hang."* The existing test
(`ReactionsTest.kt:118`, `collectTriggersReturnsNothingAtMaxDepth`) asserts
the silent-empty behavior directly, with no comment explaining the
deviation from doc09 — unlike 5a, there's no sign this was a conscious
choice. Silent truncation is exactly the failure mode doc04's guard-rail
philosophy (`MAX_STEPS` throws, `resume()` with a stale id throws) exists
to avoid.

**Fix direction:** change the depth-limit branch to `check(depth <
MAX_REACTION_DEPTH) { ... }` (throw) to match doc09, and update
`collectTriggersReturnsNothingAtMaxDepth` to `assertFailsWith` instead.

---

## 6. Counterspell (or any `ActionStarted` reaction) can never fire

**Status: Confirmed.**

`perform()` (`Perform.kt:36`):

```kotlin
val initial = Resolver(state, stack = initialStack(...), emitted = listOf(GameEvent.ActionStarted(caster, actionId)))
```

`ActionStarted` is placed directly into the resolver's `emitted` log as
the Resolver's *initial value* — it never passes through
`collectTriggers()`, which only runs inside `run()`'s loop over each
stack effect's own output events (`Resolver.kt:66`,
`collectTriggers(out.state, out.events, ...)`). The seeded initial
`emitted` list is never fed back into that call.

The rest of the plumbing already exists and is unused for this:
`ReactionTriggerKind.ActionStarted` (`ReactionTrigger.kt`) and
`targetsFor(ActionStarted)` (`Reactions.kt:75`) are both implemented — a
Counterspell-style reaction (`reactionTrigger.kind ==
ReactionTriggerKind.ActionStarted`) would match `matchingReaction()` just
fine if only the event reached it.

**Fix direction:** push `ActionStarted` through the stack/trigger pipeline
instead of pre-seeding `emitted` — e.g. make it the actual first
"effect-shaped" event processed by `run()` (a synthetic zero-cost step
whose only job is to emit `ActionStarted` and let `collectTriggers` see
it), rather than a value the Resolver starts with.

---

## 7. Turn boundary: divide-by-zero on empty order, dead entities still get turns

**Status: Fixed.** `endTurn()` now: (1) `check()`s `turn.order` is
non-empty before the `% order.size` — throws with a clear message
instead of an `ArithmeticException`; (2) advances past any dead entity
(`health != null && health.current <= 0`) in a loop instead of always
taking `activeIndex + 1` — a dead entity gets no `TurnStarted`/
`ResourcesReset`/status-tick, silently, since no `GameEvent` exists yet
for "this entity's turn was skipped" and inventing one was more than
this fix needed; (3) `check()`s the skip-loop doesn't run more than
`order.size` times, so a wholly-dead order throws rather than hanging.
Chose silent-skip over a design alternative (e.g. removing dead entities
from `order` outright) since nothing currently removes entities from
`GameState.entities` either — this stays consistent with "nothing is
removed until `DestroyEntity` exists," just consistently skipped.
Regression tests: `TurnBoundaryTest.endTurnSkipsADeadEntityAndGivesThe
TurnToTheNextLivingOne`, `...ThrowsRatherThanDivideByZeroOnEmptyOrder`,
`...ThrowsWhenEveryEntityInOrderIsDead`.

Original finding (neither path was reachable yet, both were unguarded):

`endTurn()` (`Handlers.kt:383`):

```kotlin
val order = working.turn.order
val nextIndex = (working.turn.activeIndex + 1) % order.size   // line 396 — ArithmeticException if order is empty
...
val nextActiveId = order[nextIndex]
...
events += GameEvent.TurnStarted(nextActiveId, round)            // no health check before this
working = working.withEntity(nextActiveId) { ... ap = stats.maxAp, mana = stats.maxMana ... }
```

**7a.** `order.size == 0` divides by zero. doc02's `checkInvariants`
(invariant #1: *"Every EntityId in turn.order exists in entities"*) says
nothing about non-emptiness, so this isn't even an invariant violation —
it's an unaddressed spec gap. Currently unreachable because nothing
removes entities from `turn.order` at runtime (`DestroyEntity` doesn't
exist yet, same deferred-effect list as doc's Push/Teleport/SpawnEntity).

**7b.** No `health.current <= 0` check before granting `nextActiveId` a
normal turn — a dead entity still on `turn.order` would get
`ResourcesReset`/`TurnStarted`/its `onTurnStart` status ticks like anyone
else. Also unaddressed by any doc — doc04 never specifies "skip a dead
entity's turn," so this is a genuine design gap, not a doc-vs-code
mismatch.

**Fix direction:** needs a design decision, not just a patch — options
are (a) skip forward through `order` past any dead entity when advancing
`nextIndex`, or (b) treat "still in `order` but dead" as invalid state
that invariant-checking should catch instead, prompting removal from
`order` elsewhere. Worth resolving alongside whatever pass finally
implements `DestroyEntity`, since that's what would first make `order`
shrink at runtime.
