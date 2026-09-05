# 44 — EndTurn guards against a non-active entity

Bug report: repeatedly clicking "End Turn" made an enemy do nothing on its turn, every round,
reliably reproducible.

## Root cause

`Effect.EndTurn`'s handler (`Handlers.kt`) had no check that `effect.who` was actually the
currently active entity — it just advanced `turn.activeIndex` unconditionally. `:ui`'s "End Turn"
button (`App.kt`) captures `activeId` once per click and fires a fresh `scope.launch { endTurn(activeId) }`
coroutine per click — nothing debounces repeated clicks. Spamming it queues a second call still
carrying the STALE `activeId` (the human's own id) from before the first click's turn-advance had
even committed. Once that first call resolves and it becomes the enemy's turn, the queued stale
call fires `Effect.EndTurn(humanId)` — with no active-entity check, the handler advanced
`turn.activeIndex` a SECOND time, skipping the enemy's entire turn (no `chooseAction`, no action,
nothing — not even the "remains hidden" fog-gate log line, since the enemy's `runAiTurns()` pass
never happened at all) before it ever got a chance to run.

An isolated repro against `:core:ai`'s `chooseAction` directly (real archetype stats, real
`AiProfileDef` tiers, real map positions) confirmed the AI decision engine itself was never the
problem — it correctly returns an Approach decision for this exact scenario. The bug was purely
"whose turn is this EndTurn actually allowed to end."

## Fix: the resolver rejects a stale EndTurn as a no-op

```kotlin
if (state.turn.order.isNotEmpty() && endingId != state.turn.order.getOrNull(state.turn.activeIndex)) {
    return HandlerOutcome(state)
}
```

Placed in the engine, not just patched at the UI layer — this closes the invariant for every
caller, not only this one button. Gated on `order.isNotEmpty()` specifically so it doesn't shadow
`endTurnThrowsRatherThanDivideByZeroOnEmptyOrder`'s existing coverage: an empty `turn.order` is a
genuinely corrupt state that must still throw via the function's own `check()` below, not get
silently swallowed as "just a stale call." A no-op here emits nothing — no `TurnEnded`/`TurnStarted`
for a turn that never actually happened, so the log stays honest about what occurred.

No `:ui`-side debounce was added — the engine fix alone makes a queued stale click harmless (a
silent no-op instead of turn-order corruption), so hardening the button itself is optional polish,
not required for correctness.
