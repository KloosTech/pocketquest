# 41 — Status duration (Turns), RemoveStatus authoring, and a confirmation on ability mods

Three asks bundled in one request; the third turned out to already be implemented.

## 1. `ApplyStatus`'s Expiry gains "Turns"

The editor previously only offered `Permanent`/`Until concentration lost` — `EndOfTurnOf`/
`StartOfTurnOf`/`EndOfRound` need a real `EntityId`/absolute round number that doesn't exist at
authoring time, so they stayed handler-constructed-only. But "lasts N rounds" (the actual common
case) doesn't need either of those — it only needs a round number, and that number is knowable the
moment the status is actually applied (`state.turn.round`), just not at authoring time.

New model case: `Expiry.Turns(n: Int)` — deliberately authoring-only, and never what a real
`ActiveStatus` stores. `Handlers.kt`'s `applyStatus` resolves it the instant a status is actually
applied:

```kotlin
val expiry = when (val e = effect.expiry) {
    is Expiry.Turns -> Expiry.EndOfRound(state.turn.round + e.n)
    else -> e
}
```

`Expiry.EndOfRound` and the turn-boundary sweep that expires it already existed and needed zero
changes — `Turns` is purely a friendlier authoring shape that compiles down to the primitive that
was already there. `core/rules/Expiry.kt`'s `matches()` gets a `Turns -> false` branch purely for
exhaustiveness; it can never actually be reached since nothing ever stores it unresolved.

`:designer`'s `ExpirySelect` becomes a type dropdown (Permanent / Until concentration lost / Turns)
plus a number field that only appears for Turns.

## 2. `RemoveStatus` authoring template

`Effect.RemoveStatus(target, status)` existed since the resolver's earliest pass — a status-cleanse
runtime primitive with no way to actually author it into an action, the same "primitive without an
authoring layer" gap `Heal` had before docs/17 added `EffectTemplate.Heal`. Added the same way:

```kotlin
data class RemoveStatus(val target: Ref, val status: StatusId) : EffectTemplate
```

Instantiates straight through (`resolveRef(target, ctx).map { Effect.RemoveStatus(it, status) }`),
validated the same way `ApplyStatus` already is (unknown status id → a `CatalogValidator` problem),
described the same way every other effect is (`"Removes <status name>."`), and editable via the same
`RefPicker` + `StatusSelect` row shape `ApplyStatus` already uses, minus the fields that don't apply
(stacks, expiry). A healer action can now actually cleanse a debuff, not just add HP back.

## 3. Ability modifiers already come from the caster — no change needed

Checked `Handlers.kt`'s `rollAttack`/`rollSave` directly: both already derive
`abilityModifier(entity's actual ability score)` via `rollBreakdown(cat, effect.ability, ...)` and
add it to the roll automatically (docs/22) — a caster with 16 Strength making a Strength-based
attack already gets +3 with zero manual input. The "extra bonus" field on `RollAttack` (labeled
exactly that in the editor) is additive on top of the derived modifier — a magic weapon's `+1`, not
a replacement for it — and defaults to 0 for an ordinary weapon. `RollSave` has no such field at
all: the saving creature's own ability modifier is derived the same way, added to their roll
automatically, and only the DC is author-set (correct 5e shape — a save's DC is the caster's to
set, not derived). Nothing needed changing here; this doc exists mainly to record that it was
checked and confirmed working, not assumed.
