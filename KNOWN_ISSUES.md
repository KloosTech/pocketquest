# Known issues

Tracked findings from an external spec-analysis pass (#1-#7) plus
`docs/17-engine-gaps.md`'s Tier 0 (#8-#11), each independently verified
against the current code (not taken on faith). `docs/17-engine-gaps.md`
is now the authoritative, dependency-ordered gap list for the whole
project — this file stays as the detailed investigation record for the
items that came out of it, cross-referenced by its numbering (0.1-0.10)
below. Its Tier 0 list maps onto #1-#11 almost exactly:

| Here | doc17 | Status |
| --- | --- | --- |
| #1 (movement never animates) | not listed in doc17 | Fixed |
| #2 (corpses/reserve units) | 0.1 | Fixed |
| #3 (free reactions) | 0.2 | Fixed |
| #4 (`reactedTo` dedup) | 0.3 | Fixed |
| #5 (reaction depth) | 0.4 | 5a kept as a documented tradeoff, 5b Fixed |
| #6 (`ActionStarted` bypasses triggers) | 0.5 | Fixed |
| #7 (turn boundary) | 0.6 | Fixed |
| #8 (`answererFor` null-controller stall) | 0.7 | Fixed |
| #9 (`Fizzled` uses `class.simpleName`) | 0.8 | Fixed |
| #10 (Expected-mode contradictions) | 0.9 | Fixed |
| #11 (`Modifier.Roll` dead code) | 0.10 | Open — needs a design pass, see below |

Status legend:

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

**Status: Fixed.** `ReactedKey` gained a `version: Long` field, populated
from `state.version` wherever a `ReactedKey` is constructed
(`collectTriggers`, both the filter and the insert). Two structurally-
identical events for the same entity at different `state.version`s no
longer collide. Regression tests:
`ReactionsTest.collectTriggersStillDedupsTheSameEventAtTheSameStateVersion`
and `...OffersAgainForAStructurallyIdenticalEventAtALaterStateVersion`.

**Status (original finding): Confirmed (scope corrected).**

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

**Status: 5b Fixed, 5a kept as-is (see below).** `collectTriggers` now
`check(depth < MAX_REACTION_DEPTH) { ... }` — throws `IllegalStateException`
matching doc09's spec, instead of silently returning no offers. The
existing unit test was renamed/rewritten to
`collectTriggersThrowsAtMaxDepth` (`assertFailsWith`).

**Status (original finding): two sub-findings, different severity.**

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

**Status: Fixed.** `perform()`/`preview()` now route `ActionStarted`
through `collectTriggers` explicitly (a new shared `buildInitial()`
helper in `Perform.kt`) before the action's own stack (cost + effects) is
even pushed — a matching reaction resolves before the caster pays or the
action's effects run, matching real Counterspell timing. Regression
tests: `PerformTest.aCounterspellShapedReactionToActionStartedFires` and
`...previewAlsoSeesAnActionStartedReaction`.

**Status (original finding): Confirmed.**

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

---

## 8. `answererFor` stalls the resolver for a controller-less entity

**Status: Fixed.** Added `Answerer.NeverReacts`, returned directly when
`entity.actor == null` (checked before ever looking at `.controller`, so
there's no nullable-chain collapse with a genuine `Controller.Human` to
conflate anymore). `offerReaction` handles it as a no-op — no `Ask`, no
`AwaitingInput` — while `ReactionTriggered` still fires (the opportunity
was still evaluated, just never answered). Regression tests:
`ReactionsTest.answererForReturnsNeverReactsWhenEntityHasNoActor` and
`...aReactionOfferedToAnActorlessEntityResolvesInlineInsteadOfStalling`
(the latter runs a real `run()` and asserts `Completed`, not
`AwaitingInput`).

**Status (original finding): Confirmed** (doc17 0.7).

```kotlin
fun answererFor(entity: Entity): Answerer = when (val controller = entity.actor?.controller) {
    is Controller.Ai -> Answerer.Ai(controller.profile)
    is Controller.Human, null -> Answerer.HumanUi
}
```

(`Reactions.kt:31`.) A `null` controller — meaning `entity.actor` itself
is `null` (a non-combatant per `Entity.kt`'s own doc comment: *"a wall
has no health field... it has health=null"*, and by extension no `actor`
either) — falls into the `HumanUi` branch. If such an entity ever ends
up offered a reaction (nothing currently prevents an archetype with no
`actor`-bearing entity from having reaction-cost actions authored for
it), `offerReaction` (`Handlers.kt:280`) spawns `Effect.Ask` and the
resolver returns `AwaitingInput` — a decision prompt for a "player" who
does not exist and will never answer. The resolver would sit parked in
`AwaitingInput` forever; nothing times it out.

Currently unreachable in the same sense as #7 originally was: nothing in
the catalog authors a reaction action on an actor-less archetype. Will
bite the first piece of content that does (e.g. an environmental hazard
with a triggered response).

**Fix direction:** `answererFor` should return a distinct "never reacts"
answerer for a `null` controller — not `HumanUi`, not `Ai` — so
`offerReaction` can skip straight to "no reaction" instead of asking. The
doc04-specified `Answerer.Auto` (never implemented, doc03.8 in
`docs/17-engine-gaps.md`) would be the natural home for this once it
exists; until then, a minimal third `Answerer` case suffices.

---

## 9. `Fizzled` events use `effect::class.simpleName`, which R8 renames

**Status: Fixed.** `fizzle()` now derives the label from the effect's own
polymorphic `@SerialName` (via `Json.encodeToJsonElement` and reading the
`"type"` discriminator field) instead of `effect::class.simpleName`. The
`@SerialName` is a compile-time string baked into the generated
serializer — immune to R8 renaming by construction, not by convention.
`:core:rules` gained a `kotlinx-serialization-json` `commonMain`
dependency (previously test-only) to do this.

**Status (original finding): Confirmed** (doc17 0.8).

```kotlin
private fun fizzle(state: GameState, effect: Effect, reason: Rejection): HandlerOutcome =
    HandlerOutcome(state, events = listOf(GameEvent.Fizzled(effect::class.simpleName ?: "Effect", reason)))
```

(`Handlers.kt:96`.) `GameEvent.Fizzled.effect: String` is meant to name
which effect failed, for the battle log and for debugging. In a release
build, R8/ProGuard renames Kotlin classes (including sealed subtypes of
`Effect`) unless explicitly kept — `class.simpleName` would return the
obfuscated name (`a`, `b$c`, etc.) instead of `"DealDamage"`,
`"SpendCost"`, and so on. Debug builds and every existing test run
un-obfuscated, so this passes every golden-event-list check today and
only breaks in production, where nobody is running the test suite
against the output.

**Fix direction:** every `Effect` subtype already carries a stable
`@SerialName` for exactly this class-name-instability reason (see
`AGENTS.md`'s *"Every Effect... needs an explicit @SerialName"* rule,
already enforced by pass 7's reflection test). `fizzle()` should read
that `@SerialName` instead of `class.simpleName` — same annotation,
same serializer machinery already in place, just read from the runtime
class's annotations rather than relying on the class name surviving
minification.

---

## 10. Expected-mode previews can report a self-contradictory roll, and ignore advantage entirely

**Status: Fixed.** `rollD20` now returns `Int`, not `Double` — Live mode
already had an exact integer from `RngState.d20()`; Expected mode now
rounds once, immediately, via a new `expectedD20(advantage)` that also
finally reads the `advantage` parameter instead of ignoring it: Normal
11 (from 10.5), Advantage 14 (from the true E[max(X,Y)]=13.825, not
10.5), Disadvantage 7 (from E[min(X,Y)]=7.175). `rollAttack`/`rollSave`/
`concentrationCheck` all use that single rounded value for both the
hit/success decision and the recorded event field, so the two can no
longer disagree. One existing test (`FlagshipScenarioTest`) had a
concentration check hand-tuned to fail against the old raw 10.5 that sat
exactly on the new rounded boundary (con 8, mod −1: old `10.5−1=9.5<10`
fails; new `11−1=10≥10` flips to succeeds) — adjusted to con 6 (mod −2)
for a comfortable margin under the corrected rounding, preserving the
scenario's actual narrative intent (a failed check breaking
concentration). Regression tests:
`RollEffectTest.expectedModeHitDecisionAgreesWithTheRecordedD20AtTheRoundingBoundary`
and `...ReflectsAdvantageAndDisadvantageInsteadOfAlwaysNormal`.

**Status (original finding): Confirmed** (doc17 0.9).

```kotlin
private fun rollD20(state: GameState, mode: RngMode, advantage: RollMode): Pair<Double, GameState> = when (mode) {
    RngMode.Live -> ...
    RngMode.Expected -> 10.5 to state
}
```

(`Handlers.kt:74`.) In `rollAttack` (`Handlers.kt:223`), `hit` is decided
against the *unrounded* `10.5`: `val total = rollValue + effect.attackBonus;
val hit = total >= ac`. The emitted `AttackRolled.d20` field, though, is
`rollValue.roundToInt()` — a rounded integer. Per Kotlin's documented
tie-breaking rule (`roundToInt` rounds ties toward positive infinity),
`10.5` rounds to `11`, not `10` as doc17's illustrative example states —
worth correcting doc17 itself if this file's fix references it, since
the mechanism is right but that specific number isn't. The real bug
survives the correction: recompute `hit` by hand from the *recorded*
event fields (`d20 + mod >= ac`) and it can disagree with the `hit` flag
actually emitted, whenever `ac` falls strictly between `10.5 + mod` and
`11 + mod`. A human reading a preview's event log has no way to tell the
roll was fractional; they'll "check the math" against the rounded number
and get the wrong answer.

Separately: `resolveAdvantage`/`RollMode` are threaded into `rollD20`'s
signature but `RngMode.Expected` ignores the `advantage` parameter
completely, always returning `10.5` regardless of advantage or
disadvantage. A true expected value under advantage is measurably higher
(≈13.825 for a d20, not 10.5) — every AI/preview evaluation of an
advantaged attack currently understates its own odds, which
systematically biases `:core:ai`'s `chooseAction` scoring against
advantage-granting plays without anyone having decided that should be
true.

**Fix direction:** two independent fixes. (a) Either round `rollValue`
consistently *before* computing `hit` too (so the recorded d20 and the
hit decision agree, at the cost of Expected mode being a slightly
different number than the "true" continuous expectation), or keep `hit`
exact but stop reporting a misleadingly precise-looking integer `d20` in
Expected mode specifically. (b) Compute the real expected value of a d20
under advantage/disadvantage (sum over 400 pair-outcomes, or the closed
form) instead of hardcoding `10.5` regardless of `advantage`.

---

## 11. `Modifier.Roll` is fully dead code

**Status: Confirmed** (doc17 0.10).

`Modifier.Roll(ctx: RollContext, side: AdvSide)` and `RollContext`
(`Modifier.kt`) are modeled, `@Serializable`, and round-trip through
JSON like every other `Modifier` variant — but `stats()`
(`StatsDerivation.kt`) never reads them:

```kotlin
for (om in ordered) (om.modifier as? Modifier.Add)?.let { work.add(it.stat, it.value) }
for (om in ordered) (om.modifier as? Modifier.Mul)?.let { work.mul(it.stat, it.factor) }
...Override...
...Grant...
...Resist...
```

(`StatsDerivation.kt:74-90`.) Five of `Modifier`'s six sealed subtypes
get an `as?` filter pass; `Roll` gets none. `Stats` itself
(`AbilityScores.kt`) has no advantage/roll-context field at all to put
the result in even if `stats()` did read it. A status or item authored
today with `Modifier.Roll(AttackRoll(...), Advantage)` — "grants
advantage on attack rolls" — silently does nothing. Combined with #10's
finding (`RngMode.Expected` also ignores advantage), advantage as a
mechanic does not currently function anywhere in the engine end to end,
despite `AdvSide`/`RollMode`/`resolveAdvantage` all existing and being
exercised by dice-roll-level tests.

**Fix direction:** this is a real design gap, not a one-line patch —
`Stats` needs a place to carry "sources of advantage/disadvantage on
[context]" (probably `Set<RollContext>` split by side, or a
`Map<RollContext, RollMode>`), `stats()` needs a sixth filter pass
collecting `Modifier.Roll` into it, and every call site that currently
takes an explicit `advantage: Set<AdvSide>` parameter (actions,
`EffectTemplate.RollAttack`/`RollSave`) needs to fold in whatever the
caster's/target's derived `Stats` contributes on top of what the action
itself specifies. Worth designing alongside whichever pass finally
implements real advantage-granting content, not in isolation.
