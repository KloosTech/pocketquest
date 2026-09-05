# 42 — Per-stack status damage, and stack decay (Bleed)

Scoped to what "Bleed: 2 piercing per stack at the start of the bearer's turn, loses a stack at the
end of it" actually needs: a way for an `onTurnStart` `DealDamage` to know how many stacks the
ticking status currently has, and a way for a status to lose stacks over time with no reapplication
involved.

## Referencing the stack count: `DealDamage.perStack` + a well-known slot

`EffectTemplate.DealDamage` gains `perStack: Int = 0`. At instantiate time it's resolved against
`ActionCtx.slots[STATUS_STACKS_SLOT]` (a new well-known `SlotKey`, `core/rules/action/
EffectTemplateInstantiate.kt`) — `amount + perStack * stacks`, where `stacks` is 0 if that slot
isn't present. `endTurn`'s `onTurnStart` tick (`Handlers.kt`) is the ONLY place that ever populates
it, with the ticking status's own `ActiveStatus.stacks`:

```kotlin
val ctx = ActionCtx(
    caster = status.sourceId ?: nextActiveId,
    targets = listOf(nextActiveId),
    slots = mapOf(STATUS_STACKS_SLOT to SlotValue.IntSlot(status.stacks)),
)
```

`SlotValue.IntSlot` already existed on the model (alongside `EntitySlot`/`BoolSlot`) but had never
actually been constructed or read anywhere — this is its first real use. `perStack` is a genuine
no-op everywhere else (a regular action's `DealDamage` never has this slot in its `ctx`, so it always
resolves to 0 stacks) — the same "meaningless outside its one real context" shape `Push.onWallHit`
already has for `Ref.EachTarget`.

## Stack decay: a new field, deliberately NOT a `StackPolicy`

The ask named this "a stack policy," but `StackPolicy` (`Refresh`/`AddStacks`/`KeepStrongest`/
`Independent`) answers a different question — "what happens when this status is applied again while
already active." Decay ("lose 1 stack per turn, with no reapplication involved") is a second,
independent axis: it fires every turn regardless of whether the status was ever reapplied. Folding
it into `StackPolicy` would conflate two orthogonal behaviors into one enum with no clean way to
express "AddStacks AND decays" (which is exactly Bleed's own shape — stacks build up when reapplied,
then drain on their own between hits). So: a separate field, `StatusDef.decayStacksPerTurn: Int = 0`
— 0 (every pre-existing status) means "never decays," matching every other opt-in field's default.

`docs/03-modifiers-and-status.md` already documents `Expiry` as "expiry timestamps, not countdowns"
— a deliberate choice to avoid decremented-counter state. Stack decay doesn't fight that: it isn't a
timer being decremented, it's the status's own defining trait (`decayStacksPerTurn`, an authored
constant) applied fresh each turn boundary — no new mutable countdown field, `ActiveStatus.stacks`
was already the thing being changed.

`Handlers.kt`'s `endTurn`, right after its existing step-6 `EndOfTurn` expiry sweep, folds decay over
the ENDING entity's own statuses (not the entity whose turn is starting) — losing a stack at the end
of your own turn, so the next tick of that same status (whenever your turn comes back around) sees
the already-decayed count. Reaching 0 stacks removes the status entirely (`GameEvent.StatusExpired`,
same as any other removal); decaying to a positive count reuses `GameEvent.StatusApplied` to report
the new number (no dedicated "stacks changed" event existed, and reuse reads fine — the visible
effect either way is "this status's stack count is now N").

## `:designer`

- `EffectTemplateEditor.kt`'s `DealDamage` row: the existing amount field is now labeled "flat," with
  a new "per stack" field next to it (only meaningful inside a status's own `ON TURN START` list).
- `StatusPanel.kt`: a new "STACK DECAY (lost at the end of the bearer's own turn — 0 = never
  decays)" stepper, positioned right under STACK POLICY with an explicit note that the two are
  different axes.
