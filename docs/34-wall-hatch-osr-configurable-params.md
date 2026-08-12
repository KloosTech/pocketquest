# 34 — OSR hatch: configurable parameters

Every tunable `generateWallHatchOsr` (docs/33) reads is now a `WallHatchOsrParams` field, editable
per-map in `:designer`, instead of a hardcoded constant — resolved after two rounds of live tuning
each needed a code change to try a different look. Defaults match whatever was last tuned live.

## `WallHatchOsrParams`

A new `@Serializable` type, persisted on `BattleMapDef` as `wallHatchOsrParams` (the generator
inputs the map's current `wallHatchOsr` bake was last produced with) — deliberately **not** on the
runtime `BattleMap`: these only matter to `:designer`'s generator, never read at render time (only
the baked `HatchLine`s are).

| Field | Meaning |
| --- | --- |
| `subcellsPerTile` | Sub-grid resolution — finer means smaller, more numerous strokes. |
| `fadeDistanceCells` | How many whole tiles deep into a wall mass the hatch still generates. |
| `targetCoverage` | Fraction (0..1) of eligible sub-cells the fill pass aims to claim. |
| `minLineLengthSubcells` / `maxLineLengthSubcells` | Stroke length range. |
| `minGroupSize` / `maxGroupSize` | Parallel strokes placed together per growth attempt — the reference image's "clusters of 3 to 5 parallel lines," re-added explicitly (see below). |
| `angleJitterDegrees` | Cosmetic wobble on each *finished* line, not the growth direction (see below). |
| `angleRegionSubcells` | How large a block of sub-cells shares one snapped angle. |
| `lineWidthFraction` | Pen width, as a fraction of one tile. |

## Groups are back, inside the packing model

The docs/33 rewrite dropped the scatter algorithm's "cluster of 3-5 parallel lines" concept
entirely — each growth attempt placed exactly one line. Re-added here as `minGroupSize`/
`maxGroupSize`, but implemented *inside* the occupancy-aware packing model rather than as
independent parallel offsets like the old scatter version: a growth attempt now picks a group size,
offsets that many candidate start points perpendicular to the shared growth direction (offsets
computed directly in sub-cell integers — no separate spacing parameter needed), and grows each one
independently (so one line in a group can end up shorter than its neighbors if it hits an obstacle
first — an intentional bit of organic irregularity). Every line in the group still updates the
shared occupancy grid, so later attempts correctly treat the whole group as claimed space.

## Why `angleJitterDegrees` doesn't touch the growth direction

The packing algorithm's occupancy tracking depends on growth staying on an exact integer sub-cell
lattice — the four canonical directions (0°/45°/90°/135°) are the only ones that map cleanly to a
repeating integer step in a square grid. A literal "jittered angle" growth direction would break
that. Instead, `angleJitterDegrees` rotates each *already-grown* line's drawn endpoints by a small
random amount around its own midpoint, purely cosmetically, after occupancy has already been
recorded against the exact lattice direction — the same "hand-drawn imperfection" the original
Path Perturbation idea (from the very first AI-sourced analysis this whole thread started from)
was after, applied at the right layer instead of breaking the packing model.

## `:designer` UI

A new "OSR HATCH PARAMETERS" block appears under WALL TEXTURE whenever a map is set to
`WallStyle.Osr`, right below the Regenerate Hatch button — steppers for the integer fields, a small
`FloatField` (mirrors `EffectTemplateEditor.kt`'s `IntField` technique) for the float ones. Editing
a value never regenerates anything by itself, same as painting walls doesn't (docs/33) — only
Regenerate Hatch or Save actually re-runs the generator, now reading whatever params are currently
set.

## Non-goals

- No UI to reset params back to defaults — `WallHatchOsrParams()`'s own defaults are only ever
  applied to a map that's never touched this section at all.
- No per-parameter validation beyond basic clamping (coverage to 0..1, line width/jitter to
  non-negative, max-size steppers floored at their min) — an author can still pick values that
  produce a sparse or slow bake; this is an authoring tool, not a guardrail.
