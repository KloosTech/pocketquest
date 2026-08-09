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
    /**
     * Whether Wall-type cells render as procedural hand-drawn crosshatch (`:ui`'s `drawWallHatch`)
     * instead of a flat fill — defaults on, since it's the intended look for every map, including
     * every one already saved before this field existed. Not a sprite/manifest reference (an earlier
     * sprite-sheet attempt looked visibly tiled — hand-drawn crosshatch can't be chopped into
     * independent cell-aligned squares), just an on/off switch; the hatch itself is drawn live, not
     * loaded. Purely a Map editor/rendering concern, no rules-engine consumer, same as floorTexture.
     */
    val wallHatch: Boolean = true,
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

/** doc16's ink-parchment visual family for a map — a single default for now, no customization surface yet (that's art-direction work, not this pass's). */
@Serializable
enum class PaperStyle { Default }

/**
 * doc16: "a map is reused across party sizes (1 to 3) and enemy counts (up to 5) — fixed start
 * coordinates would need a map per configuration." [EncounterSpec] picks how many of each role to
 * fill; `startEncounter` fills these zones in order.
 */
@Serializable
data class SpawnZone(val role: SpawnRole, val tiles: List<GridPos>)

@Serializable
enum class SpawnRole { Party, Enemy, Elite, Boss, Objective }
