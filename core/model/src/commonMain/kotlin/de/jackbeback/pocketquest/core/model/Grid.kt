package de.jackbeback.pocketquest.core.model

import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.Serializable

@Serializable
data class GridPos(val col: Int, val row: Int)

/** Grid distance where diagonal movement costs the same as cardinal — matches the uniform-cost BattleMap. */
fun GridPos.chebyshevDistanceTo(other: GridPos): Int =
    max(abs(col - other.col), abs(row - other.row))

/** True circular distance — used for Shape.Sphere so a "burst radius" reads as round, not square. */
fun GridPos.euclideanDistanceTo(other: GridPos): Double {
    val dx = (col - other.col).toDouble()
    val dy = (row - other.row).toDouble()
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * What the rules need from a tile and nothing else (docs/16-art-direction.md) — walkable,
 * movement cost, blocks line of sight, and hazard are independent axes on purpose: rubble blocks
 * movement but not sight, tall grass the reverse. [hazard] is a data-only marker for now — no
 * on-enter effect is wired to it yet, that's future content-authoring work, not an engine gap.
 */
@Serializable
data class TileType(
    val walkable: Boolean = true,
    val moveCost: Int = 1,
    val blocksLoS: Boolean = false,
    val hazard: Boolean = false,
) {
    companion object {
        val Floor = TileType()
        val Wall = TileType(walkable = false, blocksLoS = true)
        val Difficult = TileType(moveCost = 2)
        val Hazard = TileType(hazard = true)
        /** docs/50-terrain-mutation.md: unwalkable but NOT LoS-blocking — a pit/chasm you can see across but not stand in, expressible with zero new [TileType] axes. A labeled preset, same category as [Wall]/[Difficult]/[Hazard], not a fifth boolean. */
        val Chasm = TileType(walkable = false, hazard = true)
        /**
         * docs/54: mechanically identical to [Chasm] (unwalkable, never blocks LoS) — a distinct
         * named preset purely for authoring INTENT, not a new axis: "hand-place collision for an
         * off-grid `DecorationPlacement` too big/oddly-shaped for the underlying `PropDef`'s own
         * rectangular footprint to cover well" vs. `Chasm`'s "a real environmental pit." `:ui`'s
         * Board never renders hazard/difficult/chasm terrain with any special hatch at all (found
         * while building this — that styling is `:designer`-authoring-only), so this is already
         * exactly as invisible to a player as [Chasm] is; `:designer` gives it its own distinct
         * marker instead of `Chasm`'s hazard hatch, so an author can tell the two apart while
         * placing them.
         */
        val InvisibleWall = TileType(walkable = false)
    }
}

/** One of the four sides of a grid cell — which edge a [WallEdge] sits on. */
@Serializable
enum class Side { North, South, East, West }

fun Side.opposite(): Side = when (this) {
    Side.North -> Side.South
    Side.South -> Side.North
    Side.East -> Side.West
    Side.West -> Side.East
}

private fun Side.step(): GridPos = when (this) {
    Side.North -> GridPos(0, -1)
    Side.South -> GridPos(0, 1)
    Side.East -> GridPos(1, 0)
    Side.West -> GridPos(-1, 0)
}

/**
 * A constructed wall on one side of [pos] — the Map editor's "room divider" style: thin, sits on a
 * tile edge, both neighbouring cells keep their full floor area. Distinct from
 * [TileType.walkable] `= false` (a whole cell consumed — natural rubble/a cave-in/a pillar), which
 * this is layered on top of rather than replacing, so every existing cell-wall map/test keeps
 * working unchanged. A physical wall needs only one canonical entry (from either side's
 * perspective) — [BattleMap.hasWallEdge] checks both directions.
 */
@Serializable
data class WallEdge(val pos: GridPos, val side: Side)

/**
 * docs/48-gates-and-wander-ai.md: a movement-only cousin of [WallEdge] — [edges] (one or more,
 * contiguous, same [Side], for a portcullis wider than one tile) block [BattleMap.canCross] while
 * this gate's id is absent from [GameState][de.jackbeback.pocketquest.core.model.GameState.openGates],
 * but are NEVER added to [BattleMap.wallEdges] and never checked by `hasLineOfSight` — bars, not a
 * solid door, on purpose (see the doc's "why a gate can't just be a WallEdge" section). A
 * `closedSprite` left null renders as plain matching wall texture instead of visible bars — the
 * doc's secret-door amendment, not a separate concept. [requiredTriggers] (the doc's multi-trigger
 * unlock amendment) is a second, independent way to open this same gate: once every id in that set
 * is present in `GameState.firedTriggers`, `Triggers.kt` synthesizes an `OpenGate` effect for it —
 * empty means "only an authored `OpenGate` effect opens this gate," unchanged from the base design.
 */
@Serializable
data class GatePlacement(
    val id: GateId,
    val edges: List<WallEdge>,
    val closedSprite: String? = null,
    val openSprite: String? = null,
    val requiredTriggers: Set<TriggerId> = emptySet(),
)

/**
 * Minimal battle map: just enough for invariant checking (bounds + walkable) plus terrain
 * (docs/17-engine-gaps.md 1.4 — was uniform walkable/blocked with no TileType until now). A tile
 * absent from [terrain] is [TileType.Floor] — most of a map is plain floor, so this stays sparse
 * rather than requiring every cell to be listed.
 */
@Serializable
data class BattleMap(
    val width: Int,
    val height: Int,
    val terrain: Map<GridPos, TileType> = emptyMap(),
    val wallEdges: Set<WallEdge> = emptySet(),
    /** Carried straight from [BattleMapDef] purely for `:ui`'s Board to render — no rules-engine consumer, same as `floorTexture`/`wallStyle` below. */
    val props: List<PropPlacement> = emptyList(),
    val floorTexture: String? = null,
    val wallStyle: WallStyle = WallStyle.Hatch,
    /**
     * Carried from [BattleMapDef.fogOfWar] — unlike the rendering-only flags above, this one IS read
     * by `:core:rules`' visibility computation and the AI's hidden-enemy skip-turn check. Defaults
     * OFF here, opposite of [BattleMapDef]'s own default-on: that default is an authoring choice for
     * real content, this one is the bare engine primitive countless tests construct directly with no
     * fog intent at all (and no `startEncounter` call to populate `revealedTiles` for them) — those
     * must not silently start "fully dark" and skip every enemy's turn just because this field
     * exists now. `toBattleMap()` always passes `fogOfWar` explicitly, so a real authored map's
     * default-on carries through regardless of this constructor default.
     */
    val fogOfWar: Boolean = false,
    /** docs/33-wall-hatch-osr-packing.md: the baked OSR-hatch stroke list, carried straight from [BattleMapDef] — `:ui`'s Board only ever renders it, never generates. */
    val wallHatchOsr: List<HatchLine> = emptyList(),
    /** docs/35-wall-background-punch-through.md: carried straight from [BattleMapDef] — only meaningful when [wallStyle] is [WallStyle.Background]. */
    val backgroundMarginTiles: Int = 4,
    /** docs/36-map-triggers.md: carried straight from [BattleMapDef] — read by the exploration hop loop and the combat `MoveAlong` handler, not just rendered. */
    val triggers: List<TriggerPlacement> = emptyList(),
    /** docs/48-gates-and-wander-ai.md: carried straight from [BattleMapDef] — read by [canCross] (and, transitively, `findPath`/`reachableTiles`), never by `hasLineOfSight`. */
    val gates: List<GatePlacement> = emptyList(),
    /** docs/52-organic-decoration-placement.md: carried straight from [BattleMapDef] — purely rendering data, `:ui`'s Board only ever draws it, never a rules-engine consumer. */
    val decorations: List<DecorationPlacement> = emptyList(),
) {
    fun inBounds(pos: GridPos): Boolean =
        pos.col in 0 until width && pos.row in 0 until height

    fun tileAt(pos: GridPos): TileType = terrain[pos] ?: TileType.Floor

    fun isWalkable(pos: GridPos): Boolean =
        inBounds(pos) && tileAt(pos).walkable

    fun moveCost(pos: GridPos): Int = tileAt(pos).moveCost

    fun blocksLoS(pos: GridPos): Boolean = tileAt(pos).blocksLoS

    /** Every unwalkable tile — the old `blockedTiles` field's role, for rendering/queries that only care about whole-cell walls. */
    val walls: Set<GridPos> get() = terrain.filterValues { !it.walkable }.keys

    fun hasWallEdge(pos: GridPos, side: Side): Boolean {
        if (WallEdge(pos, side) in wallEdges) return true
        return WallEdge(pos + side.step(), side.opposite()) in wallEdges
    }

    /**
     * Movement legality for one step from [from] to an adjacent (incl. diagonal) [to] — purely the
     * edge-wall check; bounds/walkable/occupancy remain the caller's job, same split as before this
     * existed. A diagonal step passes through the corner shared by four cells; a wall attached to
     * that corner from *any* of them blocks it (checking only the two edges touching [from] misses
     * a cell walled on every side but approached diagonally, since none of its walls touch [from]).
     */
    /**
     * [openGates] defaults to empty (every gate treated as closed) — every existing call site/test
     * that never authored a [GatePlacement] sees identical behavior to before this parameter
     * existed, since [edgeOpen] falls back to the plain [hasWallEdge] check whenever no gate covers
     * an edge at all. A real caller with a live [GameState] passes `state.openGates`.
     */
    fun canCross(from: GridPos, to: GridPos, openGates: Set<GateId> = emptySet()): Boolean {
        val dc = to.col - from.col
        val dr = to.row - from.row
        return when {
            dc == 0 && dr == -1 -> edgeOpen(from, Side.North, openGates)
            dc == 0 && dr == 1 -> edgeOpen(from, Side.South, openGates)
            dc == 1 && dr == 0 -> edgeOpen(from, Side.East, openGates)
            dc == -1 && dr == 0 -> edgeOpen(from, Side.West, openGates)
            dc == 1 && dr == -1 -> noCornerWall(from, Side.North, Side.East, to, Side.South, Side.West, openGates)
            dc == 1 && dr == 1 -> noCornerWall(from, Side.South, Side.East, to, Side.North, Side.West, openGates)
            dc == -1 && dr == -1 -> noCornerWall(from, Side.North, Side.West, to, Side.South, Side.East, openGates)
            dc == -1 && dr == 1 -> noCornerWall(from, Side.South, Side.West, to, Side.North, Side.East, openGates)
            else -> true
        }
    }

    private fun noCornerWall(from: GridPos, fromSideA: Side, fromSideB: Side, to: GridPos, toSideA: Side, toSideB: Side, openGates: Set<GateId>): Boolean =
        edgeOpen(from, fromSideA, openGates) && edgeOpen(from, fromSideB, openGates) &&
            edgeOpen(to, toSideA, openGates) && edgeOpen(to, toSideB, openGates)

    /** The [GatePlacement] (if any) whose [GatePlacement.edges] contains the edge on [side] of [pos], checked from either canonical direction — mirrors [hasWallEdge]'s own both-directions check. */
    private fun gateAt(pos: GridPos, side: Side): GatePlacement? {
        val direct = WallEdge(pos, side)
        val mirrored = WallEdge(pos + side.step(), side.opposite())
        return gates.firstOrNull { direct in it.edges || mirrored in it.edges }
    }

    /** Whether the edge on [side] of [pos] can be crossed — a gate's own open/closed state if one covers this edge (a gate edge is never also in [wallEdges], so the two checks never both apply), otherwise the plain [hasWallEdge] check. */
    private fun edgeOpen(pos: GridPos, side: Side, openGates: Set<GateId>): Boolean {
        val gate = gateAt(pos, side)
        return if (gate != null) gate.id in openGates else !hasWallEdge(pos, side)
    }
}

private operator fun GridPos.plus(delta: GridPos): GridPos = GridPos(col + delta.col, row + delta.row)
