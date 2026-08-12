# 31 — Directional wall shadows

Part of a bigger "extend the hatching toward the Dyson-Logos OSR map style"
ask, scoped down to just the shadow piece for this pass — the hatching
itself (`WallHatch.kt`) already exists and isn't touched here.

## Light convention

Top-left, matching the reference image and the genre convention it's drawn
from — not a real light simulation (this is a flat top-down grid), a fixed
stylistic choice. A floor cell shadows on whichever of its North/West edges
border a wall; South/East neighbors never cast anything.

## Detection

A floor cell's North edge shadows if either is true:
- its North neighbor is a whole `TileType.Wall` cell, or
- a `WallEdge(pos, Side.North)` sits on that same cell.

Same for West/`Side.West`. This is the identical adjacency check
`wallOutlineSegments`/`map.wallEdges` already use to decide where a wall
boundary line goes — no new geometry, just reading the same two facts a
second way. Resolved: WallEdge dividers cast the same shadow a full Wall
cell would, not just whole-cell walls.

A floor cell only diagonally touching a wall (its North-West neighbor is a
wall, but neither its direct North nor West neighbor is — an outside
corner of the wall mass) got no shadow at all under the two rules above,
found live: a visible gap right at every convex corner. Fixed with a third
case — a small radial patch anchored at that exact corner point, fading
over the same depth the straight edges use, so it reads as one continuous
shadow wrapping the corner rather than a mismatched second effect.

A corner cell (wall to both its North and West) gets both strips — they
overlap in the corner square and darken slightly more there, which reads as
a natural corner-shadow rather than needing special-casing.

## Rendering

- Drawn in `drawGrid` right after the floor-texture fill, before grid
  lines — same ordering reasoning already documented for why floor-fill
  goes first ("a textured cell never paints over the line under it"); grid
  lines stay legible on top of the shadow instead of the shadow muddying them.
- Resolved: a soft gradient falloff (`Brush.linearGradient`), not a flat
  strip — the first gradient in this UI (everything else is flat ink-on-
  paper), a deliberate exception for this specific "cast shadow" look
  rather than the app's general rule.
- `INK`-based color (not raw black, keeping the existing palette). Tuned up
  after the first live look — the AI writeup's original 0.25–0.3 alpha over
  15–20% of the cell read as too subtle in practice: now 0.45 alpha at the
  wall edge, fading to 0 over 50% of the cell's size (`WALL_SHADOW_ALPHA`/
  `WALL_SHADOW_DEPTH_FRACTION` in `WallShadow.kt`).
- Perpendicular to the edge: a North-edge shadow is a horizontal band whose
  gradient runs top→bottom; a West-edge shadow is a vertical band whose
  gradient runs left→right.

## Its own file, not folded into `WallHatch.kt`

`drawWallShadows` lives in a new `WallShadow.kt`, not inside `WallHatch.kt`
— a separate mechanism from the crosshatch, not an extension of it; an
earlier version of this pass mistakenly added it to `WallHatch.kt` directly,
corrected here. Same public shape either way (`isWall`/`hasWallEdge`
lambdas, no hard `BattleMap` dependency) and shared between `:ui`'s real
Board and `:designer`'s Map editor canvas the same reasoning `drawWallHatch`
itself is shared: an author should see the same shadow while placing walls
that they'll see in Playtest. `:ui` passes `BattleMap.hasWallEdge` directly;
`:designer`'s `MapDef` has no equivalent method, so its call site replicates
the same bidirectional (check-from-either-side) canonicalization inline,
reusing the existing `sideDelta`/`toggleWallEdge` helpers already in
`MapEditorPanel.kt`.

## Non-goals (this pass)

- No change to `WallHatch.kt`'s hatching itself, the grayish-background
  "double stroke" trick, or hand-drawn wall-outline perturbation — those
  are the other 3 pieces of the original analysis, left for a later pass
  if wanted.
- No shadow on South/East-facing wall edges — the fixed top-left
  convention above is intentional, not a gap.
