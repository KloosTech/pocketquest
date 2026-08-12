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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
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
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.PropId
import de.jackbeback.pocketquest.core.model.PropLayer
import de.jackbeback.pocketquest.core.model.PropPlacement
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.SpawnZone
import de.jackbeback.pocketquest.core.model.TileType
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
import kotlin.math.roundToInt

private const val TILE_PX = 32f
// docs/23-sprite-rendering.md: :ui's composeResources tree is the one location actually bundled
// cross-platform, not a repo-root duplicate — :designer reads that same tree directly.
private const val PARTY_SPRITE_PATH = "ui/src/commonMain/composeResources/files/normalized/characters/hero_a_idle.png"
// Not private — ArchetypePanel.kt's sprite picker reuses this same base path (docs/23).
const val PROPS_DIR = "ui/src/commonMain/composeResources/files/normalized/"
private const val CANVAS_PADDING = 48f

/** What a click currently paints. [Wall] toggles the nearest tile edge rather than a cell; [Prop] places/erases a footprint-sized piece of furniture anchored at the clicked cell. */
private sealed interface PaintTool {
    data class Terrain(val tile: TileType) : PaintTool
    data class Spawn(val role: SpawnRole?) : PaintTool
    object Wall : PaintTool
    data class Prop(val asset: ManifestAsset?) : PaintTool
}

private fun paintCell(map: BattleMapDef, pos: GridPos, tool: PaintTool): BattleMapDef = when (tool) {
    is PaintTool.Terrain -> {
        val tiles = expandTerrainRuns(map.terrain).toMutableMap()
        if (tool.tile == TileType.Floor) tiles.remove(pos) else tiles[pos] = tool.tile
        map.copy(terrain = compressTerrainToRuns(tiles, map.width, map.height))
    }
    is PaintTool.Spawn -> {
        val bySpawn = map.spawns.flatMap { zone -> zone.tiles.map { it to zone.role } }.toMap().toMutableMap()
        if (tool.role == null) bySpawn.remove(pos) else bySpawn[pos] = tool.role
        val zones = bySpawn.entries.groupBy({ it.value }, { it.key }).map { (role, tiles) -> SpawnZone(role, tiles) }
        map.copy(spawns = zones)
    }
    is PaintTool.Prop -> {
        val withoutHere = map.props.filterNot { it.at == pos }
        val placed = tool.asset?.let { withoutHere + PropPlacement(PropId(it.id), pos, PropLayer.Object) } ?: withoutHere
        map.copy(props = placed)
    }
    PaintTool.Wall -> map
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

private fun GridPos.neighbor(side: Side): GridPos = when (side) {
    Side.North -> copy(row = row - 1)
    Side.South -> copy(row = row + 1)
    Side.East -> copy(col = col + 1)
    Side.West -> copy(col = col - 1)
}

/**
 * Derived, not authored: every side of a whole-tile [TileType.Wall] cell that borders a non-Wall
 * cell (including off the map edge, since a missing [tiles] entry already defaults to Floor) gets a
 * solid outline — this is what makes a painted Wall mass in the reference screenshot read as one
 * solid building with a clean border, without the author separately placing a `WallEdge` (the
 * "Wall (edge)" tool's thin room-divider) around every hatch region by hand. Manually placed
 * `WallEdge`s (interior thin dividers between two Floor cells) are unrelated and still drawn
 * separately from `map.wallEdges` — this never reads or writes that list.
 */
private fun wallOutlineSegments(tiles: Map<GridPos, TileType>, width: Int, height: Int): List<Pair<Offset, Offset>> {
    val segments = mutableListOf<Pair<Offset, Offset>>()
    for (col in 0 until width) {
        for (row in 0 until height) {
            val pos = GridPos(col, row)
            if ((tiles[pos] ?: TileType.Floor) != TileType.Wall) continue
            for (side in Side.entries) {
                val neighbor = pos.neighbor(side)
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
    }
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
                    onCatalogChange(catalog.copy(maps = catalog.maps + (id to BattleMapDef(id = id, name = "New Map $n", width = w, height = h))))
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

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                listOf(TileType.Floor, TileType.Wall, TileType.Difficult, TileType.Hazard).forEach { t ->
                    TerrainToolSwatch(t, selected = (tool as? PaintTool.Terrain)?.tile == t, onClick = { tool = PaintTool.Terrain(t) })
                }
                WallToolSwatch(selected = tool == PaintTool.Wall, onClick = { tool = PaintTool.Wall })
            }
            InkLabel("SPAWN ZONE", modifier = Modifier.padding(top = 8.dp))
            Row {
                SpawnToolSwatch(null, selected = tool.let { it is PaintTool.Spawn && it.role == null }, onClick = { tool = PaintTool.Spawn(null) })
                SpawnRole.entries.forEach { role ->
                    SpawnToolSwatch(role, selected = (tool as? PaintTool.Spawn)?.role == role, onClick = { tool = PaintTool.Spawn(role) })
                }
            }
            InkLabel("PROPS", modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val currentAsset = (tool as? PaintTool.Prop)?.asset
                InkSelect(
                    selected = currentAsset,
                    options = listOf<ManifestAsset?>(null) + AssetManifest.placeableProps,
                    label = { it?.let { a -> "${a.id} (${a.tilesW}x${a.tilesH})" } ?: "Erase" },
                    onSelect = { asset -> tool = PaintTool.Prop(asset) },
                    modifier = Modifier.width(200.dp),
                    itemContent = { asset ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val bmp = asset?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
                            if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(end = 6.dp))
                            BasicText(
                                asset?.let { "${it.id} (${it.tilesW}x${it.tilesH})" } ?: "Erase",
                                style = TextStyle(color = INK, fontSize = 13.sp),
                            )
                        }
                    },
                )
                if (currentAsset != null) {
                    val bmp = remember(currentAsset.file) { SpriteLoader.load(PROPS_DIR + currentAsset.file) }
                    if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                MapCanvas(
                    map = map,
                    tool = tool,
                    onPaintCell = { pos -> updateMap { paintCell(it, pos, tool) } },
                    onToggleWall = { pos, side -> updateMap { it.copy(wallEdges = toggleWallEdge(it.wallEdges, pos, side)) } },
                )
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
) {
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
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val (pos, rel) = hoveredCell(lastPressPos) ?: return@clickable
                    if (tool == PaintTool.Wall) {
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
            for (placement in map.props) {
                val meta = AssetManifest.prop(placement.prop.raw) ?: continue
                val bmp = SpriteLoader.load(PROPS_DIR + meta.file) ?: continue
                val w = (meta.tilesW ?: 1) * screenTile
                val h = (meta.tilesH ?: 1) * screenTile
                val topLeft = toScreen(Offset(placement.at.col * TILE_PX, placement.at.row * TILE_PX))
                drawImage(bmp, dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
            }
            for (col in 0 until map.width) {
                for (row in 0 until map.height) {
                    spawns[GridPos(col, row)]?.let { role ->
                        val center = toScreen(Offset(col * TILE_PX + TILE_PX / 2f, row * TILE_PX + TILE_PX / 2f))
                        drawSpawnToken(role, center, TILE_PX * 0.32f * zoom, partySprite)
                    }
                }
            }
            if (tool == PaintTool.Wall) {
                hoverPos?.let { hp ->
                    val (pos, rel) = hoveredCell(hp) ?: return@let
                    val (a, b) = wallSegment(WallEdge(pos, nearestSide(rel.x, rel.y)))
                    drawLine(DANGER, toScreen(a), toScreen(b), strokeWidth = 4f * zoom)
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
