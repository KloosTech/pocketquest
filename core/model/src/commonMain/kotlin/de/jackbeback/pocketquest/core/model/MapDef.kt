package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * docs/16-art-direction.md's authored map schema — distinct from [BattleMap] (the runtime combat
 * grid `TileType` already attaches to): this is what a designer edits and what gets loaded into a
 * catalog, [BattleMap] is what an encounter actually runs against. `RoomStamp`/`Connector` (the
 * doc's middle "reusable room" tier) are deliberately not implemented yet — the Stamp editor is out
 * of scope for this pass, same as terrain painting is for the Map picker.
 */
@Serializable
data class BattleMapDef(
    val id: MapId,
    /** Author-facing display name — `id` stays the stable reference key everywhere else (encounters, catalog maps). Blank for any map saved before this field existed; display sites fall back to `id.raw` in that case rather than showing an empty label. */
    val name: String = "",
    val width: Int,
    val height: Int,
    val terrain: List<TerrainRun> = emptyList(),
    val wallEdges: List<WallEdge> = emptyList(),
    val props: List<PropPlacement> = emptyList(),
    val spawns: List<SpawnZone> = emptyList(),
    val paper: PaperStyle = PaperStyle.Default,
    /** References a manifest floor-texture id (assets.json's `kind: "floor"` entries) — null renders plain parchment. Purely a Map editor/rendering concern, no rules-engine consumer. */
    val floorTexture: String? = null,
    /** See [WallStyle] — defaults to [WallStyle.Hatch], the original intended look, including for every map saved before this field existed (or before it was still the Boolean `wallHatch`). */
    val wallStyle: WallStyle = WallStyle.Hatch,
    /**
     * docs/33-wall-hatch-osr-packing.md: the baked result of the OSR hatch's packing generator —
     * only meaningful when [wallStyle] is [WallStyle.Osr]. Generated (expensive: a real space-
     * filling pass, not something to re-run per frame) exclusively in `:designer`, either via its
     * "Regenerate Hatch" button or automatically on Save for any Osr-style map that has none yet.
     * `:ui`'s Board only ever renders this stored list — no generation code path exists there at
     * all. Empty for a map that's never been switched to `Osr`, or switched but not yet baked.
     */
    val wallHatchOsr: List<HatchLine> = emptyList(),
    /**
     * The seed [wallHatchOsr] was generated with — deterministic given (wall layout, this seed), so
     * an unrelated catalog Save never reshuffles an already-baked map's look (same layout + same
     * seed = same result). Only the "Regenerate Hatch" button changes it, an explicit "reroll" ask.
     */
    val wallHatchOsrSeed: Long = 0L,
    /** See [WallHatchOsrParams] — the generator inputs [wallHatchOsr] was last baked with, editable in `:designer` and reused by the "Regenerate Hatch" button until changed again. */
    val wallHatchOsrParams: WallHatchOsrParams = WallHatchOsrParams(),
    /**
     * docs/35-wall-background-punch-through.md: only meaningful when [wallStyle] is
     * [WallStyle.Background] — how many tiles beyond the map's own width/height the tiled
     * background image still extends before stopping, instead of tiling the entire pannable
     * viewport regardless of map size (the original behavior, found live to read as "the map is
     * lost in an infinite texture" rather than a bounded scene with a frame around it).
     */
    val backgroundMarginTiles: Int = 4,
    /**
     * Fog of war (unlike every other flag on this class) IS a rules-engine consumer, not purely
     * Map editor/rendering — `:core:rules`' visibility computation and the AI's "hidden enemies
     * skip their turn" check both read this off the runtime [BattleMap] it's carried into. Defaults
     * on, matching every map authored before this field existed.
     */
    val fogOfWar: Boolean = true,
    /**
     * docs/36-map-triggers.md: one-shot, player-controlled-only, per-cell triggers — a generic
     * [EffectTemplate] list (message, damage, heal, spawn, ...) resolved the same way an action's
     * own `effects` already are. IS a rules-engine consumer (unlike `floorTexture`/`wallStyle`
     * above) — carried onto the runtime [BattleMap] and read by both the exploration hop loop and
     * the combat `MoveAlong` handler, same category as [fogOfWar].
     */
    val triggers: List<TriggerPlacement> = emptyList(),
    /** docs/48-gates-and-wander-ai.md: authored, same sibling shape as [triggers]/[props]. IS a rules-engine consumer ([canCross]), never [hasLineOfSight] — see [GatePlacement]'s own doc comment. */
    val gates: List<GatePlacement> = emptyList(),
    /** docs/52-organic-decoration-placement.md: authored, same sibling shape as [props] — purely rendering data, never a rules-engine consumer (see [DecorationPlacement]'s own doc comment). */
    val decorations: List<DecorationPlacement> = emptyList(),
)

/**
 * Run-length encoded terrain — doc16: "a 44x32 map is 1408 tiles and most of them are plain
 * floor, storing each one individually bloats every map file for nothing." The doc names the
 * technique but not an exact encoding; this is the simplest one a straight horizontal or vertical
 * run of one [tile] can express. `:core:rules`' `MapExpansion.kt` converts between this and a flat
 * per-tile map for painting/runtime use — [TerrainRun] itself stays a pure data shape.
 */
@Serializable
data class TerrainRun(val start: GridPos, val length: Int, val horizontal: Boolean, val tile: TileType)

@Serializable
data class PropPlacement(
    val prop: PropId,
    val at: GridPos,
    val layer: PropLayer,
    val rotationQuarters: Int = 0,
    val flipX: Boolean = false,
    val tint: Int? = null,
)

/** Rugs / furniture / arches — doc16's own examples for each layer. */
@Serializable
enum class PropLayer { Floor, Object, Overhead }

/**
 * docs/52-organic-decoration-placement.md: a free-floating, purely decorative sibling of
 * [PropPlacement] — [x]/[y] are continuous tile-unit coordinates (same "3.25 = 3.25 tiles from
 * origin" convention [HatchLine] already uses), not a [GridPos]. Anchored at its own CENTER, not
 * top-left like [PropPlacement.at] — there's no footprint-of-cells concept to anchor a single
 * free-floating object from. [rotationDegrees] is a free angle, unlike [PropPlacement]'s
 * quarter-turn-only [PropPlacement.rotationQuarters]. References the same [PropId] catalog every
 * grid-snapped prop does — deliberately NOT a separate decoration-only asset pool. Never read by
 * `toBattleMap()`'s obstruction fold (docs/51) — this IS the mechanism that makes a decoration
 * decorative-only, not a flag anywhere.
 */
@Serializable
data class DecorationPlacement(
    val id: DecorationId,
    val prop: PropId,
    val x: Float,
    val y: Float,
    val rotationDegrees: Float = 0f,
    val flipX: Boolean = false,
    val tint: Int? = null,
    val scale: Float = 1f,
    val layer: PropLayer = PropLayer.Object,
)

/**
 * docs/51-props-catalog-and-placement.md: props become real `Catalog` content — [id] stays
 * permanently paired to one underlying manifest sprite asset (never independently re-pointed at a
 * different one; [id]`.raw` == that asset's own id, set once at creation/migration), so
 * [PropPlacement.prop] keeps resolving to a sprite exactly the way it always has. [name]/[tags] are
 * freely editable authoring metadata with no mechanics attached. [footprintTilesW]/[footprintTilesH]
 * default to a copy of the manifest asset's own declared size at creation time — `:core:model`/
 * `:core:rules` have no access to `:designer`'s `AssetManifest` at all, so the footprint has to be
 * duplicated here for [blocksMovement]/[blocksLoS] obstruction-folding (`toBattleMap()`) to size
 * correctly — but docs/53 made it freely author-editable afterward too (the Props tab's own
 * footprint steppers), independent of whatever the underlying sprite image's actual pixel
 * dimensions are. Only ever consulted for a GRID-snapped [PropPlacement]'s obstruction size — a
 * free-floating `DecorationPlacement` referencing the same `PropDef` ignores this entirely and
 * scales the sprite via its own `scale` field instead. Defaulting
 * both flags to `false` means every prop placed before this pass existed stays purely decorative
 * until an author opts a `PropDef` into blocking, matching the pre-docs/51 behavior exactly.
 */
@Serializable
data class PropDef(
    val id: PropId,
    val name: String = "",
    val tags: Set<String> = emptySet(),
    val footprintTilesW: Int = 1,
    val footprintTilesH: Int = 1,
    val blocksMovement: Boolean = false,
    val blocksLoS: Boolean = false,
)

/** doc16's ink-parchment visual family for a map — a single default for now, no customization surface yet (that's art-direction work, not this pass's). */
@Serializable
enum class PaperStyle { Default }

/**
 * docs/32-wall-hatch-osr-style.md: which of the wall-rendering styles this map's Wall-type cells
 * use — a flat fill, the original procedural crosshatch (`:ui`'s `drawWallHatch`), or the denser
 * clustered/angled OSR-map style (`drawWallHatchOsr`), or [Background] (docs/35): a shared texture
 * image tiled under the whole map, with Wall cells left unpainted so it shows through — "punched"
 * relative to every Floor cell, which still paints opaquely over it same as any other style.
 * Replaces the earlier `wallHatch: Boolean` (on/off) now that there's more than one hatch style.
 * Purely a Map editor/rendering concern, no rules-engine consumer, same as `floorTexture`.
 */
@Serializable
enum class WallStyle { Flat, Hatch, Osr, Background }

/**
 * docs/33-wall-hatch-osr-packing.md: one pre-generated OSR-hatch stroke — endpoints and pen width
 * in unscaled tile-unit floats (e.g. `x0 = 3.25` means 3.25 tiles from the map origin), the same
 * "tile units, not pixels" convention every other map-geometry field already follows — so it stays
 * correct regardless of whatever pixel-per-tile constant the renderer happens to use.
 */
@Serializable
data class HatchLine(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val width: Float)

/**
 * docs/34-wall-hatch-osr-configurable-params.md: every tunable knob `generateWallHatchOsr` reads,
 * exposed for real authoring control instead of hardcoded constants — resolved after the first two
 * rounds of live tuning kept needing a code change to try a different look. Defaults match the
 * values already tuned live so far. Kept off [BattleMap] (the runtime type) entirely — these only
 * matter to `:designer`'s generator, never read at render time (only [HatchLine]s are).
 */
@Serializable
data class WallHatchOsrParams(
    /** Sub-cells per tile side — finer means smaller, more numerous strokes; coarser means chunkier ones. */
    val subcellsPerTile: Int = 6,
    /** How many whole tiles deep into a wall mass the hatch still bothers to exist, fading to nothing beyond. */
    val fadeDistanceCells: Int = 2,
    /** Fraction (0..1) of eligible sub-cells the fill pass aims to claim before stopping. */
    val targetCoverage: Float = 0.92f,
    val minLineLengthSubcells: Int = 2,
    val maxLineLengthSubcells: Int = 5,
    /** How many parallel strokes one growth attempt places together, side by side — the "clusters of 3 to 5 parallel lines" the reference image shows. */
    val minGroupSize: Int = 3,
    val maxGroupSize: Int = 5,
    /**
     * Cosmetic wobble (± degrees) applied to each already-grown line's drawn endpoints around its
     * own midpoint — NOT the growth direction itself, which must stay exactly grid-aligned
     * (0°/45°/90°/135°) for the occupancy tracking to work. Purely a hand-drawn-imperfection touch;
     * 0 draws perfectly straight snapped lines.
     */
    val angleJitterDegrees: Float = 6f,
    /** Sub-cells per side of one shared-angle region — larger reads as bigger coherent "streams" between direction changes. */
    val angleRegionSubcells: Int = 4,
    /** Pen width, as a fraction of one tile. */
    val lineWidthFraction: Float = 0.03f,
)

/**
 * doc16: "a map is reused across party sizes (1 to 3) and enemy counts (up to 5) — fixed start
 * coordinates would need a map per configuration." [EncounterSpec] picks how many of each role to
 * fill; `startEncounter` fills these zones in order.
 */
@Serializable
data class SpawnZone(val role: SpawnRole, val tiles: List<GridPos>)

/**
 * docs/37-lootable-containers.md: [LootCommon]/[LootRare]/[LootEpic]/[LootLegendary] are placement
 * tiles for a lootable container (see [LootDef]/[LootPlacement]) — pooled and filled exactly like
 * [Enemy]/[Elite]/[Boss] already are, just consumed by `EncounterSpec.lootSpawns` instead of
 * `EncounterSpec.enemies`.
 */
@Serializable
enum class SpawnRole { Party, Enemy, Elite, Boss, Objective, LootCommon, LootRare, LootEpic, LootLegendary }

/**
 * docs/37-lootable-containers.md: a reusable lootable container definition, placed at rarity-tier
 * [SpawnZone] tiles and referenced by `EncounterSpec.lootSpawns`. [closedSprite]/[openSprite] are
 * manifest prop ids (docs/23), resolved through the same `AssetManifest.prop` lookup [PropPlacement]
 * rendering already uses. [table] reuses [LootEntry] verbatim — the same independent-Bernoulli-roll
 * shape the old per-encounter loot list used, now owned by the container instead of the encounter so
 * the same container can be reused across many encounters.
 */
@Serializable
data class LootDef(
    val id: LootId,
    val name: String = "",
    val closedSprite: String? = null,
    val openSprite: String? = null,
    val table: List<LootEntry> = emptyList(),
)

/** docs/37-lootable-containers.md: one resolved container placement in a live encounter — see [GameState.lootPlacements]. */
@Serializable
data class LootPlacement(val at: GridPos, val loot: LootId)

/**
 * docs/36-map-triggers.md: [id] is generated once in `:designer` at placement time (a UUID string),
 * never author-typed — it exists purely so [GameState.firedTriggers] has something stable to track,
 * never shown to a player. [effects] is exactly the same authored vocabulary [ActionDef.effects]
 * already uses — `Ref.Caster` resolves to whichever entity stepped on the trigger, `Ref.EachTarget`
 * to the whole living player party (see `core/rules/Triggers.kt`).
 */
@Serializable
data class TriggerPlacement(val id: TriggerId, val at: GridPos, val effects: List<EffectTemplate> = emptyList())
