# 43 — Status display: Inspect detail + corner badge

Two display-only changes, no model/engine changes.

## Inspect panel: icon, stack count, and mechanical text

The status list at the bottom of `InspectPanel` used to be a raw dump — `"${name} ×${stacks}
(${expiry})"`, e.g. `Bleeding ×4 (EndOfRound(round=2))`. Replaced with, per status: the
`StatusDef.icon` sprite (docs/40, same `statusIcons` map the Board already loads — `InspectPanel`
now takes it as a parameter), `"${name} ×${stacks}"`, and a new mechanical description line below
it via `describeStatus(def, catalog)` — the same "auto-generated text from the authored data" idea
`describeEffects` already provides for actions (docs/25), reused wholesale rather than duplicated:
`describeStatus` is just `describeEffects(def.onTurnStart, catalog)` plus a decay sentence
(`"Loses N stack(s) at the end of your turn."` when `decayStacksPerTurn > 0`). For Bleed this reads
as "Deals 2 Piercing damage per stack. Loses 1 stack at the end of your turn." — `describeEffect`'s
`DealDamage` case also got a small phrasing fix for the flat=0 case: `perStack` with no flat amount
now reads "Deals 2 Piercing damage per stack," not the previous "Deals 0 ... damage (+2 per stack)."

## Board: status badge moves to the corner

Was a row centered above the token; now pinned to the top-right corner of the entity's own cell —
the first (and usually only) icon sits flush with the corner, additional ones extend leftward so a
multi-status cluster still reads as "pinned to that corner." Same world-space-math positioning
(off `entity.pos.value`, scaled by `TILE_PX * zoom`) every other Board overlay already uses — no
separate screen-space tracking, stays correct under pan/zoom automatically.
