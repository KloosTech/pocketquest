# 27 — AoE highlight + Details sheet in TargetPicked

Two small, related fixes to `Selection.TargetPicked` (a target point is
confirmed, awaiting the player's second tap to actually perform()):

## Red AoE highlight on the board

Before this pass, only the confirmed point itself was highlighted (green,
`drawSelectedTile`) — a multi-target action's actual footprint was invisible
until it landed. `Board` gains an `affectedTiles: Set<GridPos>` param,
computed at the call site via the same `tilesInShape(caster, point, shape,
map)` the projectile ripple (`docs/24`) already uses — real map geometry,
not the abstract preview grid `docs/25`'s Details view draws. `Shape.Single`
actions produce no red tiles (their one affected tile is already the green
point — a red ring around it would say nothing new). Drawn red
(`drawAffectedTile`, same red as the threat hatch/ripple flash) between the
legal-tile highlight and the green selected tile, so the confirmed point
still reads green on top.

## Details sheet instead of "expects N events"

The old `TargetPicked` sheet text (`"Fire Ball expects 6 events"`) exposed
resolver internals no player should care about. Replaced with `docs/25`'s
`ActionDetailsPanel` — the same banner/shape-preview/auto-text view reachable
by swiping a card, shown automatically the moment a target's picked. Its
`onBack` cancels the pending target (`selection = Selection.None`), the same
outcome as tapping elsewhere on the board — Confirm still only happens by
tapping the highlighted tile again, unchanged. The confirm/cancel hint text
stays, below the Details panel.
