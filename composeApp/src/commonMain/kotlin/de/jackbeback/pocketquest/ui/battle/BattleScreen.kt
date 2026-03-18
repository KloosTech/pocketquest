package de.jackbeback.pocketquest.ui.battle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.map.throneRoomConfig
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.game.animation.ANIM_DURATION_MS
import de.jackbeback.pocketquest.game.animation.MOVE_ANIM_MS
import de.jackbeback.pocketquest.game.animation.AnimationEvent
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS
import de.jackbeback.pocketquest.game.loop.TurnPhase
import de.jackbeback.pocketquest.ui.component.ConditionBadges
import de.jackbeback.pocketquest.ui.component.SkillPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colour palette ───────────────────────────────────────────────────────────
private val ColorBg          = Color(0xFF0d1117)
private val ColorFieldBg     = Color(0xFF161b22)
private val ColorTileA       = Color(0xFF161d2b)   // even tiles
private val ColorTileB       = Color(0xFF1a2232)   // odd tiles
private val ColorTileGrid    = Color(0xFF2a3547)   // grid lines
private val ColorTileMove    = Color(0xFF1a4a7a)   // blue: reachable
private val ColorTileMoveRim = Color(0xFF58a6ff)
private val ColorTileAtk     = Color(0xFF5a1a1a)   // red: attackable
private val ColorTileAtkRim  = Color(0xFFf85149)
private val ColorTileHeal    = Color(0xFF1a4a2a)   // green: healable
private val ColorTileHealRim = Color(0xFF3fb950)
private val ColorTileSel     = Color(0xFF4a3a00)   // gold: confirmed pending target
private val ColorTileSelRim  = Color(0xFFd29922)
private val ColorPlayer      = Color(0xFF58a6ff)
private val ColorEnemy       = Color(0xFFf85149)
private val ColorHpBar       = Color(0xFF238636)
private val ColorHpBarBg     = Color(0xFF3d1f1f)
private val ColorLogBg       = Color(0xFF0d1117)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleScreen(viewModel: BattleViewModel, onBattleEnd: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val animationEvent by viewModel.animationEvent.collectAsState()
    val phaseBanner by viewModel.phaseBanner.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val tiles by viewModel.tileCache.tiles.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSkillSheet by remember { mutableStateOf(false) }

    val canAct = state.turnPhase == TurnPhase.PlayerPhase && !isLocked && !state.playerHasActed

    LaunchedEffect(state.isBattleOver) {
        if (state.isBattleOver) onBattleEnd()
    }

    // Dismiss sheet when we leave the player phase
    LaunchedEffect(canAct) {
        if (!canAct) showSkillSheet = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorBg)
                .systemBarsPadding()
        ) {
            PhaseBar(state)

            TacticalGrid(
                state = state,
                animationEvent = animationEvent,
                tiles = tiles,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BATTLE_COLS.toFloat() / BATTLE_ROWS)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                onCellTap = { col, row -> viewModel.onCellTap(col, row) },
            )

            UnitStatusRow(state.units)

            if (state.log.isNotEmpty()) BattleLogPanel(state.log)

            // Targeting overlay bar — visible while a skill is armed
            TargetingBar(
                state = state,
                isLocked = isLocked,
                onCancel = { viewModel.onCancelSkill() },
                onConfirm = { viewModel.onConfirmTargets() },
            )

            ActionBar(
                phase = state.turnPhase,
                movesRemaining = state.playerMovesRemaining,
                hasActed = state.playerHasActed,
                isLocked = isLocked,
                onEndTurn = { viewModel.onEndTurn() },
                onOpenSkillSheet = { showSkillSheet = true },
            )
        }

        // Phase banner overlay
        AnimatedVisibility(
            visible = phaseBanner != null,
            enter = scaleIn(tween(200)) + fadeIn(tween(200)),
            exit  = scaleOut(tween(200)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = phaseBanner ?: "",
                    color = if (phaseBanner == "Enemy Turn") ColorEnemy else ColorPlayer,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }

    // Skill selection bottom sheet
    if (showSkillSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSkillSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF161b22),
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color(0xFF484f58), RoundedCornerShape(2.dp))
                )
            },
        ) {
            Text(
                text = "Select a Skill",
                color = Color(0xFFc9d1d9),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, bottom = 8.dp),
            )
            SkillPanel(
                skills = state.availableSkills,
                selectedSkillId = null,
                onSkillSelected = { id ->
                    showSkillSheet = false
                    viewModel.onSkillSelected(id)
                },
                enabled = canAct,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

// ── Phase bar ────────────────────────────────────────────────────────────────

@Composable
private fun PhaseBar(state: BattleUiState) {
    val (label, color) = when (state.turnPhase) {
        TurnPhase.PlayerPhase    -> "Your Turn" to ColorPlayer
        TurnPhase.EnemyPhase     -> "Enemy Turn" to ColorEnemy
        TurnPhase.EnvironmentPhase -> "Environment" to Color(0xFF8b949e)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorFieldBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (state.turnPhase == TurnPhase.PlayerPhase) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MoveDots(remaining = state.playerMovesRemaining, max = state.playerMaxMoves)
                ActionChip(label = "Act", done = state.playerHasActed, activeColor = Color(0xFFd29922))
            }
        }
    }
}

/** Filled/empty dot row showing how many move hops remain this turn. */
@Composable
private fun MoveDots(remaining: Int, max: Int) {
    if (max == 0) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Move", color = if (remaining > 0) ColorPlayer else Color(0xFF484f58), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(2.dp))
        for (i in 0 until max) {
            val filled = i < remaining
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (filled) ColorPlayer else Color(0xFF2d3748),
                        RoundedCornerShape(50),
                    )
                    .border(1.dp, if (filled) ColorPlayer.copy(alpha = 0.6f) else Color(0xFF484f58), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun ActionChip(label: String, done: Boolean, activeColor: Color) {
    val bg    = if (done) Color(0xFF21262d) else activeColor.copy(alpha = 0.15f)
    val fg    = if (done) Color(0xFF484f58) else activeColor
    val style = if (done) TextDecoration.LineThrough else TextDecoration.None
    Text(
        text = label,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        textDecoration = style,
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Tactical grid ────────────────────────────────────────────────────────────

private val ThroneCols = throneRoomConfig.colCount
private val ThroneRows = throneRoomConfig.rowCount

@Composable
private fun TacticalGrid(
    state: BattleUiState,
    animationEvent: AnimationEvent?,
    tiles: Map<Pair<Int, Int>, ImageBitmap>,
    modifier: Modifier,
    onCellTap: (Int, Int) -> Unit,
) {
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    // Unit movement animation
    val moveProgress = remember { Animatable(0f) }
    LaunchedEffect(animationEvent) {
        if (animationEvent is AnimationEvent.UnitMove) {
            moveProgress.snapTo(0f)
            moveProgress.animateTo(1f, tween(MOVE_ANIM_MS.toInt(), easing = FastOutSlowInEasing))
        }
    }

    // Projectile animation
    val projectileProgress = remember { Animatable(0f) }
    LaunchedEffect(animationEvent) {
        if (animationEvent is AnimationEvent.ProjectileSkill) {
            projectileProgress.snapTo(0f)
            projectileProgress.animateTo(1f, tween(ANIM_DURATION_MS.toInt(), easing = FastOutSlowInEasing))
        }
    }

    // Floating number animation
    val floatAlpha   = remember { Animatable(0f) }
    val floatOffsetY = remember { Animatable(0f) }
    LaunchedEffect(animationEvent) {
        if (animationEvent is AnimationEvent.FloatingDamage || animationEvent is AnimationEvent.FloatingHeal) {
            floatAlpha.snapTo(1f)
            floatOffsetY.snapTo(0f)
            launch { floatOffsetY.animateTo(-60f, tween(ANIM_DURATION_MS.toInt())) }
            launch {
                delay(ANIM_DURATION_MS - 200)
                floatAlpha.animateTo(0f, tween(200))
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ColorFieldBg)
            .onSizeChanged { fieldSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (fieldSize.width > 0 && fieldSize.height > 0) {
                        val col = (offset.x / (fieldSize.width.toFloat() / BATTLE_COLS))
                            .toInt().coerceIn(0, BATTLE_COLS - 1)
                        val row = (offset.y / (fieldSize.height.toFloat() / BATTLE_ROWS))
                            .toInt().coerceIn(0, BATTLE_ROWS - 1)
                        onCellTap(col, row)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width  / BATTLE_COLS
            val cellH = size.height / BATTLE_ROWS

            // 1. Draw tile background — Throneroom images with tactical overlays
            for (r in 0 until BATTLE_ROWS) {
                for (c in 0 until BATTLE_COLS) {
                    val cell   = Pair(c, r)
                    val isMove = cell in state.reachableTiles
                    val isAtk  = cell in state.attackableTiles
                    val isSel  = cell in state.selectedTargetTiles
                    val tileX  = c % ThroneCols
                    val tileY  = r % ThroneRows
                    val tileBitmap = tiles[Pair(tileX, tileY)]
                    val topLeft  = Offset(c * cellW, r * cellH)
                    val tileSize = Size(cellW, cellH)

                    if (tileBitmap != null) {
                        drawImage(
                            image       = tileBitmap,
                            dstOffset   = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                            dstSize     = IntSize(tileSize.width.toInt() + 1, tileSize.height.toInt() + 1),
                            filterQuality = FilterQuality.Low,
                        )
                    } else {
                        // Placeholder colour while tiles are still loading
                        val base = if ((c + r) % 2 == 0) ColorTileA else ColorTileB
                        drawRect(color = base, topLeft = topLeft, size = tileSize)
                    }

                    // Semi-transparent tactical overlay (selected > attackable > reachable)
                    when {
                        isSel  -> drawRect(ColorTileSel.copy(alpha = 0.65f),  topLeft, tileSize)
                        isAtk  -> drawRect(ColorTileAtk.copy(alpha = 0.55f),  topLeft, tileSize)
                        isMove -> drawRect(ColorTileMove.copy(alpha = 0.45f), topLeft, tileSize)
                    }
                    // Coloured rim on highlighted tiles
                    when {
                        isSel  -> drawRect(ColorTileSelRim.copy(alpha = 0.9f),  topLeft, tileSize, style = Stroke(2.5f))
                        isAtk  -> drawRect(ColorTileAtkRim.copy(alpha = 0.7f),  topLeft, tileSize, style = Stroke(2f))
                        isMove -> drawRect(ColorTileMoveRim.copy(alpha = 0.7f), topLeft, tileSize, style = Stroke(2f))
                    }
                }
            }

            // 2. Grid lines
            for (i in 0..BATTLE_COLS) {
                drawLine(ColorTileGrid, Offset(i * cellW, 0f), Offset(i * cellW, size.height), 0.5f)
            }
            for (j in 0..BATTLE_ROWS) {
                drawLine(ColorTileGrid, Offset(0f, j * cellH), Offset(size.width, j * cellH), 0.5f)
            }

            // 3. Units (skip the currently-moving unit; draw it separately at lerped position)
            val movingId = (animationEvent as? AnimationEvent.UnitMove)?.entityId
            state.units.forEach { unit ->
                if (unit.entityId != movingId) drawUnit(unit, cellW, cellH)
            }

            // 3b. Moving unit at interpolated position
            if (animationEvent is AnimationEvent.UnitMove) {
                val mv = animationEvent
                val t = moveProgress.value
                val cx = (mv.fromNormX + (mv.toNormX - mv.fromNormX) * t) * size.width
                val cy = (mv.fromNormY + (mv.toNormY - mv.fromNormY) * t) * size.height
                val movingUnit = state.units.find { it.entityId == mv.entityId }
                if (movingUnit != null) drawUnitAtCenter(movingUnit, cx, cy, minOf(size.width / BATTLE_COLS, size.height / BATTLE_ROWS))
            }

            // 4. Projectile overlay
            if (animationEvent is AnimationEvent.ProjectileSkill) {
                drawProjectile(animationEvent, projectileProgress.value, size.width, size.height)
            }
        }

        // 5. Floating number text overlay
        if (fieldSize.width > 0) {
            val (nx, ny, text, color) = when (val e = animationEvent) {
                is AnimationEvent.FloatingDamage -> Quad(e.x, e.y, "-${e.amount}", damageColor(e.damageType))
                is AnimationEvent.FloatingHeal   -> Quad(e.x, e.y, "+${e.amount}", Color(0xFF3fb950))
                else                             -> null
            } ?: return@Box

            val xPx = (nx * fieldSize.width).toInt()
            val yPx = (ny * fieldSize.height - 50 + floatOffsetY.value).toInt()
            Text(
                text     = text,
                color    = color.copy(alpha = floatAlpha.value),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.absoluteOffset { IntOffset(xPx - 20, yPx) },
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun DrawScope.drawUnit(unit: UnitUiState, cellW: Float, cellH: Float) {
    val cx = (unit.position.col + 0.5f) * cellW
    val cy = (unit.position.row + 0.5f) * cellH
    drawUnitAtCenter(unit, cx, cy, minOf(cellW, cellH))
}

private fun DrawScope.drawUnitAtCenter(unit: UnitUiState, cx: Float, cy: Float, cellMin: Float) {
    val r     = cellMin * 0.36f
    val color = if (unit.faction == Faction.PLAYER) ColorPlayer else ColorEnemy

    // Outer glow
    drawCircle(color.copy(alpha = 0.18f), r + 5f, Offset(cx, cy))
    // Fill
    drawCircle(color.copy(alpha = 0.85f), r, Offset(cx, cy))
    // Border ring
    drawCircle(Color.White.copy(alpha = 0.7f), r, Offset(cx, cy), style = Stroke(2f))

    // HP bar directly below unit
    val hpRatio = unit.health.current.toFloat() / unit.health.max.toFloat().coerceAtLeast(1f)
    val barW  = cellMin * 0.7f
    val barH  = 3.dp.toPx()
    val barX  = cx - barW / 2
    val barY  = cy + r + 4f
    drawRect(ColorHpBarBg, Offset(barX, barY), Size(barW, barH))
    if (hpRatio > 0f) drawRect(ColorHpBar, Offset(barX, barY), Size(barW * hpRatio, barH))
}

private fun DrawScope.drawProjectile(
    event: AnimationEvent.ProjectileSkill,
    progress: Float,
    canvasW: Float,
    canvasH: Float,
) {
    val px = (event.fromX + (event.toX - event.fromX) * progress) * canvasW
    val py = (event.fromY + (event.toY - event.fromY) * progress) * canvasH
    val color = projectileColor(event.skillId)
    drawCircle(color.copy(alpha = 0.35f), 14.dp.toPx(), Offset(px, py))
    drawCircle(color,                     7.dp.toPx(),  Offset(px, py))
    drawCircle(Color.White.copy(alpha = 0.8f), 3.dp.toPx(), Offset(px, py))
}

private fun projectileColor(skillId: String): Color = when (skillId) {
    "fireball"      -> Color(0xFFFF6B00)
    "magic_missile" -> Color(0xFF58a6ff)
    "thorn_whip"    -> Color(0xFF3fb950)
    else            -> Color.White
}

private fun damageColor(type: DamageType): Color = when (type) {
    DamageType.FIRE        -> Color(0xFFFF6B00)
    DamageType.WATER       -> Color(0xFF58a6ff)
    DamageType.ELECTRIC    -> Color(0xFFFFD700)
    DamageType.FORCE       -> Color(0xFFAA88FF)
    DamageType.ICE         -> Color(0xFF79C0FF)
    DamageType.POISON      -> Color(0xFF3fb950)
    DamageType.PIERCING    -> Color(0xFFE3B341)
    DamageType.SLICING     -> Color(0xFFE3B341)
    DamageType.BLUDGEONING -> Color(0xFFf85149)
}

// ── Status panels ────────────────────────────────────────────────────────────

@Composable
private fun BattleLogPanel(log: List<String>) {
    val listState = rememberLazyListState()
    val recent = log.takeLast(8)
    LaunchedEffect(log.size) { if (recent.isNotEmpty()) listState.animateScrollToItem(recent.lastIndex) }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 80.dp)
            .background(ColorLogBg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        items(recent) { entry ->
            Text(entry, color = Color(0xFF8b949e), fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

@Composable
private fun UnitStatusRow(units: List<UnitUiState>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(units) { UnitStatusCard(it) }
    }
}

@Composable
private fun UnitStatusCard(unit: UnitUiState) {
    val borderColor = if (unit.faction == Faction.PLAYER) ColorPlayer else ColorEnemy
    Column(
        modifier = Modifier
            .width(110.dp)
            .background(ColorFieldBg, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(unit.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { unit.health.current.toFloat() / unit.health.max.toFloat().coerceAtLeast(1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = ColorHpBar, trackColor = ColorHpBarBg,
        )
        Text("${unit.health.current}/${unit.health.max}", color = Color(0xFF8b949e), fontSize = 10.sp)
        if (unit.mana.max > 0) {
            LinearProgressIndicator(
                progress = { unit.mana.current.toFloat() / unit.mana.max.toFloat().coerceAtLeast(1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Color(0xFF388bfd), trackColor = Color(0xFF1f2d3d),
            )
            Text("${unit.mana.current}/${unit.mana.max} MP", color = Color(0xFF8b949e), fontSize = 10.sp)
        }
        // Grid position
        Text("(${unit.position.col}, ${unit.position.row})", color = Color(0xFF484f58), fontSize = 9.sp)
        if (unit.conditions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ConditionBadges(conditions = unit.conditions)
        }
    }
}

/**
 * Slide-in bar that appears when a skill is armed, showing the skill name,
 * pending target count (for multi-target), a Confirm button, and a Cancel button.
 */
@Composable
private fun TargetingBar(
    state: BattleUiState,
    isLocked: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val skillInfo = state.availableSkills.find { it.id == state.selectedSkill }
    val visible = skillInfo != null && !isLocked

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit  = slideOutVertically { it } + fadeOut(),
    ) {
        if (skillInfo == null) return@AnimatedVisibility
        val pendingCount = state.pendingTargetIds.size
        val maxTargets = skillInfo.maxTargets
        val isMulti = maxTargets > 1

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1f2d3d))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Skill name
            Text(
                text = skillInfo.name,
                color = ColorPlayer,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            // Target count badge (multi-target only)
            if (isMulti) {
                Text(
                    text = "$pendingCount / $maxTargets",
                    color = Color(0xFFd29922),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF2d2a00), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            // Confirm (only when at least one target pending for multi-target skills)
            if (isMulti && pendingCount > 0) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF238636), contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Execute", fontSize = 12.sp)
                }
            } else if (!isMulti) {
                Text(
                    text = "Tap a target",
                    color = Color(0xFF8b949e),
                    fontSize = 11.sp,
                )
            }

            // Cancel
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFf85149)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActionBar(
    phase: TurnPhase,
    movesRemaining: Int,
    hasActed: Boolean,
    isLocked: Boolean,
    onEndTurn: () -> Unit,
    onOpenSkillSheet: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorFieldBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canAct = phase == TurnPhase.PlayerPhase && !isLocked && !hasActed

        Button(
            onClick = onOpenSkillSheet,
            enabled = canAct,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1f2d3d),
                contentColor = ColorPlayer,
                disabledContainerColor = Color(0xFF161b22),
                disabledContentColor = Color(0xFF484f58),
            ),
        ) {
            Text("Cast")
        }

        val hint = when {
            isLocked                            -> "…"
            phase != TurnPhase.PlayerPhase      -> ""
            !hasActed && movesRemaining > 0     -> "Move or Cast a spell"
            !hasActed                           -> "Cast a spell"
            movesRemaining > 0                  -> "${movesRemaining} move(s) left"
            else                                -> "End your turn"
        }
        Text(hint, color = Color(0xFF484f58), fontSize = 11.sp, modifier = Modifier.weight(1f))

        Button(
            onClick = onEndTurn,
            enabled = phase == TurnPhase.PlayerPhase && !isLocked,
            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636), contentColor = Color.White),
        ) {
            Text("End Turn")
        }
    }
}

