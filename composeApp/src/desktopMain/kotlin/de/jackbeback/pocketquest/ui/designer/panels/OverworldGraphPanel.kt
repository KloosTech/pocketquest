package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.map.TileType
import de.jackbeback.pocketquest.designer.model.OverworldDef
import de.jackbeback.pocketquest.designer.model.OverworldEdgeDef
import de.jackbeback.pocketquest.designer.model.OverworldNodeDef
import de.jackbeback.pocketquest.designer.model.OverworldNodeType
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.GraphInteractionMode
import de.jackbeback.pocketquest.ui.designer.GraphSelectionKind
import de.jackbeback.pocketquest.ui.designer.GraphSelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*

private const val ZOOM_MIN = 0.3f
private const val ZOOM_MAX = 4f
private const val NODE_RADIUS_BASE = 26f

private fun tileTypeColorForGraph(type: TileType): Color = when (type) {
    TileType.FLOOR             -> Color(0xFF3D3D52)
    TileType.WALL              -> Color(0xFF808080)
    TileType.WATER             -> Color(0xFF4090FF)
    TileType.COVER_LOW         -> Color(0xFF90FF90)
    TileType.COVER_HIGH        -> Color(0xFF00C040)
    TileType.DIFFICULT_TERRAIN -> Color(0xFFFFB020)
    TileType.HAZARD            -> Color(0xFFFF3030)
    TileType.VOID              -> Color(0xFF101010)
}

private sealed class ContextMenuState {
    object Hidden : ContextMenuState()
    data class OnCanvas(val canvasPos: Offset) : ContextMenuState()
    data class OnNode(val canvasPos: Offset, val node: OverworldNodeDef) : ContextMenuState()
    data class OnEdge(val canvasPos: Offset, val edge: OverworldEdgeDef) : ContextMenuState()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OverworldGraphPanel(
    overworld: OverworldDef,
    backgroundMap: TileMap?,
    interactionMode: GraphInteractionMode,
    nodeTypeToPlace: OverworldNodeType,
    selection: GraphSelectionState,
    edgePendingFromId: String?,
    onPlaceNode: (type: OverworldNodeType, x: Double, y: Double) -> Unit,
    onMoveNode: (nodeId: String, x: Double, y: Double) -> Unit,
    onSelectNode: (nodeId: String) -> Unit,
    onSelectEdge: (fromId: String, toId: String) -> Unit,
    onDeleteNode: (nodeId: String) -> Unit,
    onDeleteEdge: (fromId: String, toId: String) -> Unit,
    onBeginEdge: (fromNodeId: String) -> Unit,
    onCompleteEdge: (toNodeId: String) -> Unit,
    onClearSelection: () -> Unit,
    onChangeNodeType: (nodeId: String, type: OverworldNodeType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingNodeId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val latestSelection by rememberUpdatedState(selection)
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    var showTileSprites by remember { mutableStateOf(false) }
    var tileCache by remember { mutableStateOf<Map<Pair<Int, Int>, ImageBitmap>>(emptyMap()) }
    var tilesLoading by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var contextMenu by remember { mutableStateOf<ContextMenuState>(ContextMenuState.Hidden) }
    var rightClickDownPos by remember { mutableStateOf<Offset?>(null) }
    var panDragLastPos by remember { mutableStateOf<Offset?>(null) }

    fun nodeRadius() = (NODE_RADIUS_BASE * zoom).coerceIn(8f, 56f)

    fun nodeCanvasPos(node: OverworldNodeDef): Offset = Offset(
        x = panX + (node.x * canvasSize.width).toFloat() * zoom,
        y = panY + (node.y * canvasSize.height).toFloat() * zoom,
    )

    fun effectiveCanvasPos(node: OverworldNodeDef): Offset =
        nodeCanvasPos(node) + if (node.id == draggingNodeId) dragOffset else Offset.Zero

    fun toNormalized(canvasPos: Offset): Pair<Double, Double> {
        val nx = ((canvasPos.x - panX) / (canvasSize.width * zoom)).toDouble().coerceIn(0.0, 1.0)
        val ny = ((canvasPos.y - panY) / (canvasSize.height * zoom)).toDouble().coerceIn(0.0, 1.0)
        return nx to ny
    }

    fun hitNode(pos: Offset): OverworldNodeDef? {
        val r = nodeRadius()
        return overworld.nodes.firstOrNull { node ->
            val cp = nodeCanvasPos(node)
            (pos - cp).getDistance() < r
        }
    }

    fun hitEdge(pos: Offset): OverworldEdgeDef? {
        val threshold = 8f
        return overworld.edges.firstOrNull { edge ->
            val fromNode = overworld.nodes.find { it.id == edge.fromId } ?: return@firstOrNull false
            val toNode = overworld.nodes.find { it.id == edge.toId } ?: return@firstOrNull false
            val from = nodeCanvasPos(fromNode)
            val to = nodeCanvasPos(toNode)
            val dx = to.x - from.x
            val dy = to.y - from.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1f) return@firstOrNull false
            val t = ((pos.x - from.x) * dx + (pos.y - from.y) * dy) / (len * len)
            if (t < 0f || t > 1f) return@firstOrNull false
            val projX = from.x + t * dx
            val projY = from.y + t * dy
            val dist = sqrt((pos.x - projX).pow(2) + (pos.y - projY).pow(2))
            dist < threshold
        }
    }

    // Tile-loading LaunchedEffect
    LaunchedEffect(backgroundMap?.id, showTileSprites) {
        if (!showTileSprites || backgroundMap == null) { tileCache = emptyMap(); return@LaunchedEffect }
        tilesLoading = true
        tileCache = withContext(Dispatchers.IO) {
            val resDir = defaultResourcesDir() ?: return@withContext emptyMap()
            val tilesDir = File(resDir, "files/tiles/${backgroundMap.id}")
            if (!tilesDir.exists()) return@withContext emptyMap()
            buildMap {
                for (tile in backgroundMap.tiles) {
                    val file = File(tilesDir, "${tile.col}-${tile.row}.png")
                    if (!file.exists()) continue
                    runCatching {
                        val bytes = ByteArrayOutputStream()
                            .also { ImageIO.write(ImageIO.read(file), "png", it) }.toByteArray()
                        put(
                            tile.col to tile.row,
                            Bitmap.makeFromImage(Image.makeFromEncoded(bytes)).asComposeImageBitmap(),
                        )
                    }
                }
            }
        }
        tilesLoading = false
    }

    fun fitNodes() {
        if (overworld.nodes.isEmpty() || canvasSize.width == 0) return
        val xs = overworld.nodes.map { it.x.toFloat() }
        val ys = overworld.nodes.map { it.y.toFloat() }
        val pad = { range: Float -> range.coerceAtLeast(0.1f) * 0.20f }
        val minX = xs.min() - pad(xs.max() - xs.min())
        val maxX = xs.max() + pad(xs.max() - xs.min())
        val minY = ys.min() - pad(ys.max() - ys.min())
        val maxY = ys.max() + pad(ys.max() - ys.min())
        val zoomX = 1f / (maxX - minX).coerceAtLeast(0.01f)
        val zoomY = (canvasSize.height.toFloat() / canvasSize.width) / (maxY - minY).coerceAtLeast(0.01f)
        zoom = minOf(zoomX, zoomY).coerceIn(ZOOM_MIN, ZOOM_MAX)
        panX = (canvasSize.width  / 2f) - ((minX + maxX) / 2f) * canvasSize.width  * zoom
        panY = (canvasSize.height / 2f) - ((minY + maxY) / 2f) * canvasSize.height * zoom
    }

    Box(
        modifier = modifier
            .background(DC.Background)
            .onSizeChanged { canvasSize = it }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Delete, Key.Backspace -> when (selection.kind) {
                        GraphSelectionKind.NODE -> { selection.nodeId?.let(onDeleteNode); true }
                        GraphSelectionKind.EDGE -> {
                            val f = selection.edgeFromId; val t = selection.edgeToId
                            if (f != null && t != null) onDeleteEdge(f, t); true
                        }
                        else -> false
                    }
                    Key.Escape -> {
                        if (contextMenu != ContextMenuState.Hidden) {
                            contextMenu = ContextMenuState.Hidden; true
                        } else {
                            onClearSelection(); true
                        }
                    }
                    else -> false
                }
            },
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // ── Canvas (draw layers 1-3) ───────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(interactionMode, overworld, edgePendingFromId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPos = down.position
                        var lastPos = downPos
                        var hasMoved = false
                        val hitAtDown = hitNode(downPos)

                        when (interactionMode) {
                            GraphInteractionMode.SELECT -> {
                                if (hitAtDown != null) {
                                    val alreadySelected = latestSelection.kind == GraphSelectionKind.NODE &&
                                            latestSelection.nodeId == hitAtDown.id
                                    onSelectNode(hitAtDown.id)
                                    if (alreadySelected) draggingNodeId = hitAtDown.id
                                } else {
                                    onClearSelection()
                                }
                            }
                            GraphInteractionMode.ADD_NODE -> { /* handled on up */ }
                            GraphInteractionMode.ADD_EDGE -> {
                                if (hitAtDown != null) {
                                    if (edgePendingFromId == null) onBeginEdge(hitAtDown.id)
                                    else onCompleteEdge(hitAtDown.id)
                                }
                            }
                            GraphInteractionMode.DELETE -> { /* handled on up */ }
                        }

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (change.positionChanged()) {
                                val delta = change.position - lastPos
                                if (delta.getDistance() > 3f) hasMoved = true
                                hoverPos = change.position

                                val dragId = draggingNodeId
                                if (interactionMode == GraphInteractionMode.SELECT && dragId != null && hasMoved) {
                                    dragOffset += delta
                                    change.consume()
                                }
                                lastPos = change.position
                            }

                            if (!change.pressed) {
                                when (interactionMode) {
                                    GraphInteractionMode.SELECT -> {
                                        val dragId = draggingNodeId
                                        if (hasMoved && dragId != null) {
                                            val node = overworld.nodes.find { it.id == dragId }
                                            if (node != null) {
                                                val finalPos = nodeCanvasPos(node) + dragOffset
                                                val (nx, ny) = toNormalized(finalPos)
                                                onMoveNode(dragId, nx, ny)
                                            }
                                        }
                                        draggingNodeId = null
                                        dragOffset = Offset.Zero
                                    }
                                    GraphInteractionMode.ADD_NODE -> {
                                        if (!hasMoved) {
                                            val (nx, ny) = toNormalized(downPos)
                                            onPlaceNode(nodeTypeToPlace, nx, ny)
                                        }
                                    }
                                    GraphInteractionMode.DELETE -> {
                                        if (!hasMoved) {
                                            val node = hitNode(downPos)
                                            val edge = hitEdge(downPos)
                                            when {
                                                node != null -> onDeleteNode(node.id)
                                                edge != null -> onDeleteEdge(edge.fromId, edge.toId)
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                                break
                            }
                        } while (true)
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                    val dx = change.scrollDelta.x
                    val dy = change.scrollDelta.y
                    if (dx == 0f && dy == 0f) return@onPointerEvent

                    // Trackpad produces fractional deltas < 1.0; mouse wheel produces discrete >= 1.0
                    val isTrackpad = maxOf(abs(dx), abs(dy)) < 1.0f

                    if (event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed) {
                        // Ctrl/Meta + scroll = zoom for both devices
                        val mousePos = change.position
                        val zoom_ = zoom
                        val newZoom = (zoom_ * exp(-dy * 0.10f)).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        val zoomRatio = newZoom / zoom_
                        panX = mousePos.x + (panX - mousePos.x) * zoomRatio
                        panY = mousePos.y + (panY - mousePos.y) * zoomRatio
                        zoom = newZoom
                    } else if (isTrackpad) {
                        // Trackpad two-finger swipe = pan
                        val mult = 1.5f
                        panX -= dx * mult
                        panY -= dy * mult
                    } else {
                        // Mouse scroll wheel = zoom at cursor
                        val mousePos = change.position
                        val zoom_ = zoom
                        val newZoom = (zoom_ * exp(-dy * 0.12f)).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        val zoomRatio = newZoom / zoom_
                        panX = mousePos.x + (panX - mousePos.x) * zoomRatio
                        panY = mousePos.y + (panY - mousePos.y) * zoomRatio
                        zoom = newZoom
                    }
                }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val pos = event.changes.firstOrNull()?.position
                    val last = panDragLastPos
                    if (last != null && pos != null &&
                        (event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed)) {
                        panX += pos.x - last.x
                        panY += pos.y - last.y
                        panDragLastPos = pos
                    }
                    hoverPos = pos
                }
                .onPointerEvent(PointerEventType.Press) { event ->
                    val pos = event.changes.firstOrNull()?.position
                    if (event.button == PointerButton.Secondary) {
                        rightClickDownPos = pos
                        panDragLastPos = pos
                    } else if (event.button == PointerButton.Tertiary) {
                        panDragLastPos = pos
                    }
                }
                .onPointerEvent(PointerEventType.Release) { event ->
                    if (event.button == PointerButton.Secondary) {
                        val downPos = rightClickDownPos ?: return@onPointerEvent
                        val upPos   = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                        rightClickDownPos = null
                        panDragLastPos = null
                        if ((upPos - downPos).getDistance() < 4f) {
                            val hitN = hitNode(upPos)
                            val hitE = hitEdge(upPos)
                            contextMenu = when {
                                hitN != null -> ContextMenuState.OnNode(upPos, hitN)
                                hitE != null -> ContextMenuState.OnEdge(upPos, hitE)
                                else         -> ContextMenuState.OnCanvas(upPos)
                            }
                        }
                    } else if (event.button == PointerButton.Tertiary) {
                        panDragLastPos = null
                    }
                },
        ) {
            // ── Layer 1: Background ───────────────────────────────────────────
            if (backgroundMap != null) {
                drawBackgroundMap(backgroundMap, panX, panY, zoom, canvasSize, tileCache)
            } else {
                drawHexGrid(panX, panY, zoom, canvasSize)
            }

            // ── Layer 2: Edges ────────────────────────────────────────────────
            overworld.edges.forEach { edge ->
                val fromNode = overworld.nodes.find { it.id == edge.fromId } ?: return@forEach
                val toNode = overworld.nodes.find { it.id == edge.toId } ?: return@forEach
                val from = effectiveCanvasPos(fromNode)
                val to = effectiveCanvasPos(toNode)
                val isSelected = selection.kind == GraphSelectionKind.EDGE &&
                        selection.edgeFromId == edge.fromId && selection.edgeToId == edge.toId
                val color   = if (isSelected) DC.Blue else DC.Overlay0.copy(alpha = 0.88f)
                val strokeW = if (isSelected) 3.5f else 2.5f
                // Shadow pass
                drawLine(Color.Black.copy(alpha = 0.35f), from + Offset(1.5f, 1.5f), to + Offset(1.5f, 1.5f), strokeW)
                // Main edge
                drawLine(color, from, to, strokeW)
                drawArrowhead(from, to, color, strokeW)
            }

            // Pending edge preview
            val pendingFromNode = edgePendingFromId?.let { id -> overworld.nodes.find { it.id == id } }
            val hPos = hoverPos
            if (pendingFromNode != null && hPos != null) {
                val from = effectiveCanvasPos(pendingFromNode)
                drawLine(
                    color = DC.Primary.copy(alpha = 0.7f),
                    start = from,
                    end = hPos,
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }

            // ── Layer 3: Nodes ────────────────────────────────────────────────
            overworld.nodes.forEach { node ->
                val cp = effectiveCanvasPos(node)
                val r = nodeRadius()
                val isSelected = selection.kind == GraphSelectionKind.NODE && selection.nodeId == node.id

                // Glow ring
                val glowColor = when (node.type) {
                    OverworldNodeType.START  -> DC.Green
                    OverworldNodeType.BATTLE -> DC.Blue
                    OverworldNodeType.REST   -> DC.Yellow
                    OverworldNodeType.BOSS   -> DC.Red
                }
                val glowRadius = if (node.type == OverworldNodeType.BOSS) r * 1.3f + 6f else r + 6f
                drawCircle(glowColor.copy(alpha = 0.18f), radius = glowRadius, center = cp)

                // Dark backdrop (contrast)
                val backdropR = if (node.type == OverworldNodeType.BOSS) r * 1.3f else r
                val isDragging = node.id == draggingNodeId && dragOffset != Offset.Zero
                if (isDragging) {
                    drawCircle(Color.Black.copy(alpha = 0.45f), radius = backdropR + 3f, center = cp + Offset(4f, 6f))
                }
                drawCircle(DC.Crust.copy(alpha = 0.80f), radius = backdropR, center = cp)

                when (node.type) {
                    OverworldNodeType.START -> {
                        drawCircle(color = DC.Green.copy(alpha = 0.40f), radius = r, center = cp)
                        drawCircle(color = DC.Green, radius = r, center = cp, style = Stroke(2.5f))
                    }
                    OverworldNodeType.BATTLE -> {
                        drawPentagon(cp, r, DC.Blue.copy(alpha = 0.40f), DC.Sapphire, 2.5f)
                    }
                    OverworldNodeType.REST -> {
                        drawCircle(color = DC.Yellow.copy(alpha = 0.40f), radius = r, center = cp)
                        drawCircle(color = DC.Yellow, radius = r, center = cp, style = Stroke(2f))
                    }
                    OverworldNodeType.BOSS -> {
                        drawOctagon(cp, r * 1.3f, DC.Red.copy(alpha = 0.40f), DC.Red, 3f)
                    }
                }

                // Validation badge (canvas circle)
                val needsBadge = (node.type == OverworldNodeType.BATTLE || node.type == OverworldNodeType.BOSS)
                        && node.encounterId == null
                if (needsBadge) {
                    val effectiveR = if (node.type == OverworldNodeType.BOSS) r * 1.3f else r
                    val bc = Offset(cp.x + effectiveR * 0.70f, cp.y - effectiveR * 0.70f)
                    drawCircle(DC.Peach, radius = 7f, center = bc)
                    drawCircle(DC.Crust, radius = 7f, center = bc, style = Stroke(1.5f))
                }

                if (isSelected) {
                    drawCircle(
                        color = DC.Primary,
                        radius = r + 8f,
                        center = cp,
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                        ),
                    )
                }
            }
        }

        // ── Node glyphs (Text overlays) ───────────────────────────────────────
        overworld.nodes.forEach { node ->
            val cp = effectiveCanvasPos(node)
            val r = nodeRadius()
            val glyph = when (node.type) {
                OverworldNodeType.START  -> "▶"
                OverworldNodeType.BATTLE -> "⚔"
                OverworldNodeType.REST   -> "⛺"
                OverworldNodeType.BOSS   -> "☠"
            }
            Text(
                text = glyph,
                fontSize = (12f * zoom.coerceIn(0.5f, 2f)).sp,
                color = Color.White,
                modifier = Modifier.absoluteOffset {
                    IntOffset((cp.x - 8 * zoom).toInt(), (cp.y - 8 * zoom).toInt())
                },
            )

            // Node label pill
            val labelFontSize = (9f * zoom.coerceIn(0.5f, 1.5f)).sp
            Box(
                modifier = Modifier
                    .absoluteOffset {
                        IntOffset((cp.x - 30 * zoom).toInt(), (cp.y + r + 4 * zoom).toInt())
                    }
                    .background(DC.Crust.copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = node.label,
                    fontSize = labelFontSize,
                    color = DC.Subtext1,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Validation badge "!" text overlay
            val needsBadge = (node.type == OverworldNodeType.BATTLE || node.type == OverworldNodeType.BOSS)
                    && node.encounterId == null
            if (needsBadge) {
                val effectiveR = if (node.type == OverworldNodeType.BOSS) r * 1.3f else r
                val bc = Offset(cp.x + effectiveR * 0.70f, cp.y - effectiveR * 0.70f)
                Text(
                    text = "!",
                    fontSize = 8.sp,
                    color = DC.Crust,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.absoluteOffset {
                        IntOffset((bc.x - 3).toInt(), (bc.y - 7).toInt())
                    },
                )
            }
        }

        // ── Layer 4: Minimap ──────────────────────────────────────────────────
        if (canvasSize.width > 200 && overworld.nodes.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(120.dp, 80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DC.Crust)
                    .border(1.dp, DC.Surface1, RoundedCornerShape(6.dp)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val mW = size.width
                    val mH = size.height

                    overworld.edges.forEach { edge ->
                        val fromN = overworld.nodes.find { it.id == edge.fromId } ?: return@forEach
                        val toN = overworld.nodes.find { it.id == edge.toId } ?: return@forEach
                        drawLine(
                            color = DC.Overlay0.copy(alpha = 0.5f),
                            start = Offset(fromN.x.toFloat() * mW, fromN.y.toFloat() * mH),
                            end = Offset(toN.x.toFloat() * mW, toN.y.toFloat() * mH),
                            strokeWidth = 1f,
                        )
                    }
                    overworld.nodes.forEach { node ->
                        val dotColor = when (node.type) {
                            OverworldNodeType.START  -> DC.Green
                            OverworldNodeType.BATTLE -> DC.Blue
                            OverworldNodeType.REST   -> DC.Yellow
                            OverworldNodeType.BOSS   -> DC.Red
                        }
                        drawCircle(
                            color = dotColor,
                            radius = 3f,
                            center = Offset(node.x.toFloat() * mW, node.y.toFloat() * mH),
                        )
                    }

                    // Viewport rect
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        val vpX0 = (-panX / (canvasSize.width * zoom)).coerceIn(0f, 1f)
                        val vpY0 = (-panY / (canvasSize.height * zoom)).coerceIn(0f, 1f)
                        val vpW = (1f / zoom).coerceIn(0f, 1f)
                        val vpH = (1f / zoom).coerceIn(0f, 1f)
                        drawRect(
                            color = DC.Primary.copy(alpha = 0.25f),
                            topLeft = Offset(vpX0 * mW, vpY0 * mH),
                            size = Size(vpW * mW, vpH * mH),
                            style = Stroke(1f),
                        )
                    }
                }
            }
        }

        // ── Bottom-left overlay (Fit + Tiles chips + stats) ───────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Fit chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DC.Surface0.copy(alpha = 0.85f))
                        .border(1.dp, DC.Overlay0, RoundedCornerShape(4.dp))
                        .clickable { fitNodes() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("⊡ Fit", color = DC.Subtext1, fontSize = 10.sp)
                }

                // Tiles chip — only when a background map is loaded
                if (backgroundMap != null) {
                    val tilesLabel = when {
                        tilesLoading    -> "⌛ Tiles…"
                        showTileSprites -> "🗺 Tiles ✓"
                        else            -> "🗺 Tiles"
                    }
                    val tilesActive = showTileSprites && !tilesLoading
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (tilesActive) DC.Blue.copy(alpha = 0.20f) else DC.Surface0.copy(alpha = 0.85f))
                            .border(1.dp, if (tilesActive) DC.Blue else DC.Overlay0, RoundedCornerShape(4.dp))
                            .clickable { showTileSprites = !showTileSprites }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(tilesLabel, color = if (tilesActive) DC.Blue else DC.Subtext1, fontSize = 10.sp)
                    }
                }
            }

            // Stats pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(DC.Crust.copy(alpha = 0.80f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                val statsText = buildString {
                    append("${overworld.nodes.size} nodes • ${overworld.edges.size} edges")
                    backgroundMap?.let { append(" • ${it.name}") }
                }
                Text(statsText, color = DC.Overlay1, fontSize = 9.sp)
            }
        }

        // ── Right-click context menu ──────────────────────────────────────────
        val cm = contextMenu
        if (cm !is ContextMenuState.Hidden) {
            val pos = when (cm) {
                is ContextMenuState.OnCanvas -> cm.canvasPos
                is ContextMenuState.OnNode   -> cm.canvasPos
                is ContextMenuState.OnEdge   -> cm.canvasPos
                is ContextMenuState.Hidden   -> Offset.Zero
            }
            Popup(
                alignment  = Alignment.TopStart,
                offset     = IntOffset(pos.x.toInt(), pos.y.toInt()),
                onDismissRequest = { contextMenu = ContextMenuState.Hidden },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .background(DC.Surface0, RoundedCornerShape(6.dp))
                        .border(1.dp, DC.Surface1, RoundedCornerShape(6.dp))
                        .padding(6.dp)
                        .onKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                                contextMenu = ContextMenuState.Hidden; true
                            } else false
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when (cm) {
                        is ContextMenuState.OnCanvas -> {
                            CtxMenuLabel("PLACE NODE")
                            OverworldNodeType.values().forEach { t ->
                                CtxMenuTypeButton(t, isSelected = t == nodeTypeToPlace) {
                                    val (nx, ny) = toNormalized(cm.canvasPos)
                                    onPlaceNode(t, nx, ny)
                                    contextMenu = ContextMenuState.Hidden
                                }
                            }
                        }
                        is ContextMenuState.OnNode -> {
                            CtxMenuLabel("CHANGE TYPE")
                            OverworldNodeType.values().forEach { t ->
                                CtxMenuTypeButton(t, isSelected = t == cm.node.type) {
                                    onChangeNodeType(cm.node.id, t)
                                    contextMenu = ContextMenuState.Hidden
                                }
                            }
                            CtxMenuDivider()
                            CtxMenuActionButton("Delete Node", DC.Red) {
                                onDeleteNode(cm.node.id)
                                contextMenu = ContextMenuState.Hidden
                            }
                        }
                        is ContextMenuState.OnEdge -> {
                            CtxMenuActionButton("Delete Edge", DC.Red) {
                                onDeleteEdge(cm.edge.fromId, cm.edge.toId)
                                contextMenu = ContextMenuState.Hidden
                            }
                        }
                        is ContextMenuState.Hidden -> {}
                    }
                }
            }
        }

        // ── Interaction mode hint ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DC.Mantle.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val hint = when (interactionMode) {
                GraphInteractionMode.SELECT -> "Click to select • Drag to move • Right-click = menu • Mid/Right-drag or trackpad-swipe to pan • Scroll = zoom • Del = delete"
                GraphInteractionMode.ADD_NODE -> "Click canvas to place ${nodeTypeToPlace.name} node • Scroll = pan • Ctrl+scroll = zoom"
                GraphInteractionMode.ADD_EDGE -> if (edgePendingFromId == null) "Click source node" else "Click target node"
                GraphInteractionMode.DELETE -> "Click node or edge to delete"
            }
            Text(hint, color = DC.Overlay1, fontSize = 10.sp)
        }
    }
}

// ── Draw helpers ──────────────────────────────────────────────────────────────

private fun DrawScope.drawArrowhead(from: Offset, to: Offset, color: Color, strokeW: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 20f) return
    val ux = dx / len
    val uy = dy / len
    val arrowLen = 10f
    val arrowAngle = (25f * PI / 180f).toFloat()
    val tip = to
    val p1 = Offset(
        tip.x - arrowLen * (ux * cos(arrowAngle) - uy * sin(arrowAngle)),
        tip.y - arrowLen * (ux * sin(arrowAngle) + uy * cos(arrowAngle)),
    )
    val p2 = Offset(
        tip.x - arrowLen * (ux * cos(-arrowAngle) - uy * sin(-arrowAngle)),
        tip.y - arrowLen * (ux * sin(-arrowAngle) + uy * cos(-arrowAngle)),
    )
    drawLine(color = color, start = tip, end = p1, strokeWidth = strokeW)
    drawLine(color = color, start = tip, end = p2, strokeWidth = strokeW)
}

private fun DrawScope.drawPentagon(center: Offset, r: Float, fill: Color, stroke: Color, strokeW: Float) {
    val path = Path()
    for (i in 0 until 5) {
        val angle = (i * 72f - 90f) * PI.toFloat() / 180f
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(strokeW))
}

private fun DrawScope.drawOctagon(center: Offset, r: Float, fill: Color, stroke: Color, strokeW: Float) {
    val path = Path()
    for (i in 0 until 8) {
        val angle = (i * 45f - 22.5f) * PI.toFloat() / 180f
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(strokeW))
}

private fun DrawScope.drawBackgroundMap(
    map: TileMap, panX: Float, panY: Float, zoom: Float, canvasSize: IntSize,
    tileSprites: Map<Pair<Int, Int>, ImageBitmap> = emptyMap(),
) {
    val tileW = (map.tileWidthPx * zoom)
    val tileH = (map.tileHeightPx * zoom)
    if (tileW < 0.5f || tileH < 0.5f) return

    val tileTypeMap = map.tiles.associate { (it.col to it.row) to it.type }

    for (r in 0 until map.rows) {
        for (c in 0 until map.cols) {
            val type = tileTypeMap[c to r] ?: TileType.FLOOR
            val left = panX + c * tileW
            val top  = panY + r * tileH
            if (left + tileW < 0 || top + tileH < 0 || left > canvasSize.width || top > canvasSize.height) continue
            val sprite = tileSprites[c to r]
            if (sprite != null) {
                drawImage(
                    image = sprite,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize   = IntSize(tileW.toInt().coerceAtLeast(1), tileH.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Low,
                )
            } else {
                drawRect(tileTypeColorForGraph(type).copy(alpha = 0.12f), Offset(left, top), Size(tileW, tileH))
            }
        }
    }
    // Grid lines
    for (r in 0..map.rows) {
        val y = panY + r * tileH
        drawLine(DC.Overlay0.copy(alpha = 0.05f), Offset(panX, y), Offset(panX + map.cols * tileW, y), 0.5f)
    }
    for (c in 0..map.cols) {
        val x = panX + c * tileW
        drawLine(DC.Overlay0.copy(alpha = 0.05f), Offset(x, panY), Offset(x, panY + map.rows * tileH), 0.5f)
    }
}

private fun DrawScope.drawHexGrid(panX: Float, panY: Float, zoom: Float, canvasSize: IntSize) {
    val spacing = 40f * zoom
    val hexR = spacing * 0.5f
    val cols = (canvasSize.width / spacing).toInt() + 3
    val rows = (canvasSize.height / (spacing * 0.866f)).toInt() + 3
    val offCol = ((-panX) / spacing).toInt() - 1
    val offRow = ((-panY) / (spacing * 0.866f)).toInt() - 1

    for (r in offRow until offRow + rows) {
        for (c in offCol until offCol + cols) {
            val cx = panX + c * spacing + if (r % 2 == 0) 0f else spacing * 0.5f
            val cy = panY + r * spacing * 0.866f
            if (cx < -hexR || cy < -hexR || cx > canvasSize.width + hexR || cy > canvasSize.height + hexR) continue
            val path = Path()
            for (i in 0 until 6) {
                val angle = (i * 60f) * PI.toFloat() / 180f
                val px = cx + hexR * cos(angle)
                val py = cy + hexR * sin(angle)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, DC.Surface0.copy(alpha = 0.12f))
            drawPath(path, DC.Overlay0.copy(alpha = 0.08f), style = Stroke(0.8f))
        }
    }
}

@Composable
private fun CtxMenuLabel(text: String) {
    Text(text, color = DC.Overlay0, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun CtxMenuDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(DC.Surface1))
}

@Composable
private fun CtxMenuTypeButton(type: OverworldNodeType, isSelected: Boolean, onClick: () -> Unit) {
    val (label, color) = when (type) {
        OverworldNodeType.START  -> "▶ Start"  to DC.Green
        OverworldNodeType.BATTLE -> "⚔ Battle" to DC.Blue
        OverworldNodeType.REST   -> "⛺ Rest"   to DC.Yellow
        OverworldNodeType.BOSS   -> "☠ Boss"   to DC.Red
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (isSelected) color else DC.Subtext1, fontSize = 11.sp)
        if (isSelected) { Spacer(Modifier.weight(1f)); Text("✓", color = color, fontSize = 10.sp) }
    }
}

@Composable
private fun CtxMenuActionButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp)
    }
}

private fun defaultResourcesDir(): File? {
    var dir = File(System.getProperty("user.dir"))
    repeat(4) {
        val candidate = File(dir, "composeApp/src/commonMain/composeResources")
        if (candidate.exists()) return candidate
        dir = dir.parentFile ?: return null
    }
    return null
}
