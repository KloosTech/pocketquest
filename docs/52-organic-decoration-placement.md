# 52 — Organic (off-grid) decoration placement

docs/Campain_1's maps read as hand-drawn because clutter (rubble, banners, scattered debris,
candles) never aligns to a grid — every placeable thing in this engine so far
(`PropPlacement`/`TriggerPlacement`/`GatePlacement`/`SpawnZone`) anchors to a whole `GridPos`
cell. Free positioning for *everything* would need a real sub-tile collision system (partial-cell
blocking, fractional occupancy) — a much bigger change than "props feel more organic" asks for.

## Decided with the user

- **Decorative-only.** Only non-blocking clutter goes off-grid. Anything that blocks movement/LoS
  stays grid-snapped (`PropPlacement`, unchanged) — the tactics grid's collision model doesn't
  change at all.
- **Same sprite pool.** A decoration references the same `PropDef`/`PropId` catalog every grid prop
  does — no separate decoration-only asset list. `blocksMovement`/`blocksLoS` on that `PropDef` is
  simply never consulted for a decoration placement.
- **Full drag-and-drop + rotate handle** in `:designer`'s Map editor, not just numeric fields.

## Model

```kotlin
// Ids.kt
@JvmInline @Serializable value class DecorationId(val raw: String)

// MapDef.kt
@Serializable
data class DecorationPlacement(
    val id: DecorationId,
    val prop: PropId,
    val x: Float, // tile units, continuous — same "3.25 = 3.25 tiles from origin" convention HatchLine already uses
    val y: Float,
    val rotationDegrees: Float = 0f, // free angle, unlike PropPlacement.rotationQuarters
    val flipX: Boolean = false,
    val tint: Int? = null,
    val scale: Float = 1f,
    val layer: PropLayer = PropLayer.Object,
)
```

- `BattleMapDef.decorations: List<DecorationPlacement> = emptyList()`, `BattleMap.decorations`
  carried straight through by `toBattleMap()` — pure rendering data, same category as
  `floorTexture`/`wallStyle`, **never** read by the obstruction fold `toBattleMap()` does for
  `props` (docs/51). This is the entire mechanism that makes decorations "decorative-only": the
  fold function simply never looks at this list.
- `id` is generated once in `:designer` (UUID), same "stable authoring handle, never shown to the
  player" contract every other placement id already has.
- Anchored at its own center (`x, y` is the sprite's midpoint), not top-left like `PropPlacement.at`
  — a free-floating single object reads more naturally centered than corner-anchored; there's no
  "footprint of cells" concept to anchor from here.

## Rendering

`:ui`'s `Board` gets `drawDecorations(map, mapAssets, layer, camera, zoom)`, called at the same
three z-order points `drawProps` already is (Floor/Object/Overhead), sharing `mapAssets.props`
(the id-collection step in `loadMapAssets` gains `map.decorations.map { it.prop.raw }`). Per
decoration: rotate by `rotationDegrees` (free, not quarter-turns) around its own center, flip
(inner transform, same flip-then-rotate order docs/51 established for `PropPlacement`), scale,
tint — same composition technique, continuous inputs instead of stepped ones. `:designer`'s Map
editor canvas mirrors it, same "author sees what player sees" precedent.

## `:designer` authoring: drag-and-drop + rotate handle

New `PaintTool.Decoration(propDef: PropDef?)`. In Decoration-tool mode:

- **Tap empty space** places a fresh `DecorationPlacement` at that exact unsnapped world position
  (screen → world via the canvas's existing `screenToWorld`, divided by `TILE_PX` — no grid
  snapping at all).
- **Press-and-drag on an existing decoration** (nearest one under the press point within a small
  pixel radius, accounting for zoom) repositions it live — a dedicated low-level
  `awaitEachGesture`/`awaitPointerEvent` pointer block, **not** `detectDragGestures`. This file's
  own history already found `detectDragGestures` unreliable in this dev environment for the
  canvas's camera-pan gesture (replaced with `detectTransformGestures` for that reason, per the
  existing comment on `MapCanvas`) — reusing the proven low-level pattern instead of risking the
  same failure mode for a second gesture on the same canvas.
- **Tap (no drag) an existing decoration** selects it, opening an inline editor: scale stepper,
  tint swatches (reusing `PropInstanceEditorPanel`'s five-preset set), flip toggle, delete. Rotation
  is set by the drag handle below, not a field here.
- **Rotate handle**: a small nub drawn a fixed screen-pixel distance from the selected decoration's
  center, in the direction its current `rotationDegrees` already points. Dragging the handle
  computes the new angle via `atan2(dy, dx)` between the decoration's center and the handle's live
  drag position — same low-level pointer technique as the move-drag above, not
  `detectDragGestures`.

Camera panning (`detectTransformGestures`) is unaffected for every other tool and for
empty-space presses even in Decoration-tool mode — only a press landing within hit-radius of an
existing decoration (or its rotate handle, when selected) is claimed by the new gesture block
first.

## Non-goals (v1)

- No resize-by-corner-drag — `scale` is a stepper in the inline editor, not a gesture.
- No multi-select/rubber-band drag of several decorations at once.
- No snapping/alignment guides while dragging (grid snap, angle snap) — fully freeform, matching
  "organic" as asked.
- `:ui`'s Board never lets a *player* interact with a decoration (no tap-to-inspect) — purely
  background art, same as an ordinary `PropPlacement.Floor`/`Overhead` prop today.
