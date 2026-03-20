package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import de.jackbeback.pocketquest.designer.model.OverworldDef
import de.jackbeback.pocketquest.designer.model.OverworldEdgeDef
import de.jackbeback.pocketquest.designer.model.OverworldNodeDef
import de.jackbeback.pocketquest.designer.model.OverworldNodeType
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.GraphInteractionMode
import de.jackbeback.pocketquest.ui.designer.GraphSelectionKind
import de.jackbeback.pocketquest.ui.designer.GraphSelectionState
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
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingNodeId by remember { mutableStateOf<String?>(null) }
    var hoverPos by remember { mutableStateOf<Offset?>(null) }

    fun nodeRadius() = (NODE_RADIUS_BASE * zoom).coerceIn(8f, 56f)

    fun nodeCanvasPos(node: OverworldNodeDef): Offset = Offset(
        x = panX + (node.x * canvasSize.width).toFloat() * zoom,
        y = panY + (node.y * canvasSize.height).toFloat() * zoom,
    )

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

    Box(
        modifier = modifier
            .background(DC.Background)
            .onSizeChanged { canvasSize = it },
    ) {
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
                                    // Potentially drag
                                    onSelectNode(hitAtDown.id)
                                    draggingNodeId = hitAtDown.id
                                }
                            }
                            GraphInteractionMode.ADD_NODE -> { /* handled on up */ }
                            GraphInteractionMode.ADD_EDGE -> {
                                if (hitAtDown != null) {
                                    if (edgePendingFromId == null) {
                                        onBeginEdge(hitAtDown.id)
                                    } else {
                                        onCompleteEdge(hitAtDown.id)
                                    }
                                }
                            }
                            GraphInteractionMode.DELETE -> { /* handled on up */ }
                        }

                        var isRightDown = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            // Right drag = pan
                            if (event.buttons.isSecondaryPressed) {
                                if (!isRightDown) { isRightDown = true; lastPos = change.position }
                                val delta = change.position - lastPos
                                panX += delta.x
                                panY += delta.y
                                lastPos = change.position
                                change.consume()
                                continue
                            }

                            if (change.positionChanged()) {
                                val delta = change.position - lastPos
                                if (delta.getDistance() > 3f) hasMoved = true
                                hoverPos = change.position

                                // Drag selected node in SELECT mode
                                val dragId = draggingNodeId
                                if (interactionMode == GraphInteractionMode.SELECT && dragId != null && hasMoved) {
                                    val (nx, ny) = toNormalized(change.position)
                                    onMoveNode(dragId, nx, ny)
                                }
                                lastPos = change.position
                            }

                            if (!change.pressed) {
                                // pointer up
                                when (interactionMode) {
                                    GraphInteractionMode.SELECT -> {
                                        if (!hasMoved) {
                                            val node = hitNode(downPos)
                                            val edge = hitEdge(downPos)
                                            when {
                                                node != null -> onSelectNode(node.id)
                                                edge != null -> onSelectEdge(edge.fromId, edge.toId)
                                                else -> onClearSelection()
                                            }
                                        }
                                        draggingNodeId = null
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
                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                    val factor = if (scrollDelta < 0) 1.12f else 1f / 1.12f
                    val mousePos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    val newZoom = (zoom * factor).coerceIn(ZOOM_MIN, ZOOM_MAX)
                    val zoomRatio = newZoom / zoom
                    panX = mousePos.x + (panX - mousePos.x) * zoomRatio
                    panY = mousePos.y + (panY - mousePos.y) * zoomRatio
                    zoom = newZoom
                }
                .onPointerEvent(PointerEventType.Move) { event ->
                    hoverPos = event.changes.firstOrNull()?.position
                },
        ) {
            // ── Layer 1: Background ───────────────────────────────────────────
            if (backgroundMap != null) {
                drawBackgroundMap(backgroundMap, panX, panY, zoom, canvasSize)
            } else {
                drawHexGrid(panX, panY, zoom, canvasSize)
            }

            // ── Layer 2: Edges ────────────────────────────────────────────────
            overworld.edges.forEach { edge ->
                val fromNode = overworld.nodes.find { it.id == edge.fromId } ?: return@forEach
                val toNode = overworld.nodes.find { it.id == edge.toId } ?: return@forEach
                val from = nodeCanvasPos(fromNode)
                val to = nodeCanvasPos(toNode)
                val isSelected = selection.kind == GraphSelectionKind.EDGE &&
                        selection.edgeFromId == edge.fromId && selection.edgeToId == edge.toId
                val color = if (isSelected) DC.Blue else DC.Overlay0.copy(alpha = 0.65f)
                val strokeW = if (isSelected) 2.5f else 1.5f
                drawLine(color = color, start = from, end = to, strokeWidth = strokeW)
                drawArrowhead(from, to, color, strokeW)
            }

            // Pending edge preview
            val pendingFromNode = edgePendingFromId?.let { id -> overworld.nodes.find { it.id == id } }
            val hPos = hoverPos
            if (pendingFromNode != null && hPos != null) {
                val from = nodeCanvasPos(pendingFromNode)
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
                val cp = nodeCanvasPos(node)
                val r = nodeRadius()
                val isSelected = selection.kind == GraphSelectionKind.NODE && selection.nodeId == node.id

                when (node.type) {
                    OverworldNodeType.START -> {
                        drawCircle(color = DC.Green.copy(alpha = 0.25f), radius = r, center = cp)
                        drawCircle(color = DC.Green, radius = r, center = cp, style = Stroke(2f))
                    }
                    OverworldNodeType.BATTLE -> {
                        drawPentagon(cp, r, DC.Blue.copy(alpha = 0.20f), DC.Sapphire, 2f)
                    }
                    OverworldNodeType.REST -> {
                        drawCircle(color = DC.Yellow.copy(alpha = 0.20f), radius = r, center = cp)
                        drawCircle(color = DC.Yellow, radius = r, center = cp, style = Stroke(1.5f))
                    }
                    OverworldNodeType.BOSS -> {
                        drawOctagon(cp, r * 1.3f, DC.Red.copy(alpha = 0.25f), DC.Red, 2.5f)
                    }
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
            val cp = nodeCanvasPos(node)
            val glyph = when (node.type) {
                OverworldNodeType.START -> "▶"
                OverworldNodeType.BATTLE -> "⚔"
                OverworldNodeType.REST -> "⛺"
                OverworldNodeType.BOSS -> "☠"
            }
            Text(
                text = glyph,
                fontSize = (12f * zoom.coerceIn(0.5f, 2f)).sp,
                color = Color.White,
                modifier = Modifier.absoluteOffset {
                    IntOffset((cp.x - 8 * zoom).toInt(), (cp.y - 8 * zoom).toInt())
                },
            )

            // Node label below
            val labelFontSize = (9f * zoom.coerceIn(0.5f, 1.5f)).sp
            val r = nodeRadius()
            Text(
                text = node.label,
                fontSize = labelFontSize,
                color = DC.Subtext1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.absoluteOffset {
                    IntOffset((cp.x - 30 * zoom).toInt(), (cp.y + r + 4 * zoom).toInt())
                },
            )
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
                            OverworldNodeType.START -> DC.Green
                            OverworldNodeType.BATTLE -> DC.Blue
                            OverworldNodeType.REST -> DC.Yellow
                            OverworldNodeType.BOSS -> DC.Red
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

        // Interaction mode hint
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DC.Mantle.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val hint = when (interactionMode) {
                GraphInteractionMode.SELECT -> "Click node/edge to select • Drag node to move • Right-drag to pan"
                GraphInteractionMode.ADD_NODE -> "Click canvas to place ${nodeTypeToPlace.name} node"
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

private fun DrawScope.drawBackgroundMap(map: TileMap, panX: Float, panY: Float, zoom: Float, canvasSize: IntSize) {
    val tileW = (map.tileWidthPx * zoom)
    val tileH = (map.tileHeightPx * zoom)
    if (tileW < 0.5f || tileH < 0.5f) return

    val tileTypeMap = map.tiles.associate { (it.col to it.row) to it.type }

    for (r in 0 until map.rows) {
        for (c in 0 until map.cols) {
            val type = tileTypeMap[c to r] ?: TileType.FLOOR
            val color = tileTypeColorForGraph(type).copy(alpha = 0.12f)
            val left = panX + c * tileW
            val top = panY + r * tileH
            if (left + tileW < 0 || top + tileH < 0 || left > canvasSize.width || top > canvasSize.height) continue
            drawRect(color = color, topLeft = Offset(left, top), size = Size(tileW, tileH))
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
