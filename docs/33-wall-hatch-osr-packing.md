# 33 — OSR hatch: packed generation, not scatter

Replaces `WallHatchOsr.kt`'s current algorithm (docs/32) entirely — same
`WallStyle.Osr` option, a different generator underneath it. The scatter
approach (independently-sampled clusters, however dense or region-coherent)
structurally cannot produce the reference look: no stroke knows what's
already been drawn, so gaps and overlaps are inherent to the technique, not
a tuning problem. Confirmed by two rounds of live tuning that only moved
the same tradeoff around without closing it.

## The algorithm: directional line-growth over an occupancy grid

1. Discretize the wall region into a fine sub-grid — `SUBCELLS_PER_TILE`
   sub-cells per tile side (finer than one tile; a tunable constant, not a
   design fork).
2. Track occupancy per sub-cell: has a line already claimed it.
3. Repeatedly pick a random *unoccupied* sub-cell as a start point, pick a
   direction snapped to 0°/45°/90°/135° (region-coherent bias kept from the
   scatter version — nearby strokes still tend to share an angle), and grow
   a stroke sub-cell-by-sub-cell in that direction until it hits: the
   wall-mass boundary, an already-occupied sub-cell, or a max-length cap.
4. Mark every sub-cell the grown stroke's footprint (including pen width)
   touches as occupied.
5. Repeat until coverage hits a target fraction or an attempt cap is
   reached — bounds worst-case time; a few leftover gaps are fine, hand-
   drawn work isn't perfectly packed either.
6. Output: a fixed `List<HatchLine>` (start/end/width in tile-unit floats,
   same unscaled space `wallSegment`/`GridPos.toOffset` already use) — this
   list IS the generated artifact, not a live per-frame computation.

Linear in sub-grid cell count, each visited ~once. Cheap for a single
offline pass; not something to re-run every frame (confirms the
performance concern that started this discussion). Still gated by
docs/32's fade-distance-from-floor rule for which wall cells get any
generation attention at all — deep wall interior far from any room is
skipped entirely, same reasoning as before (never visually distinguishable,
no reason to spend the sub-grid pass there). Coverage target is high
(~90%+) *within* the covered band, though — packed, not scatter-sparse;
the fade rule controls *which cells participate*, not how dense the fill
is once they do.

## Resolved: baked into the map, not regenerated at runtime

Walls are static per map — nothing at runtime ever changes the geometry
the generator would produce, so there's no reason to ever run it during
actual play. `BattleMapDef` gains a new field holding the generated line
list (only meaningful when `wallStyle == WallStyle.Osr`); `:ui`'s Board
renders that stored list directly — no generation code path in `:ui` at
all, not even a cache, since there's nothing to compute.

## Resolved: `:designer` regeneration trigger

Painting/erasing walls does **not** trigger regeneration — an author
paints freely with the existing live picker feedback for Flat/Hatch (those
are unaffected, still evaluated live same as today), and the map simply
shows its *last-baked* Osr geometry (stale relative to in-progress edits)
until either:
- a "Regenerate Hatch" button next to the WALL TEXTURE picker is clicked, or
- the catalog is Saved — every map currently set to `WallStyle.Osr` gets
  unconditionally regenerated immediately before serializing, whether or
  not it looks "dirty." Simpler than dirty-tracking per map, and cheap
  enough (this whole pass exists because generation is fast in aggregate,
  just not fast enough to want it on every single cell-paint click) that
  unconditional regen-on-save for every Osr map is a non-issue.

Switching a map's style *to* `Osr` for the first time shows no geometry
(or a placeholder) until one of the two triggers above actually bakes it —
no surprise costly computation just from picking a dropdown option.

## Non-goals

- No incremental/partial regeneration (recomputing just the edited region)
  — full-map regeneration on every trigger, simplest correct thing; revisit
  only if a real map's regen time turns out to matter in practice.
- No persistence-format migration concern: this is a new field, not a
  rename — every map saved before it existed just has it absent/empty,
  falls back to no baked geometry (renders as if `Osr` were freshly
  selected) until the next Regenerate/Save.
