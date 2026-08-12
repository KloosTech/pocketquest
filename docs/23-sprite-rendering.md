# 23 — Sprite rendering: from colored circles to real art

## Context

Every entity on the board today — party member, enemy, anything — renders as
a single flat-colored circle (`App.kt`'s `drawEntity`, faction-colored via
`colorFor`). `assets/sprite_sheet_256.png` (1024×1024, a 4×4 grid of 256×256
cells, 14 of 16 populated: 4 goblin/orc variants, 4 projectile/spell orbs —
lightning, water vortex, stone, fire — 4 hero classes, 2 chest states) is the
first real character art. Goal: an `Archetype` can be linked to a sprite in
`:designer` and render as that sprite in actual gameplay instead of a circle,
plus the same linking for projectiles and chests. `sprite_rendering_research.md`
(user-supplied, generic Compose Canvas performance advice — phase-skipping
recomposition, GPU texture upload, sprite-sheet single-bind vs many textures,
cached static layers, frustum culling) is the performance angle; this doc
covers what that means concretely for this codebase.

## What exists today

Traced end to end through `App.kt`, `VisualWorld.kt`, `GameSpriteLoader.kt`/
`GameAssetManifest.kt` (`:ui`), `SpriteLoader.kt`/`AssetManifest.kt`
(`:designer`), `Archetype.kt`, `MapDef.kt`, `MapEditorPanel.kt`.

**The entire "colored circle" footprint is two spots**, both driven by one
faction-keyed (not archetype-keyed) `colors: Map<EntityId, Color>` built once
at encounter start: `drawEntity` (the board token itself) and
`TurnOrderStrip`'s token dot. `VisualEntity` already anticipates this being
temporary — its own doc comment: *"no sprite/animation-clip system yet...
add back when real art exists."*

**Two parallel asset-loading stacks exist, deliberately not shared**: a
JVM-only `:designer` one (`SpriteLoader`/`AssetManifest`, raw
`java.io.File`/`ImageIO`) and a cross-platform `:ui` one
(`GameSpriteLoader`/`GameAssetManifest`, Compose Multiplatform Resources —
`Res.readBytes("files/...")`, works uniformly on desktop/Android/iOS). Both
cache loaded (and missing) results in a map, both driven by the same
`assets.json` manifest shape:

```kotlin
data class ManifestAsset(val id: String, val file: String, val tilesW: Int? = null, val tilesH: Int? = null, val kind: String = "prop")
data class AssetManifestFile(val tile: Int = 64, val props: List<ManifestAsset> = emptyList(), val overlays: List<ManifestAsset> = emptyList())
```

**`assets.json` already has a richer, currently-unused `characters` schema**
— per-character multi-frame/multi-facing animation entries (`frameW/frameH`,
`cols/rows`, `fps`, `facings: [South, West, East, North]`) — parsed by
nothing in Kotlin today (silently dropped, `ignoreUnknownKeys = true`). The
new sprite sheet doesn't need this: it has exactly one static pose per
character, no facings, no animation frames. Worth knowing this richer schema
is sitting there authored-but-dormant for a future walk-cycle pass, not
needed for this one.

**A real, load-bearing maintenance trap**: every asset physically exists in
**two places** — `assets/normalized/**` (repo root, what `:designer`'s
JVM-only loader reads) and `ui/src/commonMain/composeResources/files/normalized/**`
(what actually ships in the game on every platform, since only Compose
Resources are bundled cross-platform). These are byte-identical today but
there is **no build step, sync task, or symlink** connecting them — someone
manually copies after running the normalize script. `assets/sprite_sheet_256.png`
currently exists *only* at repo root: right now it is invisible to real
gameplay on every platform, regardless of anything else this spec decides.

**Prop picker in `:designer` is the exact UI template to copy** —
`MapEditorPanel.kt`'s prop `InkSelect` already does "dropdown with a live
thumbnail per option, plus a persistent preview thumbnail of the current
selection" for `ManifestAsset` picks, via a small `PropThumbnail` composable
(`Canvas` + `drawImage` at a fixed small size). `ArchetypePanel.kt` has zero
image fields today but is a flat list of labeled sections (`NAME`, `ACTIONS`,
`ABILITY SCORES`, ...) — a `SPRITE` section slots in the same way.

**No projectile concept exists anywhere** (`grep`ped `core/model`/`core/rules`/
`ui` for "Projectile" — zero hits beyond doc16 already flagging this as a
known gap: *"Projectile and impact effects... a known gap"*). Combat has no
travel animation for ranged/spell attacks today — a ranged hit gets the same
generic attacker-pulse as melee, nothing flies across the board. Linking a
sprite to "a projectile" is therefore not an asset swap on an existing
mechanic — the travel animation itself doesn't exist yet.

**No chest concept exists as an entity or interaction** — `chest1x1` etc.
already exist purely as decorative `Prop` entries (place-and-forget map
furniture, `PropPlacement`/`BattleMapDef.props`), with zero gameplay hook.
Actual loot is a wholly separate, non-spatial system: `EncounterSpec.loot`
auto-grants items into `RunState.inventory` the instant an encounter ends —
no "walk up and open a container" interaction exists at all. The sheet
having both a closed and an open state strongly implies real
open/interact/loot behavior is wanted, but that is new model + rules surface,
not a rendering change — see open question #2.

## Proposed shape (for the parts that are unambiguous)

### Archetype → sprite linking

`Archetype` gains one new field:

```kotlin
val spriteId: String? = null // key into the asset manifest; null keeps today's colored-circle fallback
```

New field with a default — no schema bump, same reasoning every prior
addition to this project has used. `null` is a real, permanent, supported
state (not just a migration shim): a homebrew archetype an author hasn't
drawn art for yet still renders correctly as a circle, forever, if that's
where it stays.

`colors: Map<EntityId, Color>` stays exactly as it is (fallback still needs a
color); `drawEntity` becomes: if the entity's archetype has a `spriteId` and
that sprite is loaded, `drawImage` it centered on the tile instead of
`drawCircle`. Same branch in `TurnOrderStrip`'s token dot, sprite thumbnail
instead of a color dot when one exists.

### Physical asset pipeline

The 14 individual 256×256 PNGs (see open question #3) each become an
ordinary `ManifestAsset` entry — new `kind` values `"character"` and
`"projectile"` (so `GameAssetManifest.placeableProps`, which filters
`kind == "prop"`, doesn't surface them in the map editor's decorative-prop
picker — they're linked via `Archetype.spriteId`, not placed via
`PropPlacement`) plus two ordinary `kind = "prop"` entries for the chests
(placed via the existing prop pipeline like any other scenery). All 14 files
move into `ui/src/commonMain/composeResources/files/normalized/sprites/`
(the one true location per open question #4's resolution).

### Scale

The existing pipeline's logical tile is 64px (`assets.json`'s own `"tile":
64`, doc16's "characters upscaled 2× NEAREST from 32, props downscaled 70→64
LANCZOS, pixel-art tokens render at integer physical scale only"). The
board's own tile constant is `TILE_PX = 48f`. 256px source cells don't
divide cleanly into either — doc16's "integer physical scale only" rule
can't be honored literally at arbitrary zoom levels no matter what's picked
here. Recommendation: draw sprites at a fixed fraction of the tile (e.g. the
same `TILE_PX * zoom * 0.9` footprint a token roughly occupies today, scaled
from the 256px source with normal bitmap filtering) rather than chasing
integer-multiple purity for these specific assets — flagged as a real
doc16 tension, not silently ignored, but not blocking this feature on a
wider tile-size migration.

## Open questions

**1. RESOLVED: skip the heavy optimization machinery.** Apply the
already-free/no-cost piece this project already does (load-once-not-per-frame,
the existing `loadMapAssets`/`produceState` pattern) and skip atlas slicing,
explicit `prepareToDraw`, and entity frustum culling as premature for a
game that stays well under the "hundreds of sprites" regime that advice
targets. Cheap to add later if a real frame-time problem ever shows up.

**2. RESOLVED: decorative reskin only.** `chest_closed_256.png`/
`chest_upen_256.png` become two more entries in the existing Prop picker —
an author places one or the other as static scenery. No interaction, no
loot hookup, no state transition. Real open/interact/loot is real new
model+rules surface (bridging `PropPlacement` and loot content), explicitly
a separate future feature, not bundled here.

**3. RESOLVED: pre-sliced individual PNGs, no slicing needed.** The sheet's
14 cells already exist as separate 256×256 files at repo root
(`goblin_256.png`, `goblin_brute_256.png`, `goblin_archer_256.png`,
`goblin_shamane_256.png`, `fireball_256.png`, `iceball_256.png`,
`earthball_256.png`, `lightningball_256.png`, `Knight.png`, `Priest_256.png`,
`Rouge_256.png`, `Wizard_256.png`, `chest_closed_256.png`,
`chest_upen_256.png`). No offline slicing, no draw-time `srcOffset`/`srcSize`
math, no new manifest schema — each becomes one ordinary `ManifestAsset`,
exactly like every existing prop, reusing `GameSpriteLoader.load`/
`PropThumbnail`/the existing picker UI verbatim.

**4. RESOLVED: fix the `assets/normalized` ↔ `composeResources`
duplication.** Lowest-risk fix (no new Gradle sync task, which would need
verifying across desktop/Android/iOS resource bundling blind): make
`ui/src/commonMain/composeResources/files/normalized/` the single source of
truth. Repoint `:designer`'s `SpriteLoader`/`AssetManifest` path-resolution
candidates there (currently repo-root `assets/normalized`), repoint
`tools/normalize_assets.py`'s output path there too, delete the now-redundant
root `assets/normalized/` tree. One directory, one writer, one reader for
both `:designer` and `:ui`, nothing to forget to sync.

## Non-goals

- Directional facing / walk-cycle animation — the richer `characters` schema
  exists but nothing here needs it; the new sheet has one static pose per
  character.
- Projectile *travel* animation is unavoidably new scope (no such mechanic
  exists to attach a sprite to) — the 4 projectile sprites get registered in
  this pass (manifest entries, `kind = "projectile"`) so they exist as
  linkable assets, but nothing renders them yet; the travel-animation Beat
  is a real follow-up pass, not bundled here.
- Real chest interaction (open/loot) — decorative reskin only this pass.

## Implementation plan

1. **Asset pipeline** — repoint `:designer`'s `SpriteLoader`/`AssetManifest`
   and `tools/normalize_assets.py`'s output at
   `ui/src/commonMain/composeResources/files/normalized/`; delete the
   redundant root `assets/normalized/` tree; move the 14 new PNGs into a new
   `sprites/` subfolder there; register all 14 in `assets.json` (8×
   `kind="character"`, 4× `kind="projectile"`, 2× `kind="prop"` for the
   chests). Verify `:designer`'s existing floor/prop pickers still work
   after the repoint (regression check on the fix itself).
2. **`core:model`** — `Archetype.spriteId: String? = null`.
3. **`:ui`** — load each distinct archetype-linked sprite once per encounter
   (mirrors `loadMapAssets`'s `produceState` pattern, keyed off
   `initialState`/`catalog` rather than `state.map`); `drawEntity` draws the
   sprite when present, falls back to today's circle when not (same branch
   in `TurnOrderStrip`'s token dot).
4. **`:designer`** — a `SPRITE` section in `ArchetypePanel.kt`, same
   thumbnail-dropdown pattern as the existing prop picker.

Verification per pass: full cross-module regression sweep, `v1` untouched,
live check in the designer (existing map editor still loads assets after
pass 1; a re-skinned archetype actually shows its sprite on the board after
passes 2-4).
