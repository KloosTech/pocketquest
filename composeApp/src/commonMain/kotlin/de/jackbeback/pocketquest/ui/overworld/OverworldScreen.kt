package de.jackbeback.pocketquest.ui.overworld

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.definitions.Relic
import de.jackbeback.pocketquest.content.definitions.allRelics
import de.jackbeback.pocketquest.content.events.OverworldEvent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.game.run.RunScopedState
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private val ColorBg      = Color(0xFF0d1117)
private val ColorHp      = Color(0xFF238636)
private val ColorHpBg    = Color(0xFF3d1f1f)
private val ColorMana    = Color(0xFF388bfd)
private val ColorManaBg  = Color(0xFF1f2d3d)
private val ColorText    = Color(0xFFc9d1d9)
private val ColorSubtext = Color(0xFF8b949e)
private val ColorGold    = Color(0xFFd29922)

// Graph rendering constants
private const val GRAPH_SCALE = 2f

@Composable
fun OverworldScreen(
    viewModel: OverworldViewModel,
    onNextArea: () -> Unit = {},
    onEndRun: () -> Unit = {},
    onViewCharacter: () -> Unit = {},
) {
    val runState        by viewModel.runState.collectAsState()
    val eventsRemaining by viewModel.eventsRemaining.collectAsState()
    val pendingRest     by viewModel.pendingRest.collectAsState()
    val mapCleared      by viewModel.mapCleared.collectAsState()
    val playerHealth    by viewModel.playerHealth.collectAsState()
    val playerMana      by viewModel.playerMana.collectAsState()

    val currentNodeId    = runState?.currentNodeId ?: "start"
    val completedNodeIds = runState?.completedNodeIds ?: setOf("start")
    val availableNodeIds = if (currentNodeId in completedNodeIds)
        viewModel.graph.nextOf(currentNodeId).toSet() else emptySet()

    Box(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        ProgressionGraph(
            graph            = viewModel.graph,
            allEvents        = viewModel.allEvents,
            currentNodeId    = currentNodeId,
            completedNodeIds = completedNodeIds,
            availableNodeIds = availableNodeIds,
            onNodeTap        = { nodeId -> viewModel.onNodeTap(nodeId) },
            modifier         = Modifier.fillMaxSize(),
        )

        if (runState != null) {
            OverworldHud(
                runState        = runState!!,
                health          = playerHealth,
                mana            = playerMana,
                eventsRemaining = eventsRemaining,
                onViewCharacter = onViewCharacter,
                modifier        = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .systemBarsPadding(),
            )
        }

        pendingRest?.let { rest ->
            RestSiteDialog(
                rest      = rest,
                currentHp = playerHealth.current,
                maxHp     = playerHealth.max,
                onConfirm = { viewModel.onRestConfirmed() },
                onDismiss = { viewModel.onRestDismissed() },
            )
        }

        AnimatedVisibility(
            visible  = mapCleared,
            enter    = scaleIn(tween(300)) + fadeIn(tween(300)),
            exit     = scaleOut(tween(200)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            MapClearOverlay(
                runState  = runState,
                onNextArea = {
                    viewModel.dismissMapClear()
                    onNextArea()
                },
                onEndRun  = onEndRun,
            )
        }
    }
}

// ── Progression Graph ─────────────────────────────────────────────────────────

@Composable
private fun ProgressionGraph(
    graph: de.jackbeback.pocketquest.content.events.MapGraph,
    allEvents: Map<String, OverworldEvent>,
    currentNodeId: String,
    completedNodeIds: Set<String>,
    availableNodeIds: Set<String>,
    onNodeTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val scope      = rememberCoroutineScope()
    val panX       = remember { Animatable(0f) }
    val panY       = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val density    = LocalDensity.current
    val nodeRadius = remember(density) { with(density) { 22.dp.toPx() } }

    // Derived virtual canvas dimensions
    val graphW = canvasSize.width.toFloat()  * GRAPH_SCALE
    val graphH = canvasSize.height.toFloat() * GRAPH_SCALE
    val maxPanX = (graphW - canvasSize.width).coerceAtLeast(0f)
    val maxPanY = (graphH - canvasSize.height).coerceAtLeast(0f)

    // Center on current node whenever it changes or the canvas is first laid out
    LaunchedEffect(currentNodeId, canvasSize) {
        if (canvasSize.width == 0) return@LaunchedEffect
        val ev = allEvents[currentNodeId] ?: return@LaunchedEffect
        val tx = (ev.x.toFloat() * graphW - canvasSize.width  / 2f).coerceIn(0f, maxPanX)
        val ty = (ev.y.toFloat() * graphH - canvasSize.height / 2f).coerceIn(0f, maxPanY)
        launch { panX.animateTo(tx, tween(400)) }
        launch { panY.animateTo(ty, tween(400)) }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down      = awaitFirstDown()
                    var drag      = androidx.compose.ui.geometry.Offset.Zero
                    var isDragging = false
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!isDragging && canvasSize.width > 0) {
                                val tapGraphX = down.position.x + panX.value
                                val tapGraphY = down.position.y + panY.value
                                val gw = canvasSize.width  * GRAPH_SCALE
                                val gh = canvasSize.height * GRAPH_SCALE
                                val hit = allEvents.entries.firstOrNull { (_, ev) ->
                                    val nx = ev.x.toFloat() * gw
                                    val ny = ev.y.toFloat() * gh
                                    val dx = tapGraphX - nx
                                    val dy = tapGraphY - ny
                                    sqrt(dx * dx + dy * dy) <= nodeRadius * 1.4f
                                }?.key
                                if (hit != null) onNodeTap(hit)
                            }
                            break
                        }
                        val delta = change.position - change.previousPosition
                        drag += delta
                        if (!isDragging && drag.getDistance() > touchSlop) isDragging = true
                        if (isDragging) {
                            change.consume()
                            scope.launch {
                                panX.snapTo((panX.value - delta.x).coerceIn(0f, maxPanX))
                                panY.snapTo((panY.value - delta.y).coerceIn(0f, maxPanY))
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ox = panX.value
            val oy = panY.value
            val gw = size.width  * GRAPH_SCALE
            val gh = size.height * GRAPH_SCALE

            // Draw edges
            graph.nodes.forEach { node ->
                val fromEv = allEvents[node.id] ?: return@forEach
                val fx = fromEv.x.toFloat() * gw - ox
                val fy = fromEv.y.toFloat() * gh - oy
                node.nextIds.forEach { nextId ->
                    val toEv = allEvents[nextId] ?: return@forEach
                    val tx = toEv.x.toFloat() * gw - ox
                    val ty = toEv.y.toFloat() * gh - oy
                    val edgeColor = when {
                        node.id in completedNodeIds -> Color(0xFF388bfd).copy(alpha = 0.8f)
                        node.id == currentNodeId    -> Color(0xFF484f58)
                        else                        -> Color(0xFF21262d)
                    }
                    drawLine(edgeColor, Offset(fx, fy), Offset(tx, ty), strokeWidth = 2.5f)
                }
            }

            // Draw nodes
            graph.nodes.forEach { node ->
                val ev = allEvents[node.id] ?: return@forEach
                val cx = ev.x.toFloat() * gw - ox
                val cy = ev.y.toFloat() * gh - oy
                if (cx + nodeRadius * 2 < 0 || cx - nodeRadius * 2 > size.width) return@forEach
                if (cy + nodeRadius * 2 < 0 || cy - nodeRadius * 2 > size.height) return@forEach

                val isCompleted = node.id in completedNodeIds
                val isCurrent   = node.id == currentNodeId
                val isAvailable = node.id in availableNodeIds
                val isBoss      = ev is OverworldEvent.BattleEncounter && ev.isBoss

                val bgColor = when {
                    isCurrent   -> Color(0xFF1f2d3d)
                    isCompleted -> Color(0xFF161b22)
                    isAvailable -> when (ev) {
                        is OverworldEvent.RestSite      -> Color(0xFF1a3a1f)
                        is OverworldEvent.BattleEncounter -> if (isBoss) Color(0xFF2d2a00) else Color(0xFF3a1a1a)
                        else                             -> Color(0xFF161b22)
                    }
                    else        -> Color(0xFF0d1117)
                }
                val borderColor = when {
                    isCurrent   -> ColorGold
                    isCompleted -> Color(0xFF30363d)
                    isAvailable -> when (ev) {
                        is OverworldEvent.RestSite        -> Color(0xFF3fb950)
                        is OverworldEvent.BattleEncounter -> if (isBoss) ColorGold else Color(0xFFf85149)
                        else                              -> Color(0xFF484f58)
                    }
                    else        -> Color(0xFF21262d)
                }
                val borderWidth = if (isCurrent || isAvailable) 2.5f else 1.5f

                // Node circle
                drawCircle(bgColor,     nodeRadius,           Offset(cx, cy))
                drawCircle(borderColor, nodeRadius,           Offset(cx, cy), style = Stroke(borderWidth))

                // Icon text centred inside the circle
                val icon = nodeIcon(ev, isCompleted)
                val iconStyle = TextStyle(
                    fontSize   = if (isBoss) 16.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = when {
                        isCompleted -> Color(0xFF484f58)
                        isAvailable -> borderColor
                        isCurrent   -> ColorGold
                        else        -> Color(0xFF30363d)
                    },
                )
                val measured = textMeasurer.measure(icon, iconStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text  = icon,
                    style = iconStyle,
                    topLeft = Offset(
                        cx - measured.size.width  / 2f,
                        cy - measured.size.height / 2f,
                    ),
                )

                // Label below the node
                if (ev !is OverworldEvent.StartNode) {
                    val labelStyle = TextStyle(
                        fontSize = 8.sp,
                        color    = if (isAvailable || isCurrent) ColorText else Color(0xFF30363d),
                    )
                    val labelMeasured = textMeasurer.measure(ev.label, labelStyle)
                    drawText(
                        textMeasurer = textMeasurer,
                        text  = ev.label,
                        style = labelStyle,
                        topLeft = Offset(
                            cx - labelMeasured.size.width / 2f,
                            cy + nodeRadius + 4f,
                        ),
                    )
                }
            }
        }
    }
}

private fun nodeIcon(event: OverworldEvent, completed: Boolean): String = when {
    completed -> "✓"
    event is OverworldEvent.StartNode -> "▶"
    event is OverworldEvent.RestSite  -> "✦"
    event is OverworldEvent.BattleEncounter && event.isBoss -> "☠"
    else -> "⚔"
}

// ── Rest site dialog ──────────────────────────────────────────────────────────

@Composable
private fun RestSiteDialog(
    rest: OverworldEvent.RestSite,
    currentHp: Int,
    maxHp: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val healAmount = (maxHp * rest.healPercent).toInt().coerceAtLeast(1)
    val healedHp   = (currentHp + healAmount).coerceAtMost(maxHp)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rest.label) },
        text  = {
            Text("Rest here to restore ${(rest.healPercent * 100).toInt()}% HP?\n" +
                 "($currentHp → $healedHp / $maxHp)")
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Rest") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Leave") } },
    )
}

// ── HUD ───────────────────────────────────────────────────────────────────────

@Composable
private fun OverworldHud(
    runState: RunScopedState,
    health: HealthComponent,
    mana: ManaComponent,
    eventsRemaining: Int,
    onViewCharacter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val characterName = runState.characterTemplateId.replaceFirstChar { it.uppercase() }
    val encounterNum  = runState.difficultyCounter + 1
    val hpRatio       = health.current.toFloat() / health.max.toFloat().coerceAtLeast(1f)
    val manaRatio     = if (mana.max > 0) mana.current.toFloat() / mana.max.toFloat() else 0f
    val heldRelics    = allRelics.filter { it.id in runState.relics }

    Column(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .background(Color(0xCC0d1117), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(characterName, color = ColorText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("HP", color = ColorSubtext, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                    LinearProgressIndicator(
                        progress    = { hpRatio },
                        modifier    = Modifier.weight(1f).height(5.dp),
                        color       = ColorHp, trackColor = ColorHpBg,
                    )
                    Text("${health.current}/${health.max}", color = ColorSubtext, fontSize = 10.sp)
                }
                if (mana.max > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MP", color = ColorSubtext, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                        LinearProgressIndicator(
                            progress    = { manaRatio },
                            modifier    = Modifier.weight(1f).height(5.dp),
                            color       = ColorMana, trackColor = ColorManaBg,
                        )
                        Text("${mana.current}/${mana.max}", color = ColorSubtext, fontSize = 10.sp)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("#$encounterNum", color = ColorGold, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("$eventsRemaining left", color = ColorSubtext, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onViewCharacter,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("👤 Stats", color = ColorText, fontSize = 10.sp)
                }
            }
        }
        if (heldRelics.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(heldRelics) { relic -> RelicChip(relic) }
            }
        }
    }
}

@Composable
private fun RelicChip(relic: Relic) {
    Row(
        modifier = Modifier
            .background(Color(0xFF0d1117), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF30363d), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(relicIcon(relic.id), fontSize = 11.sp)
        Text(relic.name, color = ColorGold, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Start)
    }
}

// ── Map-clear overlay ─────────────────────────────────────────────────────────

@Composable
private fun MapClearOverlay(
    runState: RunScopedState?,
    onNextArea: () -> Unit,
    onEndRun: () -> Unit,
) {
    val heldRelics = allRelics.filter { it.id in (runState?.relics ?: emptyList()) }
    val areaNum    = runState?.areaNumber ?: 1

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(Color(0xFF161b22), RoundedCornerShape(12.dp))
                .border(1.dp, ColorGold.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("☠  Area $areaNum Cleared!", color = ColorGold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            HorizontalDivider(color = Color(0xFF30363d), thickness = 0.5.dp)
            if (runState != null) {
                StatRow("Level",      "Lv ${runState.level}")
                StatRow("Encounters", "${runState.difficultyCounter}")
                if (heldRelics.isNotEmpty()) StatRow("Relics", "${heldRelics.size}")
            }
            HorizontalDivider(color = Color(0xFF30363d), thickness = 0.5.dp)
            Text("The map is clear. Press on.", color = ColorSubtext, fontSize = 11.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onNextArea,
                colors  = ButtonDefaults.buttonColors(containerColor = ColorGold, contentColor = Color(0xFF0d1117)),
                modifier = Modifier.fillMaxWidth(),
                shape   = RoundedCornerShape(8.dp),
            ) {
                Text("Next Area  →", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onEndRun, modifier = Modifier.fillMaxWidth()) {
                Text("End Run", color = ColorSubtext, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ColorSubtext, fontSize = 12.sp)
        Text(value, color = ColorText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun relicIcon(relicId: String): String = when (relicId) {
    "ancient_tome"  -> "📖"
    "iron_will"     -> "🛡"
    "war_trophy"    -> "⚔"
    "mage_stone"    -> "💎"
    "well_of_power" -> "🌊"
    "scholars_ring" -> "💍"
    "blood_pact"    -> "❤"
    "arcane_focus"  -> "✨"
    else            -> "★"
}
