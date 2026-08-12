# 25 — Action selection UI

Replaces today's bare `Row` of text `InkButton`s (doc15's Peek sheet action
bar) with icon+name cards, an authored description shown once an action is
picked, and a swipe-to-Details view that explains targeting shape and effects
without committing to use the action. Player-facing only — `Selection`'s
state machine and the resolver/perform() pipeline underneath don't change.

## Card grid (replaces the action `Row`)

- Rounded-rect cards, max 2 per row (wrap into a grid, not a scrolling row).
- Icon left (`ActionDef.projectileSprite`'s art, same `GameAssetManifest`/
  `Res.readBytes` loading path `App.kt`'s `loadEntitySprites` already
  established), name right.
- **No sprite authored** (true for most actions today — `projectileSprite`
  only just started getting content): a generic placeholder glyph fills the
  icon slot rather than dropping it — every card keeps the same size, no
  layout wobble as content fills in sprites over time.
- `End Turn` stays a separate full-width `InkButton` below the grid, not a
  third grid cell — it isn't an action card, styling it like one would imply
  it's targetable.
- `Move` stays excluded from the grid, same as today (tapping the active
  entity's own board tile does it) — this pass doesn't revisit that.

## Header / description text

- Default: `"Select an action"`.
- Once a card is tapped (entering `Selection.ActionPicked`/`TargetPicked`,
  same as today — tap behavior itself is unchanged), the header swaps to
  `ActionDef.description`.
- Empty `description` (nothing authored yet): header just reverts to
  `"Select an action"` rather than showing a blank line — no "no description
  set" placeholder text, since that would show on every action in every
  existing catalog until content is authored.

## `ActionDef.description` (new field)

- `val description: String = ""`, free text, authored in `:designer`'s
  `ActionPanel.kt` `ActionEditor` — a multi-line `InkTextField` alongside
  `NAME`. Flavor/tactical text an author writes by hand ("A wide arc that
  knocks enemies back"), independent of the auto-generated effect text below
  (that one's mechanical, this one's prose).

## Swipe → Details view

- Swipe **left** on a card opens a Details view for that specific action —
  independent of `Selection`/targeting state entirely (swiping never starts
  targeting, never touches the board, same "must not mutate anything" bar
  the whole Peek sheet already holds itself to). A new `detailsActionId:
  ActionId?` piece of state, orthogonal to `selection`.
- Layout mirrors `InspectPanel`'s existing swap convention (the bottom sheet
  fully swaps its content, not an overlay) — a `Back` `InkButton` returns to
  the normal grid, exactly like `InspectPanel`'s own `onBack`.
- Banner: icon (same sprite/placeholder rule as the grid card) + name.
- Left: an abstract shape/AoE preview — a small fixed grid (sized to fit the
  shape's extent, caster placed at a fixed origin cell) rendered by calling
  the existing `tilesInShape(origin, point, shape, map)` (already used by
  `Director.kt`'s ripple beat) against a throwaway single-cell-per-tile
  `BattleMap`, the same "no real map/party needed" approach `:designer`'s own
  `PreviewPanel` already uses for its `preview()` call. Caster cell rendered
  distinctly (green in the mockup) from affected cells (red) — purely
  illustrative, doesn't reflect actual LoS/obstacles on the real map.
- Right: auto-generated mechanical text, walked from `action.effects` (see
  below).

## Auto-generated effect text

A pure `fun describeEffects(effects: List<EffectTemplate>): String` (new
file, likely `ui/.../ActionDescription.kt`), one sentence per top-level
`EffectTemplate`, joined with spaces:

| Effect | Phrasing |
| --- | --- |
| `RollAttack` | `"Does [{ability}] Attack roll which does {dice}+{bonus} {damageType} damage."` |
| `RollSave` | `"Causes [{ability}] Save roll vs DC {dc}"` + on-success/on-fail clauses, each recursing into their own `effects` list — matches the mockup's own example ("...which on success pushes each target 2 tiles") |
| `DealDamage` | `"Deals {amount} {damageType} damage."` |
| `Push` | `"Pushes the target {distance} tiles away."` |
| `Heal` | `"Heals {amount} HP."` |
| `ApplyStatus` | `"Applies {status name} ({stacks}x)."` |
| `Teleport` | `"Teleports the target."` |
| `SpawnEntity` | `"Summons {archetype name}."` |
| `DestroyEntity` | `"Destroys the target."` |

Ability/damage-type names reuse `Ability`/`DamageType`'s own enum names
(`Str`, `Fire`, …) — same casing convention `RollCard`'s modifier chips
already use, nothing new to invent. Cost (AP/mana/hpCost) is intentionally
NOT part of this text — the grid cards and Peek header already show AP/mana,
repeating it here would be redundant with what's already on screen.

## Non-goals (this pass)

- No swipe-back / drag-to-preview animation — a single discrete gesture
  (`detectHorizontalDragGestures`, same primitive `Board`'s own pan/zoom
  already uses) flips straight to Details, no partial-drag visual.
- No change to `Selection`'s state machine, `perform()`/resolver pipeline, or
  how targeting/confirm actually works on the board — purely a sheet-content
  and card-styling pass.
- No live map/LoS in the Details shape preview — abstract only, as above.

## Implementation passes

1. `core/model`: `ActionDef.description: String = ""`.
2. `:designer` `ActionPanel.kt`: description text field in `ActionEditor`.
3. `:ui`: card-grid component (icon + name, 2-per-row wrap, placeholder
   glyph fallback) replacing the current action `Row`; header text swap.
4. `:ui`: `describeEffects()` + shape-preview grid composable + Details view
   (banner/back button) + swipe gesture wiring.
5. Full regression sweep, `v1` check, live playtest verification.
