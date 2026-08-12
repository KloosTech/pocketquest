# 32 — OSR-style wall hatch (second wall art)

The actual "another wall art" ask this whole thread was working toward —
the shadow (docs/31) was a preliminary, related discussion resolved first.
A second, distinct hatch algorithm, chosen per-map alongside the original.

## `WallStyle` replaces `wallHatch: Boolean`

`BattleMapDef`/`BattleMap` both had a `wallHatch: Boolean` (hatch vs flat).
With a second hatch style, an on/off switch no longer says enough — it's
now `wallStyle: WallStyle` (`Flat` / `Hatch` / `Osr`), defaulting to
`Hatch` (same default the old `Boolean` had). `:designer`'s Map settings
picker (`InkSelect`) now offers all three; the live-preview-while-authoring
canvas and `:ui`'s real Board both read the same field via a `when`.

## The algorithm (`WallHatchOsr.kt`, next to `WallHatch.kt` — not merged in)

The user's own restated version of the earlier analysis: "clusters of 3 to
5 parallel lines, rotated at different angles (mostly perpendicular or
45-degree variations), extend outward from the rooms, fading into the
blank exterior after about 1.5 to 2 grid cells."

- **Anchor grid, seeded by coordinate** — same technique `drawWallHatch`
  already validated (deterministic, no per-frame re-randomizing, adjacent
  Wall cells' clipped fragments still line up with no seam at the boundary).
  Coarser spacing than the original though (`CLUSTER_SPACING_FRACTION`),
  since one anchor now seeds a whole cluster, not a single stroke.
- **Cluster, not stroke** — each anchor spawns 3–5 parallel lines
  (`lineSpacing` apart) sharing one angle, snapped to 0°/45°/90°/135° plus a
  small jitter, rather than one fully-random-angle stroke.
- **Falloff direction is the opposite of `drawWallHatch`'s.** The original
  algorithm is densest deep inside a wall mass, sparsest at the room
  boundary. This one is the other way — `clusterDensity` is 1.0 for a wall
  cell directly touching a floor cell, fading linearly to 0 by
  `FADE_DISTANCE_CELLS` (2) cells deeper into the rock — matching the
  reference image's hatching hugging room edges and thinning into blank
  rock further out, not the other way around.
- **Distance-to-floor** (`distanceToFloor`) is a bounded local Chebyshev-ring
  search capped at the fade distance, not a full distance transform — cheap
  because anything past the cap is density 0 and never drawn at all, the
  same "skip early, don't compute what won't render" discipline
  `drawWallHatch`'s own density cache already uses.

## Tuning after the first live look

Real user feedback, not a guess: too sparse, with clusters visibly
overlapping and crossing at unrelated angles instead of reading as one
coherent rock texture. Two changes:

- `CLUSTER_SPACING_FRACTION` halved (0.5 → 0.3) — a denser anchor grid,
  less blank space between clusters. Positional jitter also tightened
  (`POSITION_JITTER_FRACTION`, 0.35 of spacing rather than a full spacing
  width) so neighboring clusters don't scatter into each other by chance.
- `regionAngleDegrees`: nearby clusters now share one snapped angle per
  `REGION_SIZE` (3×3) block of anchors — each cluster still jitters
  slightly around that shared angle (`ANGLE_JITTER_DEGREES`), but no longer
  independently rolls a fully random angle of its own. Reads as coherent
  parallel "streams" that occasionally shift direction between blocks,
  rather than scattered crossing streaks. Stroke length also trimmed
  (`STROKE_LEN_FRACTION`, 0.4 → 0.3) so a cluster's own lines stay inside
  its cluster instead of routinely bleeding into a neighbor's.

## Non-goals (this pass)

- No change to `drawWallHatch` itself (the original style) — untouched,
  selectable side-by-side via `WallStyle.Hatch`.
- No grayish-background "double stroke" trick or hand-drawn wall-outline
  perturbation — the other 2 pieces of the original 4-part analysis, still
  unaddressed if wanted later.
- No migration of existing maps' old `wallHatch: Boolean` value — the field
  was renamed outright (confirmed no map in `content/catalog.json` actually
  had it serialized), so every map just falls back to the new field's
  default (`Hatch`), identical to what `wallHatch`'s own default produced.
