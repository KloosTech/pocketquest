# 51 — Props become real content: `PropDef`, rotation, obstruction

Every other placeable thing in this app is authored `Catalog` content edited
in its own tab (`ActionDef`/`ItemDef`/`LootDef`/...). Props are the one
exception: `PropPlacement.prop: PropId` is just the raw `ManifestAsset.id`
string, there's no `Catalog` entry for a prop at all, and the Map editor's
Prop tool (`MapEditorPanel.kt`) can only pick a bare sprite and stamp it down
— clicking an already-placed prop's cell silently **replaces** it with a
fresh default rather than opening it for editing, unlike `Trigger`/`Gate`.

Two more real gaps found while reading the current code, not just missing
UI: `PropPlacement.rotationQuarters`/`flipX`/`tint` already exist as fields
(`MapDef.kt`) but nothing ever *sets* them (no control exists) and nothing
ever *reads* them — `:ui`'s `drawProps` (`App.kt:999-1011`) draws every prop
at rotation 0, unflipped, untinted, full stop. And every prop today is
purely decorative — `MapDef.kt`'s own doc comment says so explicitly — which
docs/Campain_1's cluttered rooms (barrels, benches, statues you'd expect to
actually block a tile) make feel wrong.

## Decided with the user before implementation

- **`PropDef` becomes real `Catalog` content**, migrated 1:1 from today's
  raw manifest-asset placements — a new `Props` tab manages it, same as
  Items/Loot.
- **Props gain `blocksMovement`/`blocksLoS` flags** and become a genuine
  rules-engine consumer (folded into `BattleMap.terrain`, not a parallel
  obstruction system) — not purely decorative anymore.
- Rotation gets a real control in the placement UI, and both `:ui` and
  `:designer` actually render rotation/flip/tint.

## `PropDef`: new catalog type

```kotlin
// core/model/Catalog.kt or a new Prop.kt sibling to LootDef
@Serializable
data class PropDef(
    val id: PropId, // == the underlying ManifestAsset.id — see migration below, never independently renamed
    val name: String = "",
    val tags: Set<String> = emptySet(), // free-text author categories ("furniture", "hazard-dressing", ...) — filtering only, no mechanics read this
    val footprintTilesW: Int = 1,
    val footprintTilesH: Int = 1,
    val blocksMovement: Boolean = false,
    val blocksLoS: Boolean = false,
)
```

`Catalog.props: Map<PropId, PropDef> = emptyMap()`.

**`id` stays permanently paired to one `ManifestAsset`** — `PropDef` never
gets its own independent `spriteId` field. This is the smallest version of
"props are real content": renaming a prop's *display name*/tags is free
authoring, but swapping which sprite an existing `PropId` points to isn't a
feature this pass adds (an author who wants a different look places a
*different* prop). This keeps `:ui`'s sprite-resolution path
(`assets.props[placement.prop.raw]`, `App.kt:1004`) completely unchanged —
`Catalog.props` is consulted for `blocksMovement`/`blocksLoS`/`name`/`tags`
only, never for "which bitmap to draw."

`footprintTilesW`/`H` are copied from the `ManifestAsset`'s own
`tilesW`/`tilesH` at the moment a `PropDef` is created (see migration) —
`:core:model`/`:core:rules` have no access to `AssetManifest` at all (it's a
`:designer`-only, JVM/Compose-Resources-reading object), so the footprint
has to be duplicated onto the portable `PropDef` for `toBattleMap()` to fold
obstruction correctly. Not independently editable in v1 — see Non-goals.

## Migration: auto-generate, never destructive

On every `:designer` load, for every `ManifestAsset` in
`AssetManifest.placeableProps` with no matching `catalog.props[PropId(asset.id)]`
yet, synthesize one: `PropDef(id = PropId(asset.id), name = asset.id,
footprintTilesW = asset.tilesW ?: 1, footprintTilesH = asset.tilesH ?: 1)`
(obstruction flags default `false` — every existing placement stays purely
decorative until an author opts a `PropDef` into blocking). Purely additive
— never overwrites an already-authored `PropDef`, safe to run unconditionally
on every load rather than a one-time migration step. Every existing
`PropPlacement.prop` value keeps resolving (both to a sprite, unchanged, and
now also to a `PropDef`) with zero data loss.

## Obstruction: folded into `BattleMap.terrain` at `toBattleMap()` time

Consistent with how this codebase already treats derived-once map data
(baked `wallHatchOsr`, expanded `TerrainRun`s) rather than teaching every
consumer (`canCross`, `hasLineOfSight`, pathfinding) a second obstruction
source to check. `BattleMapDef.toBattleMap()` (`core/rules/content/MapExpansion.kt`)
**gains a `Catalog` parameter** — the one real breaking signature change
this pass makes, named plainly: every call site (`StartEncounter.kt`, and
every `def.toBattleMap()` in `MapExpansionTest.kt`) now passes a `cat:
Catalog`. `StartEncounter.kt` already has one in scope; the tests pass
whatever fixture catalog they already build (or a catalog with an empty
`props` map, which folds nothing — behaviorally identical to today for any
test that doesn't care about this feature).

```kotlin
fun BattleMapDef.toBattleMap(cat: Catalog): BattleMap {
    val terrainWithProps = props.fold(expandedTerrain) { acc, placement ->
        val def = cat.props[placement.prop] ?: return@fold acc
        if (!def.blocksMovement && !def.blocksLoS) return@fold acc
        val (w, h) = footprintFor(def, placement.rotationQuarters) // swaps W/H on a 90°/270° rotation
        acc.toMutableMap().apply {
            for (dc in 0 until w) for (dr in 0 until h) {
                val cell = GridPos(placement.at.col + dc, placement.at.row + dr)
                val existing = this[cell] ?: TileType.Floor
                this[cell] = existing.copy(
                    walkable = existing.walkable && !def.blocksMovement,
                    blocksLoS = existing.blocksLoS || def.blocksLoS,
                )
            }
        }
    }
    // ...rest unchanged, using terrainWithProps in place of the plain expanded terrain...
}
```

`walkable` is AND-ed (a blocking prop always wins over whatever the floor
underneath was), `blocksLoS` is OR-ed (either source blocking is enough) —
the same merge logic a designer would expect from "two independent
obstruction sources stacked on one cell." **Footprint anchoring**: `at`
stays the top-left corner of the placement's *unrotated* bounding box even
after a 90°/270° rotation swaps which axis is longer — avoids re-deriving a
new anchor point on every rotate, at the cost of a rotated prop's footprint
"growing" from its original top-left corner rather than truly pivoting
around its own center. Acceptable for v1 (see Non-goals).

**Free correctness this produces, worth noting:** fog-of-war's
`revealAdjacentWalls` (`Visibility.kt`) already reveals any `blocksLoS`
tile adjacent to a revealed open one — a blocking, sight-blocking statue
folded into `terrain` gets picked up by that exact same rule with no new
code, same as a real wall would. `BattleMap.walls` (every non-walkable
tile) also now includes blocking-prop cells, which is the correct reading
of that getter's own contract ("every unwalkable tile"), not a special
case to guard against.

## Rendering: `:ui` and `:designer` actually apply rotation/flip/tint

`drawProps` (`App.kt`) currently draws every sprite unrotated, unflipped,
untinted — it never reads those three `PropPlacement` fields at all. Fixed
to: rotate the drawn image `90 * rotationQuarters` degrees around the
footprint's own center (Compose's `DrawScope.rotate`, already imported in
this file for projectiles), mirror horizontally first when `flipX` is true
(draw into a horizontally-flipped transform before rotating, so flip and
rotation compose the same way any 2D scene graph does — flip is defined
relative to the sprite's own unrotated orientation, not screen space), and
apply `tint` (the packed `Int?` ARGB already on the model) as a color
filter over the draw call, skipped entirely when `null` (today's look,
unchanged). `:designer`'s own Map editor canvas prop-rendering code
(`MapEditorPanel.kt`, around the sprite-sheet draw block) gets the identical
treatment — same "author sees what the player sees" rule docs/36 and
docs/48 both already follow for every other placeable.

## `:designer`: reworked Prop tool + new `Props` tab

**Placement interaction** changes from "click always overwrites" to the
`Trigger`/`Gate` pattern: clicking an empty cell places a fresh
`PropPlacement` using the tool's currently-selected `PropDef` (rotation 0,
no flip, no tint — today's defaults); clicking a cell already covered by an
existing prop's footprint (not just its exact anchor cell) opens that
placement's inline editor instead of replacing it. The inline editor
exposes: a rotate control (a single button cycling `rotationQuarters`
0→1→2→3→0, matching how a 90°-at-a-time constraint is normally exposed —
no free-angle rotation), a flip toggle, a `PropLayer` dropdown (already
existed as a hardcoded default, now actually editable), a tint color
picker, and delete.

**New `DesignerTab.Props`** (`App.kt`) + `PropPanel.kt`: CRUD over
`Catalog.props`, matching `ItemPanel`'s list-editor style — name, tags
(free-text chip entry), `blocksMovement`/`blocksLoS` checkboxes, and a
read-only display of the footprint/sprite it's permanently paired to (see
"stays permanently paired," above — this tab edits metadata, not the
sprite link itself). The Map editor's Prop tool picker (`PaintTool.Prop`)
switches from listing `AssetManifest.placeableProps` directly to listing
`catalog.props.values` (optionally filtered by tag), each entry still
resolving to the same underlying sprite for the actual paint/preview
bitmap.

## Non-goals (v1)

- No independent "collision footprint smaller/larger than the visual
  sprite" — `footprintTilesW`/`H` are copied from the sprite once at
  `PropDef` creation and not separately editable. A prop wanting a
  different collision shape than its art needs a genuinely different sprite
  asset, not a decoupled hitbox field.
- No true pivot-around-center rotation — see the anchoring note above; a
  rotated non-square prop's footprint grows from its original top-left
  corner rather than recentering.
- No re-pairing an existing `PropDef.id` to a different sprite (renaming
  the *display* name/tags is fine, the sprite link is fixed at creation).
- No mid-encounter prop placement/removal effects (a `SpawnProp`-style
  primitive) — props stay static per-map authored content, same as today;
  only their *rendering* and *obstruction folding* change this pass.
