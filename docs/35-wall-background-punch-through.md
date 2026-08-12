# 35 — Background wall style: punch-through

A fourth `WallStyle` — instead of drawing Wall cells with ink (flat, hatch, or OSR hatch), a single
shared texture (`assets/background.png`, imported as the `background` manifest asset) is tiled
under the whole map. Floor cells still paint opaquely over it exactly as they always have; Wall
cells simply paint nothing at all, so the background shows through — "punched" relative to every
floor cell around them.

## Rendering: draw first, paint nothing to reveal it

`drawGrid` (`:ui`'s Board) and `MapCanvas` (`:designer`'s Map editor) both already establish a
strict draw order: background image → floor fill → grid lines → wall layer → wall outline. Nothing
about that order changed — `WallStyle.Background` just adds a new first pass (the tiled image) and
a new wall-layer case that draws nothing:

- The background image is drawn **before** the floor fill, at the very top of the function. Every
  other draw pass after it (floor fill, grid lines, wall outline) is completely unaware anything
  changed — they behave exactly as before.
- `WallStyle.Background`'s branch in the wall-layer `when` is `Unit` — no fill, no hatch, nothing.
  The image drawn underneath is still sitting there for exactly those cells.
- The automatic wall-mass outline (`wallOutlineSegments`) still draws — unconditional for every
  style, so a Background map still gets a clean ink boundary against the texture, same as every
  other style.

## Bounded to the map + a configurable margin, not the whole viewport

First live look: tiling across the entire pannable viewport (culled only, not bounded) read as
"the map lost in an infinite texture" rather than a bounded scene with a frame around it — resolved
by bounding the tiled region to the map's own `[0, width] x [0, height]` footprint, extended by a
new per-map `backgroundMarginTiles` field (default 4, a stepper next to WALL TEXTURE in `:designer`,
only shown for `WallStyle.Background`). The viewport is still used to cull which tiles within that
bound actually get drawn (performance), but the bound itself is a hard limit regardless of camera
position — panning far past the map's edge no longer reveals more texture.

Clipped precisely to that map+margin rect (not just tile-granularity culled) via `clipRect` — found
live (a separate bug from the above): without it, the tiled image visually spilled out over
`:designer`'s own sibling UI (the settings/buttons sitting above the Map editor canvas in the same
window), the same class of bleed `drawWallHatch`'s own per-cell `clipRect` already guards against —
here the clip doubles as the actual mechanism that makes the margin a hard edge.

The margin itself fades rather than cutting off hard: `drawMarginFade` overlays four edge
`Brush.linearGradient` bands (PAPER alpha 0 right at the map's own edge, fading to fully opaque
PAPER by the outer margin edge) plus four corner `Brush.radialGradient` squares (centered on the
map's own corner point, so the edge fades meet a matching diagonal fade instead of a hard square
notch) — the same "edges + corner" gradient composition `drawWallShadows` already established
(docs/31), just applied to all four sides/corners here instead of two (a directional light only
ever needed North/West).

## Floor cells need a guaranteed opaque fill once anything is drawn under them

A second live-found bug, `:ui`'s Board only: "no floor texture configured" previously meant the
floor-fill pass drew *nothing at all* for that cell — safe only because the Canvas's own blank
backdrop happened to already be the same `PAPER` tone. Once the background image is drawn first,
that assumption breaks — an untextured floor cell let the background show straight through it,
identically to a Wall cell, which defeats the entire "floor is solid, walls punch through" premise.
Fixed: whenever a background image is active and a cell has no floor texture, it now gets an
explicit opaque `PAPER` `drawRect` instead of nothing. `:designer`'s own Map editor canvas already
did this correctly from the start (`drawFloor`'s existing `floorPatch == null -> drawRect(PAPER,
...)` fallback) — only `:ui`'s Board needed the fix.

## Tiled, not stretched

The image (1152×927px, not a perfect square) is tiled at a fixed scale — one repeat spans
`BACKGROUND_TILE_SPAN` (6) map tiles — rather than stretched across the whole map's bounding box.
Stretching a single image over an arbitrary map size would distort proportions differently for
every map; tiling at a fixed tile-span reads consistently regardless of map dimensions or zoom,
the same reasoning a floor texture already tiles per-cell instead of stretching to the room.

## Shared between `:ui` and `:designer`

`drawBackgroundImage` (`WallBackground.kt`) takes `screenToWorld`/`toScreen` as lambda parameters
rather than raw camera/zoom, the same reason `drawWallHatch` already does — `:ui`'s Board and
`:designer`'s Map editor canvas have differently-shaped conversion helpers (4-arg vs 3-arg), so the
function stays decoupled from either one's specific signature while still being the literal same
code path both call — an author sees the same background while editing that they'll see in
Playtest, matching every other wall style's shared-rendering precedent.

## Asset pipeline

`background.png` copied into `ui/src/commonMain/composeResources/files/normalized/sprites/` (the
single source of truth both modules read, docs/23) with one new manifest entry, `kind: "background"`
— no new manifest accessor needed, `AssetManifest.prop(id)`/`GameAssetManifest.prop(id)` already
search the flat list regardless of kind. `BACKGROUND_ASSET_ID` is the one place the literal id
string `"background"` lives on the `:ui` side; `:designer`'s `AssetManifest.prop(BACKGROUND_ASSET_ID)`
imports that same constant rather than repeating the string.

## Non-goals

- No per-map background picker — one shared image for every map that opts into this style, matching
  the user's ask ("use this as the background for the whole map"). A picker (like `floorTexture`'s)
  is easy to add later if more than one background image ever exists.
- No parallax/independent scroll — the image is tiled in the same world space every other map
  element lives in, panning with the map exactly like the grid does.
