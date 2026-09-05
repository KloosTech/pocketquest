# 45 — AI move budget must respect the action's own range

## Bug

An enemy AI, in the tiered `Approach`/`Retreat` goal, never moved toward the
party at all — `chooseAction` returned `null` every single round, even at
full HP with a clear, unobstructed line to the nearest enemy.

## Root cause

`resolveMoveRelativeToNearestEnemy` (`core/ai/ChooseAction.kt`) computed its
movement budget as `minOf(entity.stats(cat).speedTiles, entity.resources?.ap
?: 0)` — deriving the cap from the archetype's `speedTiles` stat alone.

`legalTargets`'s own `TargetMode.Path` branch (`core/rules/targeting/
Targeting.kt`) computes the *actual* legal budget as `minOf(rangeInTiles(
targeting.range), ap)` — the move action's own authored `range` is a ceiling
independent of `speedTiles` (same as any other action's range; a "Move"
action with a small range is mechanically no different from "Dash" reaching
only 8 tiles).

Two different formulas for "how far can this move action legally reach,"
diverging whenever an action's authored range is smaller than the caster's
speed stat (docs/README's principle 5: two implementations of the same rule
will diverge). The AI proposed a destination `speedTiles`/`ap` said was
affordable; `canPerform` — which defers to `legalTargets` — correctly
rejected it as out of the move action's range; `resolveMoveRelativeToNearestEnemy`
had no fallback and returned `null`, over and over, forever.

This reproduced in `:designer`'s Playtest and `:app` alike; it was masked
during isolated `core:ai` unit testing because the test's hand-built move
action used a generous range that happened to match `speedTiles`, hiding the
divergence entirely.

## Fix

`resolveMoveRelativeToNearestEnemy` now looks up the move action's own
`ActionDef` before computing budget, and uses the exact same formula
`legalTargets` uses: `minOf(rangeInTiles(moveDef.targeting.range), ap)`. One
formula, reused, not reimplemented.

Content-side, the demo catalog's "move" action still has a small authored
range — left as-is per explicit instruction; only the engine formula was
fixed. Any archetype whose move action's range is genuinely smaller than its
speed will still be capped by that range, which is now the *correct*,
consistent behavior (matching what a human player's own Move UI is already
bound by), not a bug.

## Related, real fixes made in the same investigation (not this bug, but found en route)

- `LaunchedEffect` in `ui/App.kt` was keyed on `(round, activeId, inCombat)`,
  which changed on every single `endTurn()` call inside `runAiTurns()`'s own
  loop — Compose cancelled and relaunched the coroutine mid-turn, sometimes
  dropping an in-flight enemy action. Re-keyed to `(inCombat, isHumanTurn)`,
  which only changes at a genuine human↔AI control handoff.
- `Effect.EndTurn`'s handler had no active-entity guard — see
  [44-end-turn-guard.md](44-end-turn-guard.md).
- `:designer`'s Playtest `Window` reused the same composition slot across
  repeated Playtest launches, leaving `:ui`'s own unkeyed `remember`s stale;
  wrapped in `key(playtestSession)` to force a real dispose+recreate per
  launch.

All three were real bugs worth keeping, but none of them was sufficient on
its own — the AI move budget/range mismatch above was the actual cause of
"the enemy does nothing."
