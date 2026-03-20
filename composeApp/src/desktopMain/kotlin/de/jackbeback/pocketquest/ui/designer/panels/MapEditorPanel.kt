package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.map.TileType
import de.jackbeback.pocketquest.designer.io.MapFileIo
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.util.defaultResourcesDir
import de.jackbeback.pocketquest.ui.designer.util.showDirDialog
import de.jackbeback.pocketquest.ui.designer.util.showImageDialog
import de.jackbeback.pocketquest.ui.designer.util.showJsonDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

// ── Terrain colors ────────────────────────────────────────────────────────────

private fun tileTypeColor(type: TileType): Color? = when (type) {
    TileType.FLOOR             -> null
    TileType.WALL              -> Color(0xFF808080)
    TileType.WATER             -> Color(0xFF4090FF)
    TileType.COVER_LOW         -> Color(0xFF90FF90)
    TileType.COVER_HIGH        -> Color(0xFF00C040)
    TileType.DIFFICULT_TERRAIN -> Color(0xFFFFB020)
    TileType.HAZARD            -> Color(0xFFFF3030)
    TileType.VOID              -> Color(0xFF000000)
}

// ── Main composable ───────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapEditorPanel(
    maps: List<TileMap>,
    selectedMapId: String?,
    onMapExported: (TileMap) -> Unit,
    onMapLoaded: (TileMap) -> Unit,
    onSelectMap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // ── Persistent editor state ───────────────────────────────────────────────
    var sourceBufferedImage by remember { mutableStateOf<BufferedImage?>(null) }
    var sourceImageBitmap   by remember { mutableStateOf<ImageBitmap?>(null) }
    var imgNativeW          by remember { mutableStateOf(0) }
    var imgNativeH          by remember { mutableStateOf(0) }
    var sourceImageFile     by remember { mutableStateOf<File?>(null) }

    var mapId        by remember { mutableStateOf("") }
    var mapName      by remember { mutableStateOf("") }
    var tileWidthPx  by remember { mutableStateOf("64") }
    var tileHeightPx by remember { mutableStateOf("64") }
    var cols         by remember { mutableStateOf("9") }
    var rows         by remember { mutableStateOf("13") }
    var offsetX      by remember { mutableStateOf("0") }
    var offsetY      by remember { mutableStateOf("0") }
    var exportDir    by remember { mutableStateOf(defaultResourcesDir()?.absolutePath ?: "") }
    var paintedTiles by remember { mutableStateOf(mapOf<Pair<Int, Int>, TileType>()) }
    var activePaint  by remember { mutableStateOf(TileType.WALL) }
    var statusMsg    by remember { mutableStateOf("Load an image to begin") }
    var statusIsError by remember { mutableStateOf(false) }

    // ── Viewport ──────────────────────────────────────────────────────────────
    var zoom    by remember { mutableStateOf(1f) }
    var panX    by remember { mutableStateOf(0f) }
    var panY    by remember { mutableStateOf(0f) }
    var canvasW by remember { mutableStateOf(0) }
    var canvasH by remember { mutableStateOf(0) }

    fun fitToCanvas() {
        if (imgNativeW <= 0 || canvasW <= 0) return
        val scaleX = canvasW.toFloat() / imgNativeW
        val scaleY = canvasH.toFloat() / imgNativeH
        zoom = minOf(scaleX, scaleY).coerceAtLeast(0.05f)
        panX = (canvasW - imgNativeW * zoom) / 2f
        panY = (canvasH - imgNativeH * zoom) / 2f
    }

    LaunchedEffect(imgNativeW, canvasW) { fitToCanvas() }

    // Sync library → editor when a map is selected from the library panel
    LaunchedEffect(selectedMapId) {
        val map = maps.find { it.id == selectedMapId } ?: return@LaunchedEffect
        if (mapId == map.id) return@LaunchedEffect
        mapId        = map.id
        mapName      = map.name
        tileWidthPx  = map.tileWidthPx.toString()
        tileHeightPx = map.tileHeightPx.toString()
        cols         = map.cols.toString()
        rows         = map.rows.toString()
        paintedTiles = map.tiles.associate { (it.col to it.row) to it.type }
        statusMsg    = "Loaded map: ${map.name}"
    }

    val tw      = tileWidthPx.toIntOrNull()  ?: 64
    val th      = tileHeightPx.toIntOrNull() ?: 64
    val numCols = cols.toIntOrNull() ?: 9
    val numRows = rows.toIntOrNull() ?: 13
    val ox      = offsetX.toIntOrNull()  ?: 0
    val oy      = offsetY.toIntOrNull()  ?: 0

    fun canvasToTile(canvX: Float, canvY: Float): Pair<Int, Int>? {
        val imgX = (canvX - panX) / zoom
        val imgY = (canvY - panY) / zoom
        val col = ((imgX - ox) / tw).toInt()
        val row = ((imgY - oy) / th).toInt()
        if (col !in 0 until numCols || row !in 0 until numRows) return null
        return col to row
    }

    fun paintAt(canvX: Float, canvY: Float) {
        val tile = canvasToTile(canvX, canvY) ?: return
        paintedTiles = if (activePaint == TileType.FLOOR) {
            paintedTiles - tile
        } else {
            paintedTiles + (tile to activePaint)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Row(modifier = modifier.fillMaxSize()) {

        MapEditorSidebar(
            sourceImageFile  = sourceImageFile,
            imgNativeW       = imgNativeW,
            imgNativeH       = imgNativeH,
            mapId            = mapId,
            mapName          = mapName,
            tileWidthPx      = tileWidthPx,
            tileHeightPx     = tileHeightPx,
            cols             = cols,
            rows             = rows,
            offsetX          = offsetX,
            offsetY          = offsetY,
            exportDir        = exportDir,
            paintedTiles     = paintedTiles,
            activePaint      = activePaint,
            zoom             = zoom,
            numCols          = numCols,
            numRows          = numRows,
            tw               = tw,
            th               = th,
            ox               = ox,
            oy               = oy,
            statusMsg        = statusMsg,
            statusIsError    = statusIsError,
            sourceBufferedImage = sourceBufferedImage,
            onMapIdChange    = { mapId = it },
            onMapNameChange  = { mapName = it },
            onTileWidthChange  = { tileWidthPx = it },
            onTileHeightChange = { tileHeightPx = it },
            onColsChange     = { cols = it },
            onRowsChange     = { rows = it },
            onOffsetXChange  = { offsetX = it },
            onOffsetYChange  = { offsetY = it },
            onExportDirChange = { exportDir = it },
            onActivePaintChange = { activePaint = it },
            onPaintedTilesChange = { paintedTiles = it },
            onStatusChange   = { msg, err -> statusMsg = msg; statusIsError = err },
            onImageLoaded    = { img, bmp, file ->
                sourceBufferedImage = img
                sourceImageBitmap   = bmp
                imgNativeW          = img.width
                imgNativeH          = img.height
                sourceImageFile     = file
                if (mapId.isEmpty())   mapId   = file.nameWithoutExtension.replace(" ", "_").replace("-", "_")
                if (mapName.isEmpty()) mapName = file.nameWithoutExtension
                statusMsg    = "${file.name}  (${img.width}×${img.height}px)"
                statusIsError = false
            },
            onMapExported    = onMapExported,
            onMapLoaded      = { map ->
                mapId        = map.id
                mapName      = map.name
                tileWidthPx  = map.tileWidthPx.toString()
                tileHeightPx = map.tileHeightPx.toString()
                cols         = map.cols.toString()
                rows         = map.rows.toString()
                paintedTiles = map.tiles.associate { (it.col to it.row) to it.type }
                statusMsg    = "Loaded: ${map.name}  (${map.tiles.size} terrain tiles)"
                statusIsError = false
                onMapLoaded(map)
            },
            onFitToCanvas    = { fitToCanvas() },
            onResetZoom      = {
                zoom = 1f
                panX = (canvasW - imgNativeW) / 2f
                panY = (canvasH - imgNativeH) / 2f
            },
            scope            = scope,
        )

        Box(Modifier.width(1.dp).fillMaxHeight().background(DC.PanelBorder))

        MapEditorCanvas(
            sourceImageBitmap = sourceImageBitmap,
            imgNativeW        = imgNativeW,
            imgNativeH        = imgNativeH,
            zoom              = zoom,
            panX              = panX,
            panY              = panY,
            paintedTiles      = paintedTiles,
            numCols           = numCols,
            numRows           = numRows,
            tw                = tw,
            th                = th,
            ox                = ox,
            oy                = oy,
            onZoomChange      = { zoom = it },
            onPanChange       = { x, y -> panX = x; panY = y },
            onPaintAt         = { x, y -> paintAt(x, y) },
            onCanvasSizeChanged = { w, h ->
                val needFit = canvasW == 0
                canvasW = w
                canvasH = h
                if (needFit) fitToCanvas()
            },
            modifier          = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

// ── Left sidebar ──────────────────────────────────────────────────────────────

@Composable
private fun MapEditorSidebar(
    sourceImageFile: File?,
    imgNativeW: Int,
    imgNativeH: Int,
    mapId: String,
    mapName: String,
    tileWidthPx: String,
    tileHeightPx: String,
    cols: String,
    rows: String,
    offsetX: String,
    offsetY: String,
    exportDir: String,
    paintedTiles: Map<Pair<Int, Int>, TileType>,
    activePaint: TileType,
    zoom: Float,
    numCols: Int,
    numRows: Int,
    tw: Int,
    th: Int,
    ox: Int,
    oy: Int,
    statusMsg: String,
    statusIsError: Boolean,
    sourceBufferedImage: BufferedImage?,
    onMapIdChange: (String) -> Unit,
    onMapNameChange: (String) -> Unit,
    onTileWidthChange: (String) -> Unit,
    onTileHeightChange: (String) -> Unit,
    onColsChange: (String) -> Unit,
    onRowsChange: (String) -> Unit,
    onOffsetXChange: (String) -> Unit,
    onOffsetYChange: (String) -> Unit,
    onExportDirChange: (String) -> Unit,
    onActivePaintChange: (TileType) -> Unit,
    onPaintedTilesChange: (Map<Pair<Int, Int>, TileType>) -> Unit,
    onStatusChange: (String, Boolean) -> Unit,
    onImageLoaded: (BufferedImage, ImageBitmap, File) -> Unit,
    onMapExported: (TileMap) -> Unit,
    onMapLoaded: (TileMap) -> Unit,
    onFitToCanvas: () -> Unit,
    onResetZoom: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(DC.Panel)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EditorSectionTitle("MAP EDITOR", DC.Yellow)

        // ── Source image ──────────────────────────────────────────────────
        EditorCard {
            CardLabel("1 — Source Image")
            ActionChip("Load Image…") {
                val file = showImageDialog()
                if (file != null) {
                    scope.launch {
                        onStatusChange("Loading…", false)
                        val result = withContext(Dispatchers.IO) {
                            MapFileIo.loadSourceImage(file).map { img ->
                                val bytes = ByteArrayOutputStream()
                                    .also { ImageIO.write(img, "png", it) }
                                    .toByteArray()
                                Pair(img, Bitmap.makeFromImage(Image.makeFromEncoded(bytes))
                                    .asComposeImageBitmap())
                            }
                        }
                        result.fold(
                            onSuccess = { (img, bmp) -> onImageLoaded(img, bmp, file) },
                            onFailure = { e -> onStatusChange("Failed: ${e.message}", true) }
                        )
                    }
                }
            }
            if (sourceImageFile != null) {
                Text(
                    "${sourceImageFile.name}  ${imgNativeW}×${imgNativeH}",
                    color = DC.Subtext0, fontSize = 10.sp,
                )
            }
        }

        // ── Grid config ───────────────────────────────────────────────────
        EditorCard {
            CardLabel("2 — Grid Configuration")
            FieldRow("Map ID")          { DField(mapId)        { onMapIdChange(it) } }
            FieldRow("Map Name")        { DField(mapName)      { onMapNameChange(it) } }
            FieldRow("Tile W (px)")     { DField(tileWidthPx)  { onTileWidthChange(it) } }
            FieldRow("Tile H (px)")     { DField(tileHeightPx) { onTileHeightChange(it) } }
            FieldRow("Columns")         { DField(cols)         { onColsChange(it) } }
            FieldRow("Rows")            { DField(rows)         { onRowsChange(it) } }
            FieldRow("Offset X (px)")   { DField(offsetX)      { onOffsetXChange(it) } }
            FieldRow("Offset Y (px)")   { DField(offsetY)      { onOffsetYChange(it) } }
            val totalTiles = numCols * numRows
            val gridW = ox + numCols * tw
            val gridH = oy + numRows * th
            Text(
                "$numCols × $numRows = $totalTiles tiles  |  grid ${gridW}×${gridH}px",
                color = DC.Overlay0, fontSize = 10.sp,
            )
        }

        // ── Tile palette ──────────────────────────────────────────────────
        EditorCard {
            CardLabel("3 — Paint Terrain  (left-click/drag)")
            TileType.entries.forEach { type ->
                val color = tileTypeColor(type) ?: Color(0xFF444455)
                val isActive = activePaint == type
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isActive) color.copy(alpha = 0.25f) else Color.Transparent)
                        .border(1.dp, if (isActive) color else DC.PanelBorder, RoundedCornerShape(4.dp))
                        .clickable { onActivePaintChange(type) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
                    Text(type.name, color = if (isActive) DC.Text else DC.Subtext0, fontSize = 11.sp)
                    val painted = paintedTiles.values.count { it == type }
                    if (painted > 0) Text("×$painted", color = color, fontSize = 9.sp)
                    if (isActive) Spacer(Modifier.weight(1f))
                    if (isActive) Text("▶", color = color, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Clear All") {
                    onPaintedTilesChange(emptyMap())
                    onStatusChange("All terrain cleared", false)
                }
                Text(
                    "${paintedTiles.size} painted",
                    color = DC.Overlay0, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        // ── Viewport controls ─────────────────────────────────────────────
        EditorCard {
            CardLabel("Viewport  (scroll = pan  •  Ctrl+scroll = zoom  •  right-drag = pan)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Fit") { onFitToCanvas() }
                ActionChip("1:1") { onResetZoom() }
                Text("${(zoom * 100).roundToInt()}%", color = DC.Subtext0, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterVertically))
            }
        }

        // ── Export ────────────────────────────────────────────────────────
        EditorCard {
            CardLabel("4 — Export to composeResources")
            FieldRow("Output Dir") { DField(exportDir) { onExportDirChange(it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip("Browse…") { showDirDialog()?.let { onExportDirChange(it.absolutePath) } }
            }
            Spacer(Modifier.height(4.dp))
            SuccessChip("⬆  Export Map") {
                val img = sourceBufferedImage
                if (img == null) {
                    onStatusChange("Load a source image first.", true)
                    return@SuccessChip
                }
                if (mapId.isBlank()) {
                    onStatusChange("Enter a Map ID first.", true)
                    return@SuccessChip
                }
                val outDir = File(exportDir)
                scope.launch {
                    onStatusChange("Exporting tiles…", false)
                    val result = withContext(Dispatchers.IO) {
                        MapFileIo.exportMap(img, mapId, mapName, tw, th, numCols, numRows, ox, oy, paintedTiles, outDir)
                    }
                    result.fold(
                        onSuccess = { tileMap ->
                            val tilesDir = "files/tiles/${tileMap.id}/"
                            onStatusChange("✓ ${numCols * numRows} tiles → $tilesDir  |  ${tileMap.name}.json", false)
                            onMapExported(tileMap)
                        },
                        onFailure = { e -> onStatusChange("Export failed: ${e.message}", true) }
                    )
                }
            }
            Text(
                "Writes PNG tiles + map JSON into composeResources for all platforms.",
                color = DC.Overlay0, fontSize = 10.sp,
            )
        }

        // ── Load existing JSON ────────────────────────────────────────────
        EditorCard {
            CardLabel("Load Map JSON")
            ActionChip("Open JSON…") {
                val file = showJsonDialog()
                if (file != null) {
                    MapFileIo.loadTileMap(file).fold(
                        onSuccess = { map -> onMapLoaded(map) },
                        onFailure = { e -> onStatusChange("Load failed: ${e.message}", true) }
                    )
                }
            }
        }

        // ── Status ────────────────────────────────────────────────────────
        HorizontalDivider(color = DC.PanelBorder)
        Text(
            statusMsg,
            color = if (statusIsError) DC.Error else DC.Subtext1,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}

// ── Canvas viewport ───────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MapEditorCanvas(
    sourceImageBitmap: ImageBitmap?,
    imgNativeW: Int,
    imgNativeH: Int,
    zoom: Float,
    panX: Float,
    panY: Float,
    paintedTiles: Map<Pair<Int, Int>, TileType>,
    numCols: Int,
    numRows: Int,
    tw: Int,
    th: Int,
    ox: Int,
    oy: Int,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Float, Float) -> Unit,
    onPaintAt: (Float, Float) -> Unit,
    onCanvasSizeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0A0A0F))
            .onSizeChanged { sz -> onCanvasSizeChanged(sz.width, sz.height) }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val dx = change.scrollDelta.x
                val dy = change.scrollDelta.y
                if (dx == 0f && dy == 0f) return@onPointerEvent

                val isTrackpad = maxOf(abs(dx), abs(dy)) < 1.0f
                if (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed) {
                    val mx = change.position.x
                    val my = change.position.y
                    val imgX = (mx - panX) / zoom
                    val imgY = (my - panY) / zoom
                    val newZoom = (zoom * exp(-dy * 0.10f)).coerceIn(0.05f, 20f)
                    onZoomChange(newZoom)
                    onPanChange(mx - imgX * newZoom, my - imgY * newZoom)
                } else if (isTrackpad) {
                    val mult = 1.5f
                    onPanChange(panX - dx * mult, panY - dy * mult)
                } else {
                    val mx = change.position.x
                    val my = change.position.y
                    val imgX = (mx - panX) / zoom
                    val imgY = (my - panY) / zoom
                    val newZoom = (zoom * exp(-dy * 0.12f)).coerceIn(0.05f, 20f)
                    onZoomChange(newZoom)
                    onPanChange(mx - imgX * newZoom, my - imgY * newZoom)
                }
            }
            .pointerInput(numCols, numRows, tw, th, ox, oy) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val isPan = (startEvent.buttons.isSecondaryPressed || startEvent.buttons.isTertiaryPressed) &&
                            !startEvent.buttons.isPrimaryPressed

                    if (!isPan) onPaintAt(down.position.x, down.position.y)

                    var prev = down.position
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        val delta = ch.position - prev
                        prev = ch.position
                        if (isPan) {
                            onPanChange(panX + delta.x, panY + delta.y)
                        } else {
                            onPaintAt(ch.position.x, ch.position.y)
                        }
                    }
                }
            },
    ) {
        val bmp = sourceImageBitmap

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (bmp != null) {
                val dstW = (imgNativeW * zoom).toInt().coerceAtLeast(1)
                val dstH = (imgNativeH * zoom).toInt().coerceAtLeast(1)
                drawImage(
                    image         = bmp,
                    dstOffset     = IntOffset(panX.toInt(), panY.toInt()),
                    dstSize       = IntSize(dstW, dstH),
                    filterQuality = FilterQuality.Low,
                )
            }

            paintedTiles.forEach { (pos, type) ->
                val color = tileTypeColor(type) ?: return@forEach
                val sx = panX + (ox + pos.first  * tw) * zoom
                val sy = panY + (oy + pos.second * th) * zoom
                drawRect(
                    color   = color.copy(alpha = 0.50f),
                    topLeft = Offset(sx, sy),
                    size    = Size(tw * zoom, th * zoom),
                )
            }

            drawGrid(ox, oy, tw, th, numCols, numRows, zoom, panX, panY, bmp != null)
        }

        if (bmp == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🗺", fontSize = 40.sp)
                Text("Load a map image to begin", color = DC.Subtext0, fontSize = 14.sp)
                Text("PNG or JPG — any resolution", color = DC.Overlay0, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Left-click/drag  →  paint terrain\nRight-drag  →  pan\nScroll  →  pan  •  Ctrl+scroll  →  zoom",
                    color = DC.Overlay0, fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }

        if (sourceImageBitmap != null) {
            Text(
                "${(zoom * 100).roundToInt()}%  |  ${numCols}×${numRows} tiles  |  ${paintedTiles.size} painted",
                color = DC.Overlay0.copy(alpha = 0.8f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

// ── Canvas draw helpers ───────────────────────────────────────────────────────

private fun DrawScope.drawGrid(
    ox: Int, oy: Int,
    tw: Int, th: Int,
    cols: Int, rows: Int,
    zoom: Float,
    panX: Float, panY: Float,
    hasImage: Boolean,
) {
    val gridAlpha = if (hasImage) 0.45f else 0.20f
    val lineColor = Color.White.copy(alpha = gridAlpha)
    val originColor = Color(0xFFF9E2AF).copy(alpha = 0.5f)

    for (c in 0..cols) {
        val x = panX + (ox + c * tw) * zoom
        val yStart = panY + oy * zoom
        val yEnd   = panY + (oy + rows * th) * zoom
        if (x < -1f || x > size.width + 1f) continue
        drawLine(lineColor, Offset(x, yStart.coerceAtLeast(0f)), Offset(x, yEnd.coerceAtMost(size.height)), 0.8f)
    }
    for (r in 0..rows) {
        val y = panY + (oy + r * th) * zoom
        val xStart = panX + ox * zoom
        val xEnd   = panX + (ox + cols * tw) * zoom
        if (y < -1f || y > size.height + 1f) continue
        drawLine(lineColor, Offset(xStart.coerceAtLeast(0f), y), Offset(xEnd.coerceAtMost(size.width), y), 0.8f)
    }

    if (ox > 0 || oy > 0) {
        val gx = panX + ox * zoom
        val gy = panY + oy * zoom
        val gw = cols * tw * zoom
        val gh = rows * th * zoom
        drawRect(
            color   = originColor,
            topLeft = Offset(gx, gy),
            size    = Size(gw, gh),
            style   = Stroke(width = 1.5f),
        )
    }
}
