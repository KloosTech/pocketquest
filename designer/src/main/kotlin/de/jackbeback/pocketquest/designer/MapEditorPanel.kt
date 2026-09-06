@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GateId
import de.jackbeback.pocketquest.core.model.GatePlacement
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.PropDef
import de.jackbeback.pocketquest.core.model.PropId
import de.jackbeback.pocketquest.core.model.PropLayer
import de.jackbeback.pocketquest.core.model.PropPlacement
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.SpawnZone
import de.jackbeback.pocketquest.core.model.TileType
import de.jackbeback.pocketquest.core.model.TriggerId
import de.jackbeback.pocketquest.core.model.DecorationId
import de.jackbeback.pocketquest.core.model.DecorationPlacement
import de.jackbeback.pocketquest.core.model.TriggerPlacement
import de.jackbeback.pocketquest.core.model.WallEdge
import de.jackbeback.pocketquest.core.model.WallHatchOsrParams
import de.jackbeback.pocketquest.core.model.WallStyle
import de.jackbeback.pocketquest.core.model.opposite
import de.jackbeback.pocketquest.core.rules.content.compressTerrainToRuns
import de.jackbeback.pocketquest.core.rules.content.expandTerrainRuns
import de.jackbeback.pocketquest.ui.BACKGROUND_ASSET_ID
import de.jackbeback.pocketquest.ui.drawBackgroundImage
import de.jackbeback.pocketquest.ui.drawWallHatch
import de.jackbeback.pocketquest.ui.drawWallHatchOsr
import de.jackbeback.pocketquest.ui.generateWallHatchOsr
import kotlin.random.Random
import de.jackbeback.pocketquest.ui.drawWallShadows
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkStepper
import de.jackbeback.pocketquest.ui.ink.InkTextField
import de.jackbeback.pocketquest.ui.ink.InkTooltip
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TILE_PX = 32f
private val GATE_DASH = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
// docs/23-sprite-rendering.md: :ui's composeResources tree is the one location actually bundled
// cross-platform, not a repo-root duplicate — :designer reads that same tree directly.
private const val PARTY_SPRITE_PATH = "ui/src/commonMain/composeResources/files/normalized/characters/hero_a_idle.png"
// Not private — ArchetypePanel.kt's sprite picker reuses this same base path (docs/23).
const val PROPS_DIR = "ui/src/commonMain/composeResources/files/normalized/"
private const val CANVAS_PADDING = 48f

/**
 * What a click currently paints. [Wall] toggles the nearest tile edge rather than a cell; [Prop]
 * places/erases a footprint-sized piece of furniture anchored at the clicked cell. [Trigger]
 * (docs/36-map-triggers.md) is handled specially in `MapEditorPanel`'s own click lambda, not
 * [paintCell] — placing/selecting one needs to open its inline effect editor, which is UI state
 * [paintCell]'s pure `BattleMapDef -> BattleMapDef` shape has no way to touch.
 */
private sealed interface PaintTool {
    data class Terrain(val tile: TileType) : PaintTool
    data class Spawn(val role: SpawnRole?) : PaintTool
    object Wall : PaintTool
    /** docs/51-props-catalog-and-placement.md: picks from `Catalog.props` (real content) rather than a raw manifest asset — `propDef == null` means Erase. Placing/editing is handled specially in `MapEditorPanel`'s own click lambda, not [paintCell] — see [Gate]'s own doc comment for why (click-existing-to-edit needs UI state [paintCell]'s pure shape can't touch). */
    data class Prop(val propDef: PropDef?) : PaintTool
    object Trigger : PaintTool
    /** docs/48-gates-and-wander-ai.md: an edge tool like [Wall], handled specially in `MapEditorPanel`'s own `onToggleWall` lambda — same "needs UI state [paintCell] can't touch" reasoning as [Trigger]. */
    object Gate : PaintTool
    /** docs/52-organic-decoration-placement.md: entirely self-contained inside `MapCanvas`'s own dedicated pointer-gesture block (tap-to-place, tap-existing-to-select, drag-to-move, drag-the-rotate-handle) — never touches [paintCell] at all, unlike every other tool. */
    data class Decoration(val propDef: PropDef?) : PaintTool
}

private fun paintCell(map: BattleMapDef, pos: GridPos, tool: PaintTool): BattleMapDef = when (tool) {
    is PaintTool.Terrain -> {
        val tiles = expandTerrainRuns(map.terrain).toMutableMap()
        if (tool.tile == TileType.Floor) tiles.remove(pos) else tiles[pos] = tool.tile
        // A cell on the map's outer boundary keeps that boundary sealed either way — a Wall tile is
        // itself already solid there, so the border WallEdge on that side would only double the line
        // (see `wallOutlineSegments`' matching fix); a non-Wall tile needs the WallEdge back so the
        // border stays continuous once the Wall tile that used to seal that side is painted over.
        val boundaryEdges = boundarySidesOf(pos, map.width, map.height).map { WallEdge(pos, it) }
        val edges = if (tool.tile == TileType.Wall) map.wallEdges - boundaryEdges.toSet() else (map.wallEdges + boundaryEdges).distinct()
        map.copy(terrain = compressTerrainToRuns(tiles, map.width, map.height), wallEdges = edges)
    }
    is PaintTool.Spawn -> {
        val bySpawn = map.spawns.flatMap { zone -> zone.tiles.map { it to zone.role } }.toMap().toMutableMap()
        if (tool.role == null) bySpawn.remove(pos) else bySpawn[pos] = tool.role
        val zones = bySpawn.entries.groupBy({ it.value }, { it.key }).map { (role, tiles) -> SpawnZone(role, tiles) }
        map.copy(spawns = zones)
    }
    // Handled in MapEditorPanel's own onPaintCell lambda instead — see PaintTool.Prop's doc comment.
    is PaintTool.Prop -> map
    PaintTool.Wall -> map
    // Handled in MapEditorPanel's own onPaintCell lambda instead — see PaintTool.Trigger's doc comment.
    PaintTool.Trigger -> map
    // Handled in MapEditorPanel's own onToggleWall lambda instead — see PaintTool.Gate's doc comment.
    PaintTool.Gate -> map
    // Handled entirely inside MapCanvas's own pointer-gesture block instead — see PaintTool.Decoration's doc comment.
    is PaintTool.Decoration -> map
}

/** Which sides of [pos] face off the [width]x[height] map — empty for any interior cell. */
private fun boundarySidesOf(pos: GridPos, width: Int, height: Int): List<Side> = buildList {
    if (pos.row == 0) add(Side.North)
    if (pos.row == height - 1) add(Side.South)
    if (pos.col == 0) add(Side.West)
    if (pos.col == width - 1) add(Side.East)
}

private fun sideDelta(side: Side): GridPos = when (side) {
    Side.North -> GridPos(0, -1)
    Side.South -> GridPos(0, 1)
    Side.East -> GridPos(1, 0)
    Side.West -> GridPos(-1, 0)
}

/** Mirrors [de.jackbeback.pocketquest.core.model.BattleMap.hasWallEdge]'s canonicalization: a physical wall needs only one entry, checked from either side. */
private fun toggleWallEdge(edges: List<WallEdge>, pos: GridPos, side: Side): List<WallEdge> {
    val direct = WallEdge(pos, side)
    if (direct in edges) return edges - direct
    val d = sideDelta(side)
    val mirrored = WallEdge(GridPos(pos.col + d.col, pos.row + d.row), side.opposite())
    if (mirrored in edges) return edges - mirrored
    return edges + direct
}

/**
 * docs/51-props-catalog-and-placement.md: the [PropPlacement] (if any) whose footprint covers
 * [pos] — [PropDef.footprintTilesW]/[PropDef.footprintTilesH] swapped on a 90°/270°
 * `rotationQuarters`, same rule `toBattleMap()`'s obstruction fold uses. A placement whose id has
 * no matching [PropDef] yet falls back to a 1x1 footprint (its anchor cell only).
 */
private fun propAt(props: List<PropPlacement>, catalog: Catalog, pos: GridPos): PropPlacement? =
    props.firstOrNull { placement ->
        val def = catalog.props[placement.prop]
        val rotated = placement.rotationQuarters % 2 != 0
        val w = if (rotated) (def?.footprintTilesH ?: 1) else (def?.footprintTilesW ?: 1)
        val h = if (rotated) (def?.footprintTilesW ?: 1) else (def?.footprintTilesH ?: 1)
        pos.col in placement.at.col until placement.at.col + w && pos.row in placement.at.row until placement.at.row + h
    }

/** Mirrors [toggleWallEdge]'s own both-directions check — the edge on [side] of [pos], or its mirror from the neighbouring cell's perspective. */
private fun mirroredGateEdge(pos: GridPos, side: Side): WallEdge {
    val d = sideDelta(side)
    return WallEdge(GridPos(pos.col + d.col, pos.row + d.row), side.opposite())
}

/** The [GatePlacement] (if any) already covering the edge on [side] of [pos] — checked from either canonical direction. */
private fun gateAt(gates: List<GatePlacement>, pos: GridPos, side: Side): GatePlacement? {
    val direct = WallEdge(pos, side)
    val mirrored = mirroredGateEdge(pos, side)
    return gates.firstOrNull { direct in it.edges || mirrored in it.edges }
}

/** Two edges on the same [side] are "adjacent" (join into one gate) when they sit on neighbouring cells along the wall's own run direction — a North/South edge runs east-west, an East/West edge runs north-south. */
private fun isAdjacentAlongSide(a: GridPos, b: GridPos, side: Side): Boolean = when (side) {
    Side.North, Side.South -> a.row == b.row && kotlin.math.abs(a.col - b.col) == 1
    Side.East, Side.West -> a.col == b.col && kotlin.math.abs(a.row - b.row) == 1
}

/**
 * docs/48-gates-and-wander-ai.md: clicking an edge with no gate yet either extends an existing
 * adjacent gate (same [Side], neighbouring cell — [isAdjacentAlongSide]) or starts a fresh
 * single-edge [GatePlacement] with a new id. Clicking an edge that already belongs to a gate opens
 * that gate's inline editor instead (`MapEditorPanel`'s own `onToggleWall` lambda) — this function
 * is only ever called for an edge [gateAt] already confirmed has no owner yet.
 */
private fun addGateEdge(gates: List<GatePlacement>, pos: GridPos, side: Side): List<GatePlacement> {
    val edge = WallEdge(pos, side)
    val adjacent = gates.firstOrNull { g -> g.edges.any { e -> e.side == side && isAdjacentAlongSide(e.pos, pos, side) } }
    return if (adjacent != null) {
        gates.map { if (it.id == adjacent.id) it.copy(edges = it.edges + edge) else it }
    } else {
        gates + GatePlacement(id = GateId(java.util.UUID.randomUUID().toString()), edges = listOf(edge))
    }
}

/**
 * docs/34-wall-hatch-osr-configurable-params.md: every [WallHatchOsrParams] knob, editable — none
 * of this applies live (see the Regenerate button just above it in the layout); changing a value
 * here only takes effect the next time that button (or Save) actually re-runs the generator.
 */
@Composable
private fun OsrHatchParamsEditor(params: WallHatchOsrParams, onChange: (WallHatchOsrParams) -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        InkLabel("OSR HATCH PARAMETERS")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("sub-cells/tile", modifier = Modifier.padding(end = 4.dp))
            InkStepper(params.subcellsPerTile, min = 2, onValueChange = { onChange(params.copy(subcellsPerTile = it)) })
            InkLabel("fade distance", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            InkStepper(params.fadeDistanceCells, min = 1, onValueChange = { onChange(params.copy(fadeDistanceCells = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("line length", modifier = Modifier.padding(end = 4.dp))
            InkStepper(params.minLineLengthSubcells, min = 1, onValueChange = { onChange(params.copy(minLineLengthSubcells = it)) })
            InkLabel("to", modifier = Modifier.padding(horizontal = 4.dp))
            InkStepper(params.maxLineLengthSubcells, min = params.minLineLengthSubcells, onValueChange = { onChange(params.copy(maxLineLengthSubcells = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("group size", modifier = Modifier.padding(end = 4.dp))
            InkStepper(params.minGroupSize, min = 1, onValueChange = { onChange(params.copy(minGroupSize = it)) })
            InkLabel("to", modifier = Modifier.padding(horizontal = 4.dp))
            InkStepper(params.maxGroupSize, min = params.minGroupSize, onValueChange = { onChange(params.copy(maxGroupSize = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("angle region", modifier = Modifier.padding(end = 4.dp))
            InkStepper(params.angleRegionSubcells, min = 1, onValueChange = { onChange(params.copy(angleRegionSubcells = it)) })
            InkLabel("angle wobble °", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            FloatField(params.angleJitterDegrees, onChange = { onChange(params.copy(angleJitterDegrees = it.coerceAtLeast(0f))) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("coverage (0-1)", modifier = Modifier.padding(end = 4.dp))
            FloatField(params.targetCoverage, onChange = { onChange(params.copy(targetCoverage = it.coerceIn(0f, 1f))) })
            InkLabel("line width", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            FloatField(params.lineWidthFraction, onChange = { onChange(params.copy(lineWidthFraction = it.coerceAtLeast(0.005f))) })
        }
    }
}

/** A [Float]-backed sibling of [EffectTemplateEditor.kt]'s `IntField` — same "local text buffer, only propagate a value that actually parses" technique. */
@Composable
private fun FloatField(value: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    InkTextField(text, onValueChange = { text = it; it.toFloatOrNull()?.let(onChange) }, modifier = Modifier.width(56.dp))
}

/** Which of a cell's 4 edges a click lands nearest to — the square split into 4 triangles by its diagonals, standard directional-pad technique. [relX]/[relY] are the click position within the cell, each in [0,1). */
private fun nearestSide(relX: Float, relY: Float): Side = when {
    relY < relX && relY < 1f - relX -> Side.North
    relY > relX && relY > 1f - relX -> Side.South
    relX < relY && relX < 1f - relY -> Side.West
    else -> Side.East
}

/** Mechanically accurate, straight off [TileType]'s fields (core/model/Grid.kt) — never invented copy. */
private fun descriptionFor(tile: TileType): String = when (tile) {
    TileType.Wall -> "Wall (whole tile) — blocks movement and line of sight entirely, consumes the whole cell. For a thin room-divider that keeps floor on both sides, use the Wall tool instead."
    TileType.Difficult -> "Difficult terrain — walkable, costs 2 move points to enter instead of 1."
    TileType.InvisibleWall -> "Invisible wall — blocks movement only, never blocks line of sight, renders as plain floor to the player. For hand-placing collision under an off-grid decoration too big/oddly-shaped for a PropDef's own footprint to cover well. Shown here with a faint cross-hatch marker so you can find it again — that marker is authoring-only, never shown in :ui."
    else -> if (tile.hazard) {
        "Hazard — marked as dangerous ground. No on-enter effect is wired up in the engine yet; this only marks the tile for future content."
    } else {
        "Floor — walkable, normal move cost, doesn't block movement or sight."
    }
}

/** SpawnRole carries no mechanics beyond spawn-count matching against an EncounterSpec (doc16) — say so plainly, don't invent rules that don't exist. */
private fun descriptionFor(role: SpawnRole?): String = when (role) {
    null -> "Erase — clears any spawn role from this tile."
    SpawnRole.Party -> "Party spawn — reserved for player characters at encounter start."
    SpawnRole.Enemy -> "Enemy spawn — reserved for regular enemy spawns."
    SpawnRole.Elite -> "Elite spawn — reserved for elite-tier enemy spawns."
    SpawnRole.Boss -> "Boss spawn — reserved for boss spawns."
    SpawnRole.Objective -> "Objective — reserved for a non-combatant objective tile. No engine-side rule is wired to this role yet beyond spawn-count matching."
    SpawnRole.LootCommon -> "Loot spawn (Common) — docs/37: reserved for a common-tier lootable container."
    SpawnRole.LootRare -> "Loot spawn (Rare) — docs/37: reserved for a rare-tier lootable container."
    SpawnRole.LootEpic -> "Loot spawn (Epic) — docs/37: reserved for an epic-tier lootable container."
    SpawnRole.LootLegendary -> "Loot spawn (Legendary) — docs/37: reserved for a legendary-tier lootable container."
}

private const val WALL_DESCRIPTION =
    "Wall (edge) — click near a tile's border to toggle a thin room-divider wall on that edge. Blocks movement and line of sight across the boundary; both cells keep their full floor. Click again on the same edge to remove it."

private fun descriptionFor(asset: ManifestAsset?): String = when (asset) {
    null -> "Erase — removes whatever prop is placed on this tile."
    else -> "${asset.id} — ${asset.tilesW}x${asset.tilesH} tile prop. Click to place, click an occupied tile again to replace, or pick Erase to remove."
}

/**
 * doc16's "Highlight styling": shape/pattern carries the meaning, colour is secondary (parchment +
 * ink + roughly 1-in-12 players with a colour vision deficiency). Wall = solid ink fill, Difficult
 * = sparse 135° hatch, Hazard = 45° hatch in the danger colour — three different angles/fills, not
 * three different flat colours.
 */
/** A sub-region of a floor-texture sheet to stamp into one cell — picked once per cell so neighbouring tiles get visually varied swatches from the same sheet. */
private data class FloorPatch(val sheet: ImageBitmap, val srcOffset: IntOffset, val srcSize: IntSize)

/** The Wall branch here is only ever reached for the small toolbar swatch icon and for a map with `wallStyle == WallStyle.Flat` — the main canvas draws Wall cells via the shared procedural [drawWallHatch]/[drawWallHatchOsr] instead, called separately before this loop runs (see [MapCanvas]). */
private fun DrawScope.drawTerrainCell(tile: TileType, rect: Rect, floorPatch: FloorPatch?) {
    when {
        tile == TileType.Wall -> drawRect(INK, rect.topLeft, rect.size)
        tile == TileType.Difficult -> {
            drawFloor(rect, floorPatch)
            drawHatch(rect, angleDegrees = 135f, spacing = rect.width / 4f, color = INK_FAINT)
        }
        // docs/54: an author-only marker — a faint cross-hatch (both diagonals, distinct from
        // Difficult's single 135° and Hazard/Chasm's single 45° danger-coloured one) — :ui never
        // renders this at all, it draws plain floor for every non-Wall tile regardless of type.
        tile == TileType.InvisibleWall -> {
            drawFloor(rect, floorPatch)
            drawHatch(rect, angleDegrees = 45f, spacing = rect.width / 3f, color = INK_FAINT)
            drawHatch(rect, angleDegrees = 135f, spacing = rect.width / 3f, color = INK_FAINT)
        }
        tile.hazard -> {
            drawFloor(rect, floorPatch)
            drawHatch(rect, angleDegrees = 45f, spacing = rect.width / 4f, color = DANGER)
        }
        else -> drawFloor(rect, floorPatch)
    }
}

private fun DrawScope.drawFloor(rect: Rect, floorPatch: FloorPatch?) {
    if (floorPatch != null) drawPatch(rect, floorPatch) else drawRect(PAPER, rect.topLeft, rect.size)
}

private fun DrawScope.drawPatch(rect: Rect, patch: FloorPatch) {
    drawImage(
        patch.sheet,
        srcOffset = patch.srcOffset,
        srcSize = patch.srcSize,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
    )
}

private fun DrawScope.drawHatch(rect: Rect, angleDegrees: Float, spacing: Float, color: Color) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        rotate(angleDegrees, pivot = rect.center) {
            val reach = rect.width + rect.height
            var x = rect.center.x - reach
            while (x < rect.center.x + reach) {
                drawLine(color, Offset(x, rect.center.y - reach), Offset(x, rect.center.y + reach), strokeWidth = 1.5f)
                x += spacing
            }
        }
    }
}

/** The screen-space segment a [WallEdge] occupies, given cell (col,row) spans [col*TILE_PX .. (col+1)*TILE_PX] x [row*TILE_PX .. (row+1)*TILE_PX]. */
private fun wallSegment(edge: WallEdge): Pair<Offset, Offset> {
    val x0 = edge.pos.col * TILE_PX
    val y0 = edge.pos.row * TILE_PX
    return when (edge.side) {
        Side.North -> Offset(x0, y0) to Offset(x0 + TILE_PX, y0)
        Side.South -> Offset(x0, y0 + TILE_PX) to Offset(x0 + TILE_PX, y0 + TILE_PX)
        Side.East -> Offset(x0 + TILE_PX, y0) to Offset(x0 + TILE_PX, y0 + TILE_PX)
        Side.West -> Offset(x0, y0) to Offset(x0, y0 + TILE_PX)
    }
}

/** Same band-on-the-edge technique `:ui`'s `drawGateSprite` uses — a screen-space line segment stretched into an image band, oriented by the edge's own [side]. */
private fun DrawScope.drawGateEdgeSprite(bitmap: ImageBitmap, screenA: Offset, screenB: Offset, side: Side, zoom: Float) {
    val length = (screenB - screenA).getDistance()
    val thickness = TILE_PX * zoom * 0.3f
    val center = (screenA + screenB) / 2f
    val horizontal = side == Side.North || side == Side.South
    val dstSize = if (horizontal) IntSize(length.roundToInt(), thickness.roundToInt()) else IntSize(thickness.roundToInt(), length.roundToInt())
    val dstOffset = IntOffset((center.x - dstSize.width / 2f).roundToInt(), (center.y - dstSize.height / 2f).roundToInt())
    drawImage(bitmap, dstOffset = dstOffset, dstSize = dstSize)
}

/** A fresh map starts fully enclosed — a `WallEdge` running the whole way around the outside, so the
 * hatch/background rendering has a boundary line to meet from the very first tile painted, instead of
 * floor bleeding straight into the margin until the author remembers to wall it off by hand. */
private fun borderWallEdges(width: Int, height: Int): List<WallEdge> {
    val edges = mutableListOf<WallEdge>()
    for (col in 0 until width) {
        edges += WallEdge(GridPos(col, 0), Side.North)
        edges += WallEdge(GridPos(col, height - 1), Side.South)
    }
    for (row in 0 until height) {
        edges += WallEdge(GridPos(0, row), Side.West)
        edges += WallEdge(GridPos(width - 1, row), Side.East)
    }
    return edges
}

private fun GridPos.neighbor(side: Side): GridPos = when (side) {
    Side.North -> copy(row = row - 1)
    Side.South -> copy(row = row + 1)
    Side.East -> copy(col = col + 1)
    Side.West -> copy(col = col - 1)
}

/**
 * Derived, not authored: every side of a whole-tile [TileType.Wall] cell that borders a non-Wall
 * cell *within the map* gets a solid outline — this is what makes a painted Wall mass in the
 * reference screenshot read as one solid building with a clean border, without the author separately
 * placing a `WallEdge` (the "Wall (edge)" tool's thin room-divider) around every hatch region by
 * hand. Manually placed `WallEdge`s (interior thin dividers between two Floor cells) are unrelated
 * and still drawn separately from `map.wallEdges` — this never reads or writes that list, except
 * indirectly: a side facing off the map is never outlined here, since every new map is already
 * seeded with a `WallEdge` border (see "+ Create" below) that draws that boundary line on its own —
 * outlining it a second time here doubled the line and broke the seamless hatch-to-margin transition
 * at the map's edge.
 */
private fun wallOutlineSegments(tiles: Map<GridPos, TileType>, width: Int, height: Int): List<Pair<Offset, Offset>> {
    val segments = mutableListOf<Pair<Offset, Offset>>()
    for (col in 0 until width) {
        for (row in 0 until height) {
            val pos = GridPos(col, row)
            if ((tiles[pos] ?: TileType.Floor) != TileType.Wall) continue
            for (side in Side.entries) {
                val neighbor = pos.neighbor(side)
                if (neighbor.col !in 0 until width || neighbor.row !in 0 until height) continue
                if ((tiles[neighbor] ?: TileType.Floor) != TileType.Wall) {
                    segments += wallSegment(WallEdge(pos, side))
                }
            }
        }
    }
    return segments
}

/**
 * doc16: enemies are ink tokens in the map's own style — "circular, black on parchment", an inner
 * ring for elite and two for boss — not sprites (there are no monster sprites in the pack). Party
 * is the one exception (doc19): it gets the real character sprite, since the party is the only
 * thing that's "really there".
 */
private fun DrawScope.drawSpawnToken(role: SpawnRole, center: Offset, radius: Float, partySprite: ImageBitmap?) {
    when (role) {
        SpawnRole.Party -> if (partySprite != null) {
            // doc16: character sheets are 4-frame walk cycles stacked vertically (64x256 = four 64x64
            // frames) — the whole sheet squashed into one square was the "weird pattern" bug. Crop the
            // first (idle) frame only: a square of side = sheet width.
            val frame = partySprite.width
            val d = (radius * 2.2f).roundToInt()
            drawImage(
                partySprite,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(frame, frame),
                dstOffset = IntOffset((center.x - d / 2f).roundToInt(), (center.y - d / 2f).roundToInt()),
                dstSize = IntSize(d, d),
            )
        } else {
            drawCircle(Color(0xFF2196F3), radius, center, style = Stroke(2f))
        }
        SpawnRole.Enemy -> drawCircle(INK, radius, center, style = Stroke(2f))
        SpawnRole.Elite -> {
            drawCircle(INK, radius, center, style = Stroke(2f))
            drawCircle(INK, radius * 0.55f, center, style = Stroke(1.5f))
        }
        SpawnRole.Boss -> {
            drawCircle(INK, radius, center, style = Stroke(2.5f))
            drawCircle(INK, radius * 0.68f, center, style = Stroke(1.5f))
            drawCircle(INK, radius * 0.36f, center, style = Stroke(1.5f))
        }
        SpawnRole.Objective -> {
            val path = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y)
                lineTo(center.x, center.y + radius)
                lineTo(center.x - radius, center.y)
                close()
            }
            drawPath(path, color = Color(0xFFF9A825), style = Stroke(2f))
        }
        // docs/37-lootable-containers.md: a filled square, not a circle/diamond — a distinct shape
        // per doc16's own "shape/pattern carries the meaning, colour is secondary" rule — color-coded
        // by rarity only as a secondary cue.
        SpawnRole.LootCommon -> drawLootSquare(center, radius, Color(0xFF9E9E9E))
        SpawnRole.LootRare -> drawLootSquare(center, radius, Color(0xFF1976D2))
        SpawnRole.LootEpic -> drawLootSquare(center, radius, Color(0xFF7B1FA2))
        SpawnRole.LootLegendary -> drawLootSquare(center, radius, Color(0xFFFF8F00))
    }
}

private fun DrawScope.drawLootSquare(center: Offset, radius: Float, color: Color) {
    val side = radius * 1.3f
    val topLeft = Offset(center.x - side / 2f, center.y - side / 2f)
    drawRect(color.copy(alpha = 0.25f), topLeft, Size(side, side))
    drawRect(color, topLeft, Size(side, side), style = Stroke(2f))
}

/** doc16's Map editor: terrain, edge walls, spawn zones, floor texture, and prop placement. */
@Composable
fun MapEditorPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf(catalog.maps.keys.firstOrNull()) }
    var tool by remember { mutableStateOf<PaintTool>(PaintTool.Terrain(TileType.Wall)) }
    var newWidth by remember { mutableStateOf("10") }
    var newHeight by remember { mutableStateOf("10") }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("MAPS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.maps.values.toList()) { map ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = map.id }
                            .background(if (map.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText("${map.name.ifBlank { map.id.raw }} (${map.width}x${map.height})", style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkLabel("NEW MAP", modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkTextField(newWidth, onValueChange = { newWidth = it }, modifier = Modifier.width(50.dp))
                BasicText(" x ", style = TextStyle(color = INK, fontSize = 13.sp))
                InkTextField(newHeight, onValueChange = { newHeight = it }, modifier = Modifier.width(50.dp))
            }
            InkButton(
                "+ Create",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                onClick = {
                    val w = newWidth.toIntOrNull()?.coerceIn(1, 60) ?: return@InkButton
                    val h = newHeight.toIntOrNull()?.coerceIn(1, 60) ?: return@InkButton
                    var n = catalog.maps.size + 1
                    while (MapId("map$n") in catalog.maps) n++
                    val id = MapId("map$n")
                    onCatalogChange(catalog.copy(maps = catalog.maps + (id to BattleMapDef(id = id, name = "New Map $n", width = w, height = h, wallEdges = borderWallEdges(w, h)))))
                    selectedId = id
                },
            )
        }

        val map = selectedId?.let { catalog.maps[it] }
        if (map == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No map selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
            return
        }
        fun updateMap(update: (BattleMapDef) -> BattleMapDef) {
            onCatalogChange(catalog.copy(maps = catalog.maps + (map.id to update(map))))
        }
        // docs/36-map-triggers.md: which trigger's inline effect editor is open, if any — keyed on
        // map.id so switching maps doesn't leave a stale popup pointing at another map's trigger.
        var editingTriggerId by remember(map.id) { mutableStateOf<TriggerId?>(null) }
        // docs/48-gates-and-wander-ai.md: same shape, for the Gate tool's inline editor.
        var editingGateId by remember(map.id) { mutableStateOf<GateId?>(null) }
        // docs/51-props-catalog-and-placement.md: same shape, keyed on a placement's anchor `at`
        // (stable for the life of one placement, unlike an index into `map.props`).
        var editingPropAt by remember(map.id) { mutableStateOf<GridPos?>(null) }
        // docs/52-organic-decoration-placement.md: which decoration is selected, if any — set by
        // MapCanvas's own gesture block (tap-to-select, or right after placing a fresh one).
        var selectedDecorationId by remember(map.id) { mutableStateOf<DecorationId?>(null) }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // docs: MapCanvas fills this whole pane (not just the space left over below the
            // palette) so its click hit-box matches where it's actually drawn — Compose doesn't
            // clip a Canvas's draw to its layout bounds, so a panned/zoomed map already bled
            // upward past a smaller box into the palette rows' area; that area looked clickable
            // but wasn't. Palette now overlays on top instead of pushing the canvas down.
            MapCanvas(
                map = map,
                tool = tool,
                onPaintCell = { pos ->
                    if (tool == PaintTool.Trigger) {
                        // docs/36-map-triggers.md: click an existing trigger cell to open its
                        // editor; click an empty cell to place a fresh one (id generated once,
                        // here — never author-typed) and open it immediately.
                        val existing = map.triggers.firstOrNull { it.at == pos }
                        if (existing != null) {
                            editingTriggerId = existing.id
                        } else {
                            val fresh = TriggerPlacement(id = TriggerId(java.util.UUID.randomUUID().toString()), at = pos)
                            updateMap { it.copy(triggers = it.triggers + fresh) }
                            editingTriggerId = fresh.id
                        }
                    } else if (tool.let { it is PaintTool.Prop }) {
                        // docs/51-props-catalog-and-placement.md: Erase mode always removes whatever
                        // covers this cell; otherwise an already-placed footprint opens its editor,
                        // an empty cell places a fresh instance of the tool's selected PropDef.
                        val propTool = tool as PaintTool.Prop
                        val existing = propAt(map.props, catalog, pos)
                        val def = propTool.propDef
                        if (def == null) {
                            if (existing != null) updateMap { it.copy(props = it.props.filterNot { p -> p.at == existing.at }) }
                        } else if (existing != null) {
                            editingPropAt = existing.at
                        } else {
                            updateMap { it.copy(props = it.props + PropPlacement(def.id, pos, PropLayer.Object)) }
                        }
                    } else {
                        updateMap { paintCell(it, pos, tool) }
                    }
                },
                onToggleWall = { pos, side ->
                    if (tool == PaintTool.Gate) {
                        // docs/48-gates-and-wander-ai.md: an edge already owned by a gate opens
                        // that gate's editor; an unclaimed edge extends an adjacent gate or starts
                        // a fresh one — mirrors PaintTool.Trigger's "click existing = edit" pattern.
                        val existing = gateAt(map.gates, pos, side)
                        if (existing != null) {
                            editingGateId = existing.id
                        } else {
                            updateMap { it.copy(gates = addGateEdge(it.gates, pos, side)) }
                        }
                    } else {
                        updateMap { it.copy(wallEdges = toggleWallEdge(it.wallEdges, pos, side)) }
                    }
                },
                editingTriggerId = editingTriggerId,
                editingGateId = editingGateId,
                selectedDecorationId = selectedDecorationId,
                onSelectDecoration = { selectedDecorationId = it },
                onDecorationsChange = { updated -> updateMap { it.copy(decorations = updated) } },
            )
            val editing = map.triggers.firstOrNull { it.id == editingTriggerId }
            if (editing != null) {
                TriggerEditorPanel(
                    trigger = editing,
                    catalog = catalog,
                    gateIds = map.gates.map { it.id },
                    onChange = { updated -> updateMap { m -> m.copy(triggers = m.triggers.map { if (it.id == updated.id) updated else it }) } },
                    onDelete = {
                        updateMap { m -> m.copy(triggers = m.triggers.filterNot { it.id == editing.id }) }
                        editingTriggerId = null
                    },
                    onClose = { editingTriggerId = null },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            val editingGate = map.gates.firstOrNull { it.id == editingGateId }
            if (editingGate != null) {
                GateEditorPanel(
                    gate = editingGate,
                    triggerIds = map.triggers.map { it.id },
                    onChange = { updated -> updateMap { m -> m.copy(gates = m.gates.map { if (it.id == updated.id) updated else it }) } },
                    onDelete = {
                        updateMap { m -> m.copy(gates = m.gates.filterNot { it.id == editingGate.id }) }
                        editingGateId = null
                    },
                    onClose = { editingGateId = null },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            val editingProp = editingPropAt?.let { at -> map.props.firstOrNull { it.at == at } }
            if (editingProp != null) {
                PropInstanceEditorPanel(
                    placement = editingProp,
                    propDef = catalog.props[editingProp.prop],
                    onChange = { updated -> updateMap { m -> m.copy(props = m.props.map { if (it.at == editingProp.at) updated else it }) } },
                    onDelete = {
                        updateMap { m -> m.copy(props = m.props.filterNot { it.at == editingProp.at }) }
                        editingPropAt = null
                    },
                    onClose = { editingPropAt = null },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            val selectedDecoration = map.decorations.firstOrNull { it.id == selectedDecorationId }
            if (selectedDecoration != null) {
                DecorationEditorPanel(
                    placement = selectedDecoration,
                    propDef = catalog.props[selectedDecoration.prop],
                    onChange = { updated -> updateMap { m -> m.copy(decorations = m.decorations.map { if (it.id == updated.id) updated else it }) } },
                    onDelete = {
                        updateMap { m -> m.copy(decorations = m.decorations.filterNot { it.id == selectedDecoration.id }) }
                        selectedDecorationId = null
                    },
                    onClose = { selectedDecorationId = null },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkLabel("NAME", modifier = Modifier.padding(end = 8.dp))
                    InkTextField(map.name, onValueChange = { updateMap { m -> m.copy(name = it) } }, modifier = Modifier.width(220.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    InkLabel("FLOOR TEXTURE")
                    InkSelect(
                        selected = map.floorTexture,
                        options = listOf(null) + AssetManifest.floorTextures.map { it.id },
                        label = { it ?: "Plain parchment" },
                        onSelect = { id -> updateMap { it.copy(floorTexture = id) } },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    InkButton(
                        "Remove Map",
                        modifier = Modifier.padding(start = 16.dp),
                        onClick = {
                            onCatalogChange(catalog.copy(maps = catalog.maps - map.id))
                            selectedId = catalog.maps.keys.firstOrNull { it != map.id }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    InkLabel("WALL TEXTURE")
                    // docs/32-wall-hatch-osr-style.md: Hatch draws live (drawWallHatch, shared with
                    // :ui) — no sprite/manifest id to pick. BattleMapDef.wallStyle defaults to Hatch,
                    // matching floorTexture's "auto-applied, overridable" precedent. Osr is different
                    // (docs/33): it renders pre-baked geometry, not a live computation — see the
                    // Regenerate button below.
                    InkSelect(
                        selected = map.wallStyle,
                        options = WallStyle.entries,
                        label = {
                            when (it) {
                                WallStyle.Flat -> "Plain (flat fill)"
                                WallStyle.Hatch -> "Hatched"
                                WallStyle.Osr -> "Hatched (OSR)"
                                WallStyle.Background -> "Background image"
                            }
                        },
                        onSelect = { style -> updateMap { it.copy(wallStyle = style) } },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    if (map.wallStyle == WallStyle.Osr) {
                        // docs/33-wall-hatch-osr-packing.md: painting walls never auto-regenerates —
                        // this button (or Save, for a map that has no bake yet at all) is the only
                        // trigger. A fresh random seed each click is the whole point: "don't like this
                        // roll, try another."
                        InkButton(
                            "Regenerate Hatch",
                            modifier = Modifier.padding(start = 8.dp),
                            onClick = {
                                val tiles = expandTerrainRuns(map.terrain)
                                val seed = Random.nextLong()
                                val lines = generateWallHatchOsr(
                                    isWall = { (tiles[it] ?: TileType.Floor) == TileType.Wall },
                                    cols = 0 until map.width,
                                    rows = 0 until map.height,
                                    seed = seed,
                                    params = map.wallHatchOsrParams,
                                )
                                updateMap { it.copy(wallHatchOsr = lines, wallHatchOsrSeed = seed) }
                            },
                        )
                    }
                    if (map.wallStyle == WallStyle.Background) {
                        // docs/35-wall-background-punch-through.md: how far past the map's own edge
                        // the tiled background still extends before stopping — resolved: bounded to
                        // the map + a margin, not the whole pannable viewport.
                        InkLabel("margin (tiles)", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
                        InkStepper(map.backgroundMarginTiles, min = 0, onValueChange = { updateMap { m -> m.copy(backgroundMarginTiles = it) } })
                    }
                }
                if (map.wallStyle == WallStyle.Osr) {
                    OsrHatchParamsEditor(
                        params = map.wallHatchOsrParams,
                        onChange = { params -> updateMap { it.copy(wallHatchOsrParams = params) } },
                    )
                }
                InkLabel("TERRAIN", modifier = Modifier.padding(top = 8.dp))
                Row {
                    listOf(TileType.Floor, TileType.Wall, TileType.Difficult, TileType.Hazard, TileType.Chasm, TileType.InvisibleWall).forEach { t ->
                        TerrainToolSwatch(t, selected = (tool as? PaintTool.Terrain)?.tile == t, onClick = { tool = PaintTool.Terrain(t) })
                    }
                    WallToolSwatch(selected = tool == PaintTool.Wall, onClick = { tool = PaintTool.Wall })
                    GateToolSwatch(selected = tool == PaintTool.Gate, onClick = { tool = PaintTool.Gate })
                }
                InkLabel("SPAWN ZONE", modifier = Modifier.padding(top = 8.dp))
                Row {
                    SpawnToolSwatch(null, selected = tool.let { it is PaintTool.Spawn && it.role == null }, onClick = { tool = PaintTool.Spawn(null) })
                    SpawnRole.entries.forEach { role ->
                        SpawnToolSwatch(role, selected = (tool as? PaintTool.Spawn)?.role == role, onClick = { tool = PaintTool.Spawn(role) })
                    }
                }
                InkLabel("TRIGGERS", modifier = Modifier.padding(top = 8.dp))
                Row {
                    TriggerToolSwatch(selected = tool == PaintTool.Trigger, onClick = { tool = PaintTool.Trigger })
                }
                // docs/51-props-catalog-and-placement.md: picks from Catalog.props (real content,
                // editable in the Props tab) instead of the raw manifest list directly — the sprite
                // thumbnail still resolves through AssetManifest.prop(propDef.id.raw), unchanged.
                InkLabel("PROPS", modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val currentDef = (tool as? PaintTool.Prop)?.propDef
                    val propOptions = remember(catalog.props) { catalog.props.values.sortedBy { it.id.raw } }
                    fun spriteOf(def: PropDef): ManifestAsset? = AssetManifest.prop(def.id.raw)
                    InkSelect(
                        selected = currentDef,
                        options = listOf<PropDef?>(null) + propOptions,
                        label = { it?.let { d -> d.name.ifBlank { d.id.raw } } ?: "Erase" },
                        onSelect = { def -> tool = PaintTool.Prop(def) },
                        modifier = Modifier.width(200.dp),
                        itemContent = { def ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val asset = def?.let { spriteOf(it) }
                                val bmp = asset?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
                                if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(end = 6.dp))
                                BasicText(
                                    def?.let { d -> d.name.ifBlank { d.id.raw } } ?: "Erase",
                                    style = TextStyle(color = INK, fontSize = 13.sp),
                                )
                            }
                        },
                    )
                    if (currentDef != null) {
                        val asset = spriteOf(currentDef)
                        val bmp = asset?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
                        if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                // docs/52-organic-decoration-placement.md: same PropDef pool as PROPS above, but
                // placed free-floating (no grid snap) via MapCanvas's own dedicated gesture block —
                // tap empty space to place, tap/drag an existing one to select/move/rotate.
                InkLabel("DECORATIONS (free placement, no grid snap)", modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val decoOptions = remember(catalog.props) { catalog.props.values.sortedBy { it.id.raw } }
                    val currentDecoDef = (tool as? PaintTool.Decoration)?.propDef ?: decoOptions.firstOrNull()
                    fun spriteOfDeco(def: PropDef): ManifestAsset? = AssetManifest.prop(def.id.raw)
                    InkSelect(
                        selected = currentDecoDef ?: return@Row,
                        options = decoOptions,
                        label = { it.name.ifBlank { it.id.raw } },
                        onSelect = { def -> tool = PaintTool.Decoration(def) },
                        modifier = Modifier.width(200.dp),
                        itemContent = { def ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val bmp = spriteOfDeco(def)?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
                                if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(end = 6.dp))
                                BasicText(def.name.ifBlank { def.id.raw }, style = TextStyle(color = INK, fontSize = 13.sp))
                            }
                        },
                    )
                    InkButton(
                        "Decoration tool",
                        modifier = Modifier.padding(start = 8.dp),
                        emphasized = tool is PaintTool.Decoration,
                        onClick = { tool = PaintTool.Decoration((tool as? PaintTool.Decoration)?.propDef ?: decoOptions.firstOrNull()) },
                    )
                    // A click on the canvas only ever selects/edits an existing decoration now
                    // (see MapCanvas's own gesture block) — this is the one place a NEW one gets
                    // created, dropped at the map's center already selected, ready to drag wherever
                    // it actually belongs.
                    InkButton(
                        "+ Add",
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = {
                            val def = currentDecoDef ?: return@InkButton
                            val fresh = DecorationPlacement(id = DecorationId(java.util.UUID.randomUUID().toString()), prop = def.id, x = map.width / 2f, y = map.height / 2f)
                            updateMap { it.copy(decorations = it.decorations + fresh) }
                            selectedDecorationId = fresh.id
                            tool = PaintTool.Decoration(def)
                        },
                    )
                }
            }
        }
    }
}

/**
 * docs/36-map-triggers.md: reuses [EffectTemplateListEditor] verbatim — the exact composable
 * `ActionDef.effects`/`StatusDef.onTurnStart` already use, since a trigger's effect list is typed
 * exactly `List<EffectTemplate>`, nothing trigger-specific about it. Anchored over the canvas rather
 * than a separate side panel — the trigger cell it's editing is still visible (highlighted DANGER
 * on the canvas) right behind it.
 */
@Composable
private fun TriggerEditorPanel(
    trigger: TriggerPlacement,
    catalog: Catalog,
    gateIds: List<GateId>,
    onChange: (TriggerPlacement) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp).background(PAPER_SHEET).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("TRIGGER at (${trigger.at.col}, ${trigger.at.row})", modifier = Modifier.weight(1f))
            InkButton("Delete", onClick = onDelete)
            InkButton("Close", modifier = Modifier.padding(start = 4.dp), onClick = onClose)
        }
        EffectTemplateListEditor(
            trigger.effects,
            catalog,
            onChange = { effects -> onChange(trigger.copy(effects = effects)) },
            modifier = Modifier.padding(top = 8.dp),
            gateIds = gateIds,
        )
    }
}

/**
 * docs/48-gates-and-wander-ai.md: sprite pickers (leaving [GatePlacement.closedSprite] unset is the
 * secret-door amendment — renders as plain wall instead of visible bars) plus a [requiredTriggers]
 * multi-select (the multi-trigger unlock amendment) — no other author-typed fields, a gate's
 * geometry is painted on the canvas, not edited here.
 */
@Composable
private fun GateEditorPanel(
    gate: GatePlacement,
    triggerIds: List<TriggerId>,
    onChange: (GatePlacement) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp).background(PAPER_SHEET).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("GATE (${gate.edges.size} edge${if (gate.edges.size == 1) "" else "s"})", modifier = Modifier.weight(1f))
            InkButton("Delete", onClick = onDelete)
            InkButton("Close", modifier = Modifier.padding(start = 4.dp), onClick = onClose)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            InkLabel("CLOSED", modifier = Modifier.padding(end = 4.dp))
            SpritePicker(gate.closedSprite, onSelect = { onChange(gate.copy(closedSprite = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("OPEN", modifier = Modifier.padding(end = 4.dp))
            SpritePicker(gate.openSprite, onSelect = { onChange(gate.copy(openSprite = it)) })
        }
        InkLabel("REQUIRED TRIGGERS (opens once ALL fire, alongside any authored OpenGate effect)", modifier = Modifier.padding(top = 8.dp))
        if (triggerIds.isEmpty()) {
            InkLabel("no triggers on this map yet")
        } else {
            Row {
                triggerIds.forEachIndexed { i, id ->
                    val selected = id in gate.requiredTriggers
                    InkButton(
                        "Trigger ${i + 1}",
                        modifier = Modifier.padding(end = 4.dp),
                        emphasized = selected,
                        onClick = {
                            val updated = if (selected) gate.requiredTriggers - id else gate.requiredTriggers + id
                            onChange(gate.copy(requiredTriggers = updated))
                        },
                    )
                }
            }
        }
    }
}

/** Same [AssetManifest.placeableProps] picker [PaintTool.Prop] uses — `null` means "no sprite" (plain wall texture, the secret-door look). */
@Composable
private fun SpritePicker(selected: String?, onSelect: (String?) -> Unit) {
    InkSelect(
        selected = selected,
        options = listOf<String?>(null) + AssetManifest.placeableProps.map { it.id },
        label = { it ?: "(none — renders as plain wall)" },
        onSelect = onSelect,
    )
}

private val TINT_SWATCHES: List<Pair<String, Int?>> = listOf(
    "None" to null,
    "Red" to 0xFFE57373.toInt(),
    "Green" to 0xFF81C784.toInt(),
    "Blue" to 0xFF64B5F6.toInt(),
    "Yellow" to 0xFFFFF176.toInt(),
)

/**
 * docs/51-props-catalog-and-placement.md: the click-existing-to-edit surface for one already-placed
 * [PropPlacement] — rotate (90° at a time, the only granularity the model supports), flip, layer,
 * and a small tint swatch set (not a full color picker — five named presets is enough authoring
 * control for "give this copy a different hue" without building a color-wheel widget). [propDef] is
 * display-only here (name/footprint) — its own fields are edited in the Props tab, not here.
 */
@Composable
private fun PropInstanceEditorPanel(
    placement: PropPlacement,
    propDef: PropDef?,
    onChange: (PropPlacement) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp).background(PAPER_SHEET).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("PROP: ${propDef?.name?.ifBlank { placement.prop.raw } ?: placement.prop.raw}", modifier = Modifier.weight(1f))
            InkButton("Delete", onClick = onDelete)
            InkButton("Close", modifier = Modifier.padding(start = 4.dp), onClick = onClose)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            InkLabel("ROTATE", modifier = Modifier.padding(end = 8.dp))
            InkButton("${placement.rotationQuarters * 90}°", onClick = { onChange(placement.copy(rotationQuarters = (placement.rotationQuarters + 1) % 4)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            InkLabel("FLIP", modifier = Modifier.padding(end = 8.dp))
            InkButton(if (placement.flipX) "Flipped" else "Not flipped", emphasized = placement.flipX, onClick = { onChange(placement.copy(flipX = !placement.flipX)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            InkLabel("LAYER", modifier = Modifier.padding(end = 8.dp))
            InkSelect(placement.layer, PropLayer.entries, { it.name }, { onChange(placement.copy(layer = it)) })
        }
        InkLabel("TINT", modifier = Modifier.padding(top = 8.dp))
        Row {
            TINT_SWATCHES.forEach { (label, value) ->
                InkButton(label, modifier = Modifier.padding(end = 4.dp), emphasized = placement.tint == value, onClick = { onChange(placement.copy(tint = value)) })
            }
        }
    }
}

/**
 * docs/52-organic-decoration-placement.md: everything EXCEPT position/rotation, which are set by
 * dragging the decoration itself / its rotate handle on the canvas, not fields here. Same
 * scale/tint/flip/delete shape [PropInstanceEditorPanel] has, minus layer (kept default) and
 * rotate-by-90 (this one rotates freely, by drag, not a button).
 */
@Composable
private fun DecorationEditorPanel(
    placement: DecorationPlacement,
    propDef: PropDef?,
    onChange: (DecorationPlacement) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 280.dp, max = 340.dp).background(PAPER_SHEET).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("DECORATION: ${propDef?.name?.ifBlank { placement.prop.raw } ?: placement.prop.raw}", modifier = Modifier.weight(1f))
            InkButton("Delete", onClick = onDelete)
            InkButton("Close", modifier = Modifier.padding(start = 4.dp), onClick = onClose)
        }
        InkLabel("Drag it to move, drag the small handle to rotate.", modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            InkLabel("SCALE", modifier = Modifier.padding(end = 8.dp))
            InkStepper((placement.scale * 100).roundToInt(), min = 25, onValueChange = { onChange(placement.copy(scale = it.coerceAtLeast(25) / 100f)) })
            InkLabel("or type", modifier = Modifier.padding(start = 8.dp, end = 4.dp))
            FloatField(placement.scale) { onChange(placement.copy(scale = it.coerceAtLeast(0.05f))) }
            InkLabel("x", modifier = Modifier.padding(start = 2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            InkLabel("FLIP", modifier = Modifier.padding(end = 8.dp))
            InkButton(if (placement.flipX) "Flipped" else "Not flipped", emphasized = placement.flipX, onClick = { onChange(placement.copy(flipX = !placement.flipX)) })
        }
        InkLabel("TINT", modifier = Modifier.padding(top = 8.dp))
        Row {
            TINT_SWATCHES.forEach { (label, value) ->
                InkButton(label, modifier = Modifier.padding(end = 4.dp), emphasized = placement.tint == value, onClick = { onChange(placement.copy(tint = value)) })
            }
        }
    }
}

// Not private — ArchetypePanel.kt's sprite picker reuses this same thumbnail composable (docs/23).
@Composable
fun PropThumbnail(bmp: ImageBitmap, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(28.dp).background(PAPER)) {
        drawImage(bmp, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

@Composable
private fun TerrainToolSwatch(tile: TileType, selected: Boolean, onClick: () -> Unit) {
    InkTooltip(descriptionFor(tile)) {
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(28.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTerrainCell(tile, Rect(Offset.Zero, size), floorPatch = null)
                drawRect(INK, Offset.Zero, size, style = Stroke(if (selected) 2.5f else 1f))
            }
        }
    }
}

@Composable
private fun WallToolSwatch(selected: Boolean, onClick: () -> Unit) {
    InkTooltip(WALL_DESCRIPTION) {
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(28.dp)
                .background(PAPER)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(INK, Offset(size.width * 0.2f, 0f), Offset(size.width * 0.2f, size.height), strokeWidth = 3f)
                drawRect(INK, Offset.Zero, size, style = Stroke(if (selected) 2.5f else 1f))
            }
        }
    }
}

private const val GATE_DESCRIPTION =
    "Gate — click near a tile's border to place/extend a portcullis edge (blocks movement, never blocks line of sight — bars, not a solid door). Click an already-placed gate edge to open its editor (sprites, required triggers, delete)."

@Composable
private fun GateToolSwatch(selected: Boolean, onClick: () -> Unit) {
    InkTooltip(GATE_DESCRIPTION) {
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(28.dp)
                .background(PAPER)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    INK,
                    Offset(size.width * 0.2f, 0f),
                    Offset(size.width * 0.2f, size.height),
                    strokeWidth = 3f,
                    pathEffect = GATE_DASH,
                )
                drawRect(INK, Offset.Zero, size, style = Stroke(if (selected) 2.5f else 1f))
            }
        }
    }
}

@Composable
private fun SpawnToolSwatch(role: SpawnRole?, selected: Boolean, onClick: () -> Unit) {
    val partySprite = remember { SpriteLoader.load(PARTY_SPRITE_PATH) }
    InkTooltip(descriptionFor(role)) {
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(28.dp)
                .background(PAPER)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (role != null) drawSpawnToken(role, center = Offset(size.width / 2f, size.height / 2f), radius = size.minDimension * 0.4f, partySprite)
                drawRect(INK, Offset.Zero, size, style = Stroke(if (selected) 2.5f else 1f))
            }
        }
    }
}

/** docs/36-map-triggers.md: a small ink star glyph, distinct from a Spawn zone's colored token and a Prop's sprite. */
private fun DrawScope.drawTriggerGlyph(center: Offset, radius: Float, color: Color = INK) {
    val outer = 5
    val path = Path()
    for (i in 0 until outer * 2) {
        val r = if (i % 2 == 0) radius else radius * 0.4f
        val angle = (PI / outer * i - PI / 2).toFloat()
        val point = Offset(center.x + r * cos(angle), center.y + r * sin(angle))
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color, style = Stroke(2f))
}

@Composable
private fun TriggerToolSwatch(selected: Boolean, onClick: () -> Unit) {
    InkTooltip("Trigger: fires a one-shot effect list when a player-controlled character enters this cell.") {
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(28.dp)
                .background(PAPER)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTriggerGlyph(center = Offset(size.width / 2f, size.height / 2f), radius = size.minDimension * 0.4f)
                drawRect(INK, Offset.Zero, size, style = Stroke(if (selected) 2.5f else 1f))
            }
        }
    }
}

/** docs/52-organic-decoration-placement.md: screen-pixel hit radius for tapping/dragging an already-placed decoration — independent of zoom, so it stays easy to grab whether zoomed in or out. */
/** Bumped from 20 to 40 alongside removing click-to-place — the whole point of a click now is "find and edit the decoration I meant," so being generous here matters more than it did when a near-miss just fell back to placing a new one harmlessly. */
private const val DECORATION_HIT_RADIUS_PX = 40f
private const val ROTATE_HANDLE_DISTANCE_PX = 48f
private const val ROTATE_HANDLE_HIT_RADIUS_PX = 22f
private const val ROTATE_HANDLE_DRAWN_RADIUS_PX = 11f

private fun decorationCenterScreen(d: DecorationPlacement, camera: Offset, zoom: Float): Offset =
    worldToScreen(Offset(d.x * TILE_PX, d.y * TILE_PX), camera, zoom)

private fun rotateHandleScreen(d: DecorationPlacement, center: Offset): Offset {
    val rad = d.rotationDegrees * PI.toFloat() / 180f
    return Offset(center.x + ROTATE_HANDLE_DISTANCE_PX * cos(rad), center.y + ROTATE_HANDLE_DISTANCE_PX * sin(rad))
}

private const val MIN_ZOOM = 0.4f
private const val MAX_ZOOM = 4f

/** Same camera/zoom transform as :ui's Board (docs/15-battle-ui.md "pan + zoom") — world-space tile coordinates converted to on-screen pixels, so every draw call and every hit-test goes through the same two functions. */
private fun worldToScreen(world: Offset, camera: Offset, zoom: Float): Offset = (world - camera) * zoom

private fun screenToWorld(screen: Offset, camera: Offset, zoom: Float): Offset = screen / zoom + camera

/**
 * The map used to render at a fixed size exactly matching its tile dimensions — fine for an 8x8
 * room, useless once maps grow ("the map is too small"). This now fills whatever space the caller
 * gives it and pans/zooms like the real battle board: scroll wheel to zoom, drag to pan. A click
 * still resolves to a grid cell/edge the same way, just through [screenToWorld] first.
 */
@Composable
private fun MapCanvas(
    map: BattleMapDef,
    tool: PaintTool,
    onPaintCell: (GridPos) -> Unit,
    onToggleWall: (GridPos, Side) -> Unit,
    editingTriggerId: TriggerId? = null,
    editingGateId: GateId? = null,
    selectedDecorationId: DecorationId? = null,
    onSelectDecoration: (DecorationId?) -> Unit = {},
    onDecorationsChange: (List<DecorationPlacement>) -> Unit = {},
) {
    // docs/52-organic-decoration-placement.md: the decoration drag/rotate pointerInput block below
    // is keyed on (map.id, tool) only (see its own comment for why) — it can stay running across
    // many separate gestures without ever restarting. A plain closure over `map`/the callback
    // params would freeze them at whatever recomposition the block last (re)launched from, so an
    // edit made through DecorationEditorPanel's side panel BETWEEN two drags (e.g. a scale change)
    // would get silently reverted the next time this block reads its stale `map` and calls its
    // stale `onDecorationsChange` — found live ("don't reset scale when positioning again").
    // rememberUpdatedState is the standard fix: always read/call the LATEST value without
    // restarting (and so aborting) an in-flight gesture.
    val currentMap = rememberUpdatedState(map)
    val currentSelectedDecorationId = rememberUpdatedState(selectedDecorationId)
    val currentOnDecorationsChange = rememberUpdatedState(onDecorationsChange)
    val currentOnSelectDecoration = rememberUpdatedState(onSelectDecoration)
    val tiles = remember(map.terrain) { expandTerrainRuns(map.terrain) }
    val spawns = remember(map.spawns) { map.spawns.flatMap { zone -> zone.tiles.map { it to zone.role } }.toMap() }
    val partySprite = remember { SpriteLoader.load(PARTY_SPRITE_PATH) }
    val floorSwatch = remember(map.floorTexture) {
        val meta = map.floorTexture?.let { AssetManifest.floorTexture(it) } ?: return@remember null
        val sheet = SpriteLoader.load(PROPS_DIR + meta.file) ?: return@remember null
        sheet to (meta.tilesW ?: 1)
    }
    // docs/35-wall-background-punch-through.md: only loaded when the map actually uses it, same
    // "only decode what this map needs" discipline floorSwatch above already follows.
    val backgroundImage = remember(map.wallStyle) {
        if (map.wallStyle != WallStyle.Background) return@remember null
        val meta = AssetManifest.prop(BACKGROUND_ASSET_ID) ?: return@remember null
        SpriteLoader.load(PROPS_DIR + meta.file)
    }
    var camera by remember(map.id) { mutableStateOf(Offset(-CANVAS_PADDING, -CANVAS_PADDING)) }
    var zoom by remember(map.id) { mutableStateOf(1f) }
    var lastPressPos by remember { mutableStateOf(Offset.Zero) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }

    fun hoveredCell(screen: Offset): Pair<GridPos, Offset>? {
        val world = screenToWorld(screen, camera, zoom)
        val col = (world.x / TILE_PX).toInt()
        val row = (world.y / TILE_PX).toInt()
        if (col !in 0 until map.width || row !in 0 until map.height) return null
        val relX = (world.x - col * TILE_PX) / TILE_PX
        val relY = (world.y - row * TILE_PX) / TILE_PX
        return GridPos(col, row) to Offset(relX, relY)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Same proven pattern as :ui's Board Canvas: detectTapGestures never fired reliably in
                // this dev environment, a plain clickable + a raw down-position tracker does.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        lastPressPos = down.position
                    }
                }
                // Same gesture detector :ui's Board panning uses (detectTransformGestures, not
                // detectDragGestures — the latter stopped recognizing drags here). Zoom stays solely
                // the scroll-wheel handler below; this only ever applies pan.
                //
                // Keyed on map.id, not Unit: a pointerInput block only restarts when its key changes,
                // so a Unit key keeps running the FIRST map's coroutine forever — its closure captured
                // that map's `camera`/`zoom` MutableState instances (remember(map.id) makes a new pair
                // per map), so dragging after switching maps kept mutating the original map's
                // (now-invisible) camera instead of the one on screen. This is exactly the bug: "drag
                // works on the first map, not after switching."
                .pointerInput(map.id) {
                    detectTransformGestures { _, pan, _, _ ->
                        camera -= pan / zoom
                    }
                }
                .onPointerEvent(PointerEventType.Move) { event -> hoverPos = event.changes.first().position }
                .onPointerEvent(PointerEventType.Exit) { hoverPos = null }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                    val factor = if (scrollY < 0f) 1.1f else 1f / 1.1f
                    zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                }
                // docs/52-organic-decoration-placement.md: entirely gated on the Decoration tool
                // being active — every other tool's gesture behavior above is completely
                // unaffected. Keyed on (map.id, tool) only, deliberately NOT on `map.decorations` —
                // keying on the list this block itself mutates every drag-frame would restart (and
                // so abort) the gesture on its own first move.
                .pointerInput(map.id, tool) {
                    if (tool !is PaintTool.Decoration) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startScreen = down.position
                        val decorations = currentMap.value.decorations

                        val selected = decorations.firstOrNull { it.id == currentSelectedDecorationId.value }
                        val handleScreen = selected?.let { rotateHandleScreen(it, decorationCenterScreen(it, camera, zoom)) }
                        val hitHandle = handleScreen != null && (handleScreen - startScreen).getDistance() <= ROTATE_HANDLE_HIT_RADIUS_PX
                        val hitDecoration = if (hitHandle) {
                            null
                        } else {
                            decorations
                                .map { it to (decorationCenterScreen(it, camera, zoom) - startScreen).getDistance() }
                                .filter { (_, dist) -> dist <= DECORATION_HIT_RADIUS_PX }
                                .minByOrNull { (_, dist) -> dist }
                                ?.first
                        }

                        // docs/52-organic-decoration-placement.md amendment: a plain click never
                        // places a new decoration anymore — found live ("hard to edit again"), a
                        // near-miss click on an existing one silently stacked a fresh one on top
                        // instead of opening its editor. Placement moved to an explicit "+ Add"
                        // button (MapEditorPanel's DECORATIONS row) instead. Empty space is left
                        // completely unconsumed here, so panning is unaffected.
                        if (!hitHandle && hitDecoration == null) return@awaitEachGesture

                        down.consume()
                        val targetId = if (hitHandle) selected!!.id else hitDecoration!!.id
                        // Read fresh at the START of every gesture (not cached across gestures) so a
                        // panel edit (scale, tint, flip) made since the last drag is never clobbered
                        // by this one — only x/y (or rotationDegrees) ever get overwritten below,
                        // every other field rides along from this fresh snapshot untouched.
                        val startPlacement = currentMap.value.decorations.first { it.id == targetId }
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            moved = true
                            val updated = if (hitHandle) {
                                val center = decorationCenterScreen(startPlacement, camera, zoom)
                                val angle = atan2(change.position.y - center.y, change.position.x - center.x) * 180f / PI.toFloat()
                                startPlacement.copy(rotationDegrees = angle)
                            } else {
                                val world = screenToWorld(change.position, camera, zoom)
                                startPlacement.copy(x = world.x / TILE_PX, y = world.y / TILE_PX)
                            }
                            currentOnDecorationsChange.value(currentMap.value.decorations.map { if (it.id == targetId) updated else it })
                        }
                        if (!moved) currentOnSelectDecoration.value(targetId)
                    }
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val (pos, rel) = hoveredCell(lastPressPos) ?: return@clickable
                    if (tool == PaintTool.Wall || tool == PaintTool.Gate) {
                        onToggleWall(pos, nearestSide(rel.x, rel.y))
                    } else {
                        onPaintCell(pos)
                    }
                },
        ) {
            fun toScreen(world: Offset) = worldToScreen(world, camera, zoom)
            val screenTile = TILE_PX * zoom

            // docs/35-wall-background-punch-through.md: drawn first, under everything — floor
            // cells paint opaquely over it in the very next pass, same as :ui's own Board.
            backgroundImage?.let { bg ->
                drawBackgroundImage(
                    bg, map.width, map.height, map.backgroundMarginTiles, TILE_PX, zoom,
                    screenToWorld = { screenToWorld(it, camera, zoom) },
                    toScreen = ::toScreen,
                )
            }

            // Pick a stable-but-varied swatch cell from the floor sheet using the tile's own
            // position, so neighbouring floor tiles don't all show the identical sub-image.
            fun patchAt(swatch: Pair<ImageBitmap, Int>?, col: Int, row: Int): FloorPatch? = swatch?.let { (sheet, cols) ->
                val cell = sheet.width / cols
                val sc = (col + row) % cols
                val sr = (col * 3 + row * 5) % cols
                FloorPatch(sheet, IntOffset(sc * cell, sr * cell), IntSize(cell, cell))
            }
            // Floor cells only here — Wall cells (hatch or flat fill) are drawn LAST among the
            // background layers, after grid lines, so they always paint over any grid line segment
            // that would otherwise show through them. Previously walls were drawn before the grid
            // lines, which is why the grid was visible through both hatched and solid walls.
            for (col in 0 until map.width) {
                for (row in 0 until map.height) {
                    val tile = tiles[GridPos(col, row)] ?: TileType.Floor
                    if (tile == TileType.Wall) continue
                    val rect = Rect(toScreen(Offset(col * TILE_PX, row * TILE_PX)), Size(screenTile, screenTile))
                    drawTerrainCell(tile, rect, patchAt(floorSwatch, col, row))
                }
            }
            // docs/31-wall-shadow-casting.md: same ordering as the floor fill above — before grid
            // lines, so they stay legible on top. Shared with :ui's real Board (drawWallShadows)
            // so an author sees the same shadow while placing walls that they'll see in Playtest.
            drawWallShadows(
                isWall = { (tiles[it] ?: TileType.Floor) == TileType.Wall },
                hasWallEdge = { pos, side ->
                    val d = sideDelta(side)
                    WallEdge(pos, side) in map.wallEdges ||
                        WallEdge(GridPos(pos.col + d.col, pos.row + d.row), side.opposite()) in map.wallEdges
                },
                cols = 0 until map.width,
                rows = 0 until map.height,
                tilePx = TILE_PX,
                zoom = zoom,
                ink = INK,
                toScreen = ::toScreen,
            )
            // Grid lines drawn after floor fill (not before) so a filled Floor/hazard cell never
            // paints over the line under it — was the original "grid lines missing" bug.
            val gridTop = toScreen(Offset(0f, 0f)).y
            val gridBottom = toScreen(Offset(0f, map.height * TILE_PX)).y
            for (col in 0..map.width) {
                val x = toScreen(Offset(col * TILE_PX, 0f)).x
                drawLine(INK_FAINT, Offset(x, gridTop), Offset(x, gridBottom))
            }
            val gridLeft = toScreen(Offset(0f, 0f)).x
            val gridRight = toScreen(Offset(map.width * TILE_PX, 0f)).x
            for (row in 0..map.height) {
                val y = toScreen(Offset(0f, row * TILE_PX)).y
                drawLine(INK_FAINT, Offset(gridLeft, y), Offset(gridRight, y))
            }
            // Walls last, so they always paint over whatever grid line just crossed that cell. A flat
            // wall (drawTerrainCell) is already an opaque INK rect, but drawWallHatch itself is ONLY
            // the sparse hand-drawn strokes — no solid backing — so grid lines were still visible in
            // the gaps between strokes even with hatch drawn last. An opaque PAPER base fill under the
            // hatch (same background tone as everywhere else) closes those gaps.
            when (map.wallStyle) {
                WallStyle.Hatch, WallStyle.Osr -> {
                    for (col in 0 until map.width) {
                        for (row in 0 until map.height) {
                            if (tiles[GridPos(col, row)] != TileType.Wall) continue
                            val rect = Rect(toScreen(Offset(col * TILE_PX, row * TILE_PX)), Size(screenTile, screenTile))
                            drawRect(PAPER, rect.topLeft, rect.size)
                        }
                    }
                    if (map.wallStyle == WallStyle.Hatch) {
                        val isWall = { pos: GridPos -> (tiles[pos] ?: TileType.Floor) == TileType.Wall }
                        drawWallHatch(isWall = isWall, cols = 0 until map.width, rows = 0 until map.height, tilePx = TILE_PX, zoom = zoom, ink = INK, toScreen = ::toScreen)
                    } else {
                        // docs/33-wall-hatch-osr-packing.md: renders the last-baked geometry only —
                        // painting doesn't regenerate it live, see the "Regenerate Hatch" button.
                        drawWallHatchOsr(lines = map.wallHatchOsr, cols = 0 until map.width, rows = 0 until map.height, tilePx = TILE_PX, zoom = zoom, ink = INK, toScreen = ::toScreen)
                    }
                }
                WallStyle.Flat -> {
                    for (col in 0 until map.width) {
                        for (row in 0 until map.height) {
                            if (tiles[GridPos(col, row)] != TileType.Wall) continue
                            val rect = Rect(toScreen(Offset(col * TILE_PX, row * TILE_PX)), Size(screenTile, screenTile))
                            drawTerrainCell(TileType.Wall, rect, null)
                        }
                    }
                }
                // docs/35-wall-background-punch-through.md: paints nothing — the background image
                // drawn at the very top of this canvas is still sitting there untouched under a
                // Wall cell, same reasoning as :ui's own Board.
                WallStyle.Background -> Unit
            }
            // Automatic outline around every Wall mass (see wallOutlineSegments' doc comment) — drawn
            // for both hatch and flat rendering; a no-op visually on a flat wall (same INK color as
            // its own fill) but is what gives a hatched wall the clean solid border in the reference
            // look, without the author placing a WallEdge by hand around every hatch region.
            for ((a, b) in wallOutlineSegments(tiles, map.width, map.height)) {
                drawLine(INK, toScreen(a), toScreen(b), strokeWidth = 4f * zoom)
            }
            for (edge in map.wallEdges) {
                val (a, b) = wallSegment(edge)
                drawLine(INK, toScreen(a), toScreen(b), strokeWidth = 4f * zoom)
            }
            // docs/48-gates-and-wander-ai.md: dashed, distinct from a solid WallEdge — fainter still
            // (INK_FAINT) for a secret door (no closedSprite), the one authoring-only affordance that
            // deliberately does NOT match what a player sees (that's the whole point of "secret").
            // docs/48-gates-and-wander-ai.md: the actual closedSprite bitmap, same as :ui's Board
            // draws it — this used to only ever draw a dashed line regardless of which sprite (or
            // none) was picked, so choosing between two real sprites looked identical (found live:
            // "I can see no difference" between Chair1x1 and arrow1x1). Falls back to the dashed
            // glyph only when no closedSprite is chosen at all (the secret-door look).
            for (gate in map.gates) {
                val meta = gate.closedSprite?.let { AssetManifest.prop(it) }
                val sprite = meta?.let { SpriteLoader.load(PROPS_DIR + it.file) }
                for (edge in gate.edges) {
                    val (a, b) = wallSegment(edge)
                    val screenA = toScreen(a)
                    val screenB = toScreen(b)
                    if (sprite != null) {
                        drawGateEdgeSprite(sprite, screenA, screenB, edge.side, zoom)
                    } else {
                        drawLine(INK_FAINT, screenA, screenB, strokeWidth = 4f * zoom, pathEffect = GATE_DASH)
                    }
                    if (gate.id == editingGateId) {
                        drawLine(DANGER, screenA, screenB, strokeWidth = 2f * zoom, pathEffect = GATE_DASH)
                    }
                }
            }
            // docs/51-props-catalog-and-placement.md: rotation/flip/tint applied here too — "author
            // sees what the player sees" (docs/36/docs/48's own precedent), same flip-then-rotate
            // composition order :ui's Board uses.
            for (placement in map.props) {
                val meta = AssetManifest.prop(placement.prop.raw) ?: continue
                val bmp = SpriteLoader.load(PROPS_DIR + meta.file) ?: continue
                val w = (meta.tilesW ?: 1) * screenTile
                val h = (meta.tilesH ?: 1) * screenTile
                val topLeft = toScreen(Offset(placement.at.col * TILE_PX, placement.at.row * TILE_PX))
                val dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
                val dstSize = IntSize(w.roundToInt(), h.roundToInt())
                val center = Offset(dstOffset.x + dstSize.width / 2f, dstOffset.y + dstSize.height / 2f)
                val colorFilter = placement.tint?.let { ColorFilter.tint(Color(it)) }
                rotate(degrees = 90f * placement.rotationQuarters, pivot = center) {
                    scale(scaleX = if (placement.flipX) -1f else 1f, scaleY = 1f, pivot = center) {
                        drawImage(bmp, dstOffset = dstOffset, dstSize = dstSize, colorFilter = colorFilter)
                    }
                }
            }
            // docs/52-organic-decoration-placement.md: free position/rotation, centered (not
            // top-left) anchor — see DecorationPlacement's own doc comment for why. The selected
            // one gets a dashed selection ring plus its rotate handle (a line to a small circle at
            // the angle it currently points), drawn last so both sit on top of the sprite.
            for (placement in map.decorations) {
                val meta = AssetManifest.prop(placement.prop.raw) ?: continue
                val bmp = SpriteLoader.load(PROPS_DIR + meta.file) ?: continue
                val center = toScreen(Offset(placement.x * TILE_PX, placement.y * TILE_PX))
                val dstSize = IntSize(((meta.tilesW ?: 1) * screenTile * placement.scale).roundToInt(), ((meta.tilesH ?: 1) * screenTile * placement.scale).roundToInt())
                val dstOffset = IntOffset((center.x - dstSize.width / 2f).roundToInt(), (center.y - dstSize.height / 2f).roundToInt())
                val colorFilter = placement.tint?.let { ColorFilter.tint(Color(it)) }
                rotate(degrees = placement.rotationDegrees, pivot = center) {
                    scale(scaleX = if (placement.flipX) -1f else 1f, scaleY = 1f, pivot = center) {
                        drawImage(bmp, dstOffset = dstOffset, dstSize = dstSize, colorFilter = colorFilter)
                    }
                }
                if (placement.id == selectedDecorationId) {
                    drawCircle(DANGER, radius = maxOf(dstSize.width, dstSize.height) / 2f + 4f, center = center, style = Stroke(width = 2f, pathEffect = GATE_DASH))
                    val handle = rotateHandleScreen(placement, center)
                    drawLine(DANGER, center, handle, strokeWidth = 2f)
                    drawCircle(DANGER, radius = ROTATE_HANDLE_DRAWN_RADIUS_PX, center = handle)
                }
            }
            for (col in 0 until map.width) {
                for (row in 0 until map.height) {
                    spawns[GridPos(col, row)]?.let { role ->
                        val center = toScreen(Offset(col * TILE_PX + TILE_PX / 2f, row * TILE_PX + TILE_PX / 2f))
                        drawSpawnToken(role, center, TILE_PX * 0.32f * zoom, partySprite)
                    }
                }
            }
            // docs/36-map-triggers.md: an author sees exactly what a player would walk into — same
            // "author sees what the player sees" precedent every other wall/shadow style follows.
            for (trigger in map.triggers) {
                val center = toScreen(Offset(trigger.at.col * TILE_PX + TILE_PX / 2f, trigger.at.row * TILE_PX + TILE_PX / 2f))
                drawTriggerGlyph(center, TILE_PX * 0.3f * zoom, if (trigger.id == editingTriggerId) DANGER else INK)
            }
            if (tool == PaintTool.Wall || tool == PaintTool.Gate) {
                hoverPos?.let { hp ->
                    val (pos, rel) = hoveredCell(hp) ?: return@let
                    val (a, b) = wallSegment(WallEdge(pos, nearestSide(rel.x, rel.y)))
                    drawLine(DANGER, toScreen(a), toScreen(b), strokeWidth = 4f * zoom, pathEffect = if (tool == PaintTool.Gate) GATE_DASH else null)
                }
            }
        }

        hoverPos?.let { hp ->
            val (pos, _) = hoveredCell(hp) ?: return@let
            val tile = tiles[pos] ?: TileType.Floor
            val role = spawns[pos]
            val prop = map.props.find { it.at == pos }
            Popup(
                offset = IntOffset(hp.x.roundToInt() + 14, hp.y.roundToInt() + 14),
                properties = PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .background(INK)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Column {
                        if (tool == PaintTool.Wall) {
                            BasicText(WALL_DESCRIPTION, style = TextStyle(color = PAPER, fontSize = 11.sp))
                        } else if (tool == PaintTool.Gate) {
                            BasicText(GATE_DESCRIPTION, style = TextStyle(color = PAPER, fontSize = 11.sp))
                        } else {
                            BasicText(descriptionFor(tile), style = TextStyle(color = PAPER, fontSize = 11.sp))
                            if (role != null) BasicText(descriptionFor(role), style = TextStyle(color = PAPER, fontSize = 11.sp))
                            if (prop != null) BasicText("Prop: ${prop.prop.raw}", style = TextStyle(color = PAPER, fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }
}
