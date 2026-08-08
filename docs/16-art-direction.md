# 16 — Art direction and map resources

Two halves: how things look, and how a hand-crafted battle map is represented as
data.

## The style split, and how to make it deliberate

Three source packs, two art styles:

| Source | Style | Native |
| --- | --- | --- |
| Characters | Pixel art, warm palette, 4-frame walk cycles | 32 px |
| Dungeon props | Hand-inked black on parchment, static | 70 px / square |
| Textures | Printmaker paper, grid overlays | varies |

Pixel characters on ink maps is a clash unless it is framed. The framing that
works: **the map is a hand-drawn battlemat and the characters are miniatures
standing on it.** That is the tabletop metaphor, and these two packs happen to be
exactly the right ingredients for it.

It only holds with deliberate support:

- Every token gets a soft ground ellipse and drop shadow, so it reads as an
  object *on* paper rather than a sprite *in* a scene.
- Enemies render as **ink tokens** in the map's own style — circular, black on
  parchment — not as pixel sprites. There are no monster sprites in the pack
  anyway, and this turns the gap into a decision: the player's party is the only
  thing that is "really there"; everything else is drawn on the mat. The cost is
  that enemies get no walk cycles.
- UI chrome (sheet, turn strip) sits in the ink register, not a Material default.

## Tile size and the scaling trap

Logical tile is **64 px**. Assets are normalised to it: characters upscaled 2×
NEAREST from 32, props downscaled 70→64 with LANCZOS. A manifest (`assets.json`)
carries frame size, sheet layout, facings and per-prop footprint so nothing is
hardcoded.

The trap is density. Rendering a 64 px asset at 48 dp on a 3× screen needs 144
physical pixels and we have 64 — a 2.25× upscale that turns crisp pixel art into
mush.

The rule: **pixel-art tokens render at integer physical scale only.** On a 3×
screen a 32 px sprite is crisp at 3×, 6×, 9× physical, which is 32 dp, 64 dp,
96 dp. Ink props are not pixel art and scale continuously without complaint, so
they can sit at any size.

Two ways to honour that, and the second is better:

- ~~Force the whole board onto integer scales~~ — makes the tile size
  density-dependent and the layout unpredictable across devices.
- **Render the board into a fixed-scale layer and scale that layer.** The board
  composes at a fixed internal tile size; zoom scales the whole composited
  layer. Pixel art stays on one internal scale factor, and the chunkiness that
  comes with zoom is the aesthetic, not a defect.

This is also why snapped zoom steps are worth considering over free pinch
([15-battle-ui.md](15-battle-ui.md#open-questions)).

### Tokens overflow their tile

A 32→64 sprite fills only about 24 px of actual body, and next to heavy ink
lines it disappears. Draw character sprites **larger than the tile, anchored at
the bottom**: a 64 px tile carries a 96 px sprite that overhangs upward. This is
standard in the genre and is the difference between "miniature on the mat" and
"small icon in a box".

Consequence for the renderer: token draw order is by grid row, so a figure
overlapping the tile above is occluded correctly by anything in front of it.

## Highlight styling

Colour alone fails here — parchment background, black ink props, and roughly one
player in twelve with a colour vision deficiency. Every mode carries shape or
pattern as well as hue, and all of them stay in the ink register rather than
becoming flat colour fills. Flat fills read like a spreadsheet on this
background and hide the token standing on the tile.

| Mode | Treatment |
| --- | --- |
| Reachable | Dotted ink outline, 8 % warm tint |
| Path | Dotted line along the route, cost pips at the destination |
| Single target | Solid ink outline, 2 px, crosshair centred |
| AoE | 45° hatch, 12 % tint, outline around the union of the area |
| Affected entity | Ring around the token base |
| Enemy threat | Sparse 135° hatch, no tint |

Opposing hatch angles for AoE and threat so overlap stays readable.

---

# Map resources

Battle maps are **hand-crafted**, assembled from the prop library. Random
generation is a testing scaffold only, not the shipping path.

## Two levels, because one is not enough

Hand-authoring every map tile by tile does not scale to a roguelike's encounter
count, but pure procedural generation gives up the authored quality that is the
point of hand-crafting. The resolution is a middle tier:

```
PropDef        one asset          chest1x1, bed1x2, door1x1
   ↓
RoomStamp      a reusable arrangement    "guard post", "library corner",
               of terrain + props        "collapsed bridge"
   ↓
BattleMapDef   a composition of stamps   the actual encounter map
               plus manual edits
```

`RoomStamp` is the piece the concept was reaching for with "Teilmaps". It is what
makes hand-crafting affordable: a designer builds twenty good rooms once and
composes maps from them, editing freely afterwards. It is also what makes the
random test generator useful rather than noise — random *stamp assembly*
produces something playable, random tile placement does not.

## Terrain and props are separate layers

The single most important rule here: **a prop is art, walkability is data.**

A table drawn at (3,4) does not block movement because it is a table. It blocks
because the terrain layer says that tile is blocked. Inferring walkability from
artwork means the rules depend on a PNG, and the first time a decorative rug
blocks a corridor the cause will be invisible.

```kotlin
@Serializable
data class BattleMapDef(
    val id: MapId,
    val width: Int,
    val height: Int,
    val terrain: List<TerrainRun>,        // run-length encoded, default Floor
    val props: List<PropPlacement>,       // purely visual
    val spawns: List<SpawnZone>,          // where parties and enemies start
    val paper: PaperStyle = PaperStyle.Default,
)

@Serializable
data class PropPlacement(
    val prop: PropId,
    val at: GridPos,
    val layer: PropLayer,                 // Floor | Object | Overhead
    val rotationQuarters: Int = 0,        // 0..3
    val flipX: Boolean = false,
    val tint: Int? = null,
)

enum class PropLayer { Floor, Object, Overhead }   // rugs / furniture / arches
```

`TerrainRun` is run-length encoded because a 44×32 map is 1408 tiles and most of
them are plain floor. Storing each one individually bloats every map file for
nothing.

Terrain carries what the rules need and nothing else: walkable, movement cost,
blocks line of sight, hazard. That is also engine gap 1.4 — `BattleMap` is
currently uniform walkable/blocked with no `TileType`
([17-engine-gaps.md](17-engine-gaps.md)).

```kotlin
@Serializable
data class RoomStamp(
    val id: StampId,
    val width: Int,
    val height: Int,
    val terrain: List<TerrainRun>,
    val props: List<PropPlacement>,
    val connectors: List<Connector>,      // edge tiles where corridors may attach
    val tags: Set<String>,                // "library", "cramped", "chokepoint"
)
```

`connectors` is what lets the test generator assemble stamps sensibly, and what
lets the designer snap rooms together instead of aligning them by eye.

## Spawn zones, not fixed positions

A map is reused across party sizes (1 to 3) and enemy counts (up to 5). Fixed
start coordinates would need a map per configuration.

```kotlin
@Serializable
data class SpawnZone(val role: SpawnRole, val tiles: List<GridPos>)
enum class SpawnRole { Party, Enemy, Elite, Boss, Objective }
```

`startEncounter` fills zones in order from the `EncounterSpec`
([11-run-state.md](11-run-state.md)). A zone with fewer tiles than needed is a
content validation error, caught at load time by the catalog validator rather
than at runtime.

## The desktop designer

See [20-desktop-designer-spec.md](20-desktop-designer-spec.md) for the
detailed, per-editor spec written after actually building Map/Encounter/
Archetype and playtesting through them — status, gaps, and build order for
everything below.

Content is authored in the tool, not by hand in JSON. This is a hard
requirement, and it has a consequence worth stating early: **every schema in
docs 12–18 must be designed as something a tool can edit**, not just something a
parser can read. Deeply nested polymorphic structures that are elegant in Kotlin
are miserable in a property inspector.

The v1 project already had this — `ui/designer/` with map, encounter, enemy and
skill editors — and it was dropped with `composeApp`. It is recoverable from the
baseline commit and worth reading before rebuilding.

Scope, desktop target only:

| Editor | Produces |
| --- | --- |
| Map | `BattleMapDef` — terrain painting, prop placement, spawn zones |
| Stamp | `RoomStamp` — same tools, plus connectors |
| Encounter | `EncounterSpec` — enemy composition, map reference, scaling |
| Archetype | `Archetype` — enemies and classes |
| Action | `ActionDef` — cost, targeting, effect templates |
| Status | `StatusDef` — modifiers, stacking, expiry, damage steps |
| Feature | `FeatureDef` — level-up features |

Two things the tool needs that a plain editor does not:

- **Live validation** against `CatalogValidator`, showing referential errors as
  you type. A dangling `ActionId` should be visible in the editor, not at app
  startup.
- **Preview against the real engine.** The desktop target can depend on
  `:core:rules`, so the encounter editor can actually run `preview()` on an
  action and show the numbers. That is the payoff for a Compose Multiplatform
  desktop target sharing the core.

## Interim: the random generator

Until the designer exists, a `RandomMapGenerator` in test/debug scope assembles
stamps at random via connectors. It is a scaffold with two jobs: unblock
[15-battle-ui.md](15-battle-ui.md), and force the `BattleMapDef` schema to be
exercised by something before a designer is built against it.

It is explicitly not the shipping content path, and it should live where it
cannot accidentally become one.

## Missing assets

Honest inventory of what the pack does not contain:

- **Enemy art** — nine humanoid protagonist sprites, no monsters. The ink token
  approach above covers this; commissioning a monster set remains the
  alternative.
- **Ability and status icons** — the included icon set is TTRPG *product
  tagging* ("solo", "dungeoncrawl", "lethal"), not gameplay iconography. The prop
  library yields `sword`, `shield`, `bow`, `staff`, `star`, `fire` and two
  silhouettes, and that is the entire vocabulary. A status effect system needs
  considerably more.
- **UI frames and a display typeface** in the ink register.
- **Projectile and impact effects** — `fire.png` and three gradients.

Ability and status icons are the most urgent of these, because
[15-battle-ui.md](15-battle-ui.md)'s action bar and status pips cannot be built
without them.
