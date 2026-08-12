# 26 — Character detail card

Replaces doc15's minimal Inspect text block (name/faction, one HP·AC·AP·mana
line, a statuses list) with a full stat card, per the user's mockup: a top
flavor-text banner, sprite + AC badge on the left, HP/MP fill bars and the
six ability scores on the right.

## Layout

- **Banner**: `Archetype.description` (new field, same authored-in-`:designer`
  pattern as `docs/25`'s `ActionDef.description`). Empty when nothing's
  authored yet — the banner row just doesn't render, same "no description,
  no empty placeholder" rule `docs/25` already established.
- **Left column**: entity sprite (same `sprites: Map<EntityId, ImageBitmap>`
  `App.kt` already loads for `Board`/`TurnOrderStrip` — `InspectPanel` gets a
  new `sprite: ImageBitmap?` parameter, generic placeholder glyph when null,
  same convention `docs/25`'s `ActionIcon` established), archetype name below
  it, AC badge (diamond, mockup's shield-ish shape) overlapping the
  sprite's top-left corner.
- **Right column**: HP bar (red fill) and MP bar (blue fill), each showing
  current/max as both a proportional fill and the numeric value (mockup
  omits the number, but dropping it loses real tactical information the
  current text-only Inspect already gave players — kept as an overlay on the
  bar). Below the bars, the six `AbilityScores` in two columns (Str/Int,
  Dex/Wis, Con/Cha per the mockup's pairing).
- **Kept, not in the mockup's sketch**: AP (still real, players need it) and
  the statuses list, both rendered below the ability-score grid — the
  mockup is a rough sketch of the new stat block, not a spec for removing
  functionality the old Inspect already had.

## Ability scores shown: derived, not base

`entity.stats(catalog).abilities` (the post-modifier `Stats.abilities`
`RollBreakdown.kt` already computes for ability checks), not
`catalog.archetype(...).abilities` — Inspect already shows current/effective
HP and AC, not archetype base values; a buffed/cursed entity's ability
scores should read the same way, not silently omit active modifiers.

## Non-goals

- No change to Peek (doc15: "they must not look alike") — this pass only
  touches Inspect.
- No new sprite-loading path — reuses `App.kt`'s existing per-encounter
  `sprites` map, just threaded into `InspectPanel`'s call site.
