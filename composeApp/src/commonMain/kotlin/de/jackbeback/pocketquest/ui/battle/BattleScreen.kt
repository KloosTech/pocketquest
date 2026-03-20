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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.definitions.Relic
import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.content.dsl.attributeModifier
import de.jackbeback.pocketquest.content.map.TileType
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.game.animation.ANIM_DURATION_MS
import de.jackbeback.pocketquest.game.animation.MELEE_ANIM_MS
import de.jackbeback.pocketquest.game.animation.MOVE_ANIM_MS
import de.jackbeback.pocketquest.game.animation.AnimationEvent
import de.jackbeback.pocketquest.game.loop.TurnPhase
import de.jackbeback.pocketquest.ui.component.ConditionBadges
import de.jackbeback.pocketquest.ui.component.HintOverlay
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

// Pre-computed alpha variants — avoids Color.copy() allocations in the hot tile loop
private val ColorTileSelFill    = ColorTileSel.copy(alpha = 0.65f)
private val ColorTileAtkFill    = ColorTileAtk.copy(alpha = 0.55f)
private val ColorTileSelRimFull = ColorTileSelRim.copy(alpha = 0.9f)
private val ColorTileAtkRimFull = ColorTileAtkRim.copy(alpha = 0.7f)
private val ColorFowExplored    = Color.Black.copy(alpha = 0.60f)

private fun terrainTileColor(type: TileType): Color? = when (type) {
    TileType.FLOOR             -> null
    TileType.WALL              -> Color(0xFF808080)
    TileType.WATER             -> Color(0xFF4090FF)
    TileType.COVER_LOW         -> Color(0xFF90FF90)
    TileType.COVER_HIGH        -> Color(0xFF00C040)
    TileType.DIFFICULT_TERRAIN -> Color(0xFFFFB020)
    TileType.HAZARD            -> Color(0xFFFF3030)
    TileType.VOID              -> Color(0xFF000000)
}

/** Per-TileType terrain overlay colour (alpha already baked in). Null = no overlay. */
private val terrainOverlayColors: Map<TileType, Color> =
    TileType.entries.mapNotNull { type ->
        terrainTileColor(type)?.let { type to it.copy(alpha = 0.35f) }
    }.toMap()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleScreen(
    viewModel: BattleViewModel,
    onBattleEnd: (result: BattleResult) -> Unit = {},
    /** Called with the relic id chosen by the player before Continue is tapped. */
    onRelicChosen: (relicId: String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val animationEvent by viewModel.animationEvent.collectAsState()
    val phaseBanner by viewModel.phaseBanner.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val tiles by viewModel.tileCache.tiles.collectAsState()
    val battleResult by viewModel.battleResult.collectAsState()
    val pendingHint by viewModel.pendingHint.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSkillSheet by remember { mutableStateOf(false) }

    val isPlayerPhase = state.turnPhase == TurnPhase.PlayerPhase
    val hasMana = state.playerMana.current > 0
    val canAct = isPlayerPhase && !isLocked && hasMana

    // Dismiss sheet when no longer the player's turn or out of mana
    LaunchedEffect(isPlayerPhase, isLocked) {
        if (!isPlayerPhase || isLocked) showSkillSheet = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ColorBg)
                .systemBarsPadding()
        ) {
            val isWide = maxWidth >= 600.dp
            val playerUnit = state.units.find { it.faction == Faction.PLAYER }

            if (isWide) {
                // ── Desktop / landscape: grid on left, controls in fixed side column ──
                Column(modifier = Modifier.fillMaxSize()) {
                    PhaseBar(state)
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        TacticalGrid(
                            state = state,
                            animationEvent = animationEvent,
                            tiles = tiles,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            onCellTap = { col, row -> viewModel.onCellTap(col, row) },
                        )
                        Column(
                            modifier = Modifier
                                .width(240.dp)
                                .fillMaxHeight()
                                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            UnitStatusRow(
                                state = state,
                                onUnitTap = { entityId -> viewModel.onUnitCardTap(entityId) },
                            )
                            if (state.log.isNotEmpty()) {
                                BattleLogPanel(
                                    log = state.log,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            TargetingBar(
                                state = state,
                                isLocked = isLocked,
                                onCancel = { viewModel.onCancelSkill() },
                                onConfirm = { viewModel.onConfirmTargets() },
                            )
                            DPad(
                                phase = state.turnPhase,
                                movesRemaining = state.playerMovesRemaining,
                                isLocked = isLocked,
                                playerPos = playerUnit?.position,
                                reachableTiles = state.reachableTiles,
                                onMoveDirection = { dx, dy -> viewModel.onMoveDirection(dx, dy) },
                            )
                            ActionBar(
                                phase = state.turnPhase,
                                movesRemaining = state.playerMovesRemaining,
                                playerMana = state.playerMana,
                                isLocked = isLocked,
                                onEndTurn = { viewModel.onEndTurn() },
                                onOpenSkillSheet = { showSkillSheet = true },
                            )
                        }
                    }
                }
            } else {
                // ── Mobile / portrait: existing stacked layout ────────────────────
                Column(modifier = Modifier.fillMaxSize()) {
                    PhaseBar(state)

                    TacticalGrid(
                        state = state,
                        animationEvent = animationEvent,
                        tiles = tiles,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        onCellTap = { col, row -> viewModel.onCellTap(col, row) },
                    )

                    UnitStatusRow(
                        state = state,
                        onUnitTap = { entityId -> viewModel.onUnitCardTap(entityId) },
                    )

                    if (state.log.isNotEmpty()) BattleLogPanel(state.log)

                    TargetingBar(
                        state = state,
                        isLocked = isLocked,
                        onCancel = { viewModel.onCancelSkill() },
                        onConfirm = { viewModel.onConfirmTargets() },
                    )

                    DPad(
                        phase = state.turnPhase,
                        movesRemaining = state.playerMovesRemaining,
                        isLocked = isLocked,
                        playerPos = playerUnit?.position,
                        reachableTiles = state.reachableTiles,
                        onMoveDirection = { dx, dy -> viewModel.onMoveDirection(dx, dy) },
                    )

                    ActionBar(
                        phase = state.turnPhase,
                        movesRemaining = state.playerMovesRemaining,
                        playerMana = state.playerMana,
                        isLocked = isLocked,
                        onEndTurn = { viewModel.onEndTurn() },
                        onOpenSkillSheet = { showSkillSheet = true },
                    )
                }
            }
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

        // Post-battle result overlay
        AnimatedVisibility(
            visible = battleResult != null,
            enter = scaleIn(tween(300)) + fadeIn(tween(300)),
            exit  = scaleOut(tween(200)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            val result = battleResult ?: return@AnimatedVisibility
            BattleResultOverlay(
                result         = result,
                onRelicChosen  = onRelicChosen,
                onContinue     = { onBattleEnd(result) },
            )
        }

        // Tutorial hint overlay — shown on top of everything, dismissed by player
        pendingHint?.let { hint ->
            HintOverlay(hint = hint, onDismiss = viewModel::dismissHint)
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
                modifier = Modifier.padding(start = 8.dp,end = 8.dp, bottom = 8.dp),
            )
            SkillPanel(
                skills = state.availableSkills,
                selectedSkillId = null,
                onSkillSelected = { id ->
                    showSkillSheet = false
                    viewModel.onSkillSelected(id)
                },
                enabled = canAct,
                playerMana = state.playerMana.current,
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
                ManaChip(mana = state.playerMana)
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
private fun ManaChip(mana: de.jackbeback.pocketquest.ecs.components.core.ManaComponent) {
    val empty = mana.current == 0
    val bg = if (empty) Color(0xFF21262d) else Color(0xFF1f2d3d)
    val fg = if (empty) Color(0xFF484f58) else Color(0xFF388bfd)
    Text(
        text = "${mana.current}/${mana.max} MP",
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ── Tactical grid ────────────────────────────────────────────────────────────

private const val ZOOM_MIN = 1.0f
private const val ZOOM_MAX = 6f
private const val ZOOM_DEFAULT = 1.5f

@Composable
private fun TacticalGrid(
    state: BattleUiState,
    animationEvent: AnimationEvent?,
    tiles: Map<Pair<Int, Int>, ImageBitmap>,
    modifier: Modifier,
    onCellTap: (Int, Int) -> Unit,
) {
    val gridCols = state.gridCols
    val gridRows = state.gridRows

    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // Pan offsets
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }

    // Zoom — starts at 1.5× for a close-up feel; range ZOOM_MIN..ZOOM_MAX
    val zoomState = remember { mutableStateOf(ZOOM_DEFAULT) }
    val zoom by zoomState

    // Base cell size fills the full width at zoom=1; multiply by zoom for actual size
    val baseCellSize = if (fieldSize.width > 0) fieldSize.width / gridCols.toFloat() else 0f
    val cellSize = baseCellSize * zoom
    val cellW = cellSize
    val cellH = cellSize
    val maxPanX = (gridCols * cellW - fieldSize.width).coerceAtLeast(0f)
    val maxPanY = (gridRows * cellH - fieldSize.height).coerceAtLeast(0f)

    // Center on player on first layout (battle start / window resize)
    val playerPos = state.units.find { it.faction == Faction.PLAYER }?.position
    LaunchedEffect(fieldSize) {
        val pos = playerPos ?: return@LaunchedEffect
        if (fieldSize.width == 0) return@LaunchedEffect
        val targetX = ((pos.col + 0.5f) * cellW - fieldSize.width  / 2f).coerceIn(0f, maxPanX)
        val targetY = ((pos.row + 0.5f) * cellH - fieldSize.height / 2f).coerceIn(0f, maxPanY)
        launch { panX.animateTo(targetX, tween(350)) }
        launch { panY.animateTo(targetY, tween(350)) }
    }

    // Pinch-to-zoom + two-finger pan (mobile / trackpad)
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val curZoom = zoomState.value
        val newZoom = (curZoom * zoomChange).coerceIn(ZOOM_MIN, ZOOM_MAX)
        val bcs = if (fieldSize.width > 0) fieldSize.width / gridCols.toFloat() else return@rememberTransformableState
        val zf = newZoom / curZoom
        val cx = fieldSize.width  / 2f
        val cy = fieldSize.height / 2f
        val newMaxPanX = (gridCols * bcs * newZoom - fieldSize.width).coerceAtLeast(0f)
        val newMaxPanY = (gridRows * bcs * newZoom - fieldSize.height).coerceAtLeast(0f)
        val npx = ((panX.value + cx) * zf - cx - panChange.x).coerceIn(0f, newMaxPanX)
        val npy = ((panY.value + cy) * zf - cy - panChange.y).coerceIn(0f, newMaxPanY)
        zoomState.value = newZoom
        scope.launch { panX.snapTo(npx) }
        scope.launch { panY.snapTo(npy) }
    }

    // Unit movement animation
    val moveProgress = remember { Animatable(0f) }
    LaunchedEffect(animationEvent) {
        if (animationEvent is AnimationEvent.UnitMove) {
            moveProgress.snapTo(0f)
            moveProgress.animateTo(1f, tween(MOVE_ANIM_MS.toInt(), easing = FastOutSlowInEasing))
        }
    }

    // Projectile animation (duration depends on animation type)
    val projectileProgress = remember { Animatable(0f) }
    LaunchedEffect(animationEvent) {
        if (animationEvent is AnimationEvent.ProjectileSkill) {
            val animDuration = when (animationEvent.animationType) {
                AnimationType.MELEE -> MELEE_ANIM_MS.toInt()
                else                -> ANIM_DURATION_MS.toInt()
            }
            projectileProgress.snapTo(0f)
            projectileProgress.animateTo(1f, tween(animDuration, easing = FastOutSlowInEasing))
        }
    }

    // Floating number animation
    val floatAlpha   = remember { Animatable(0f) }
    val floatOffsetY = remember { Animatable(0f) }
    LaunchedEffect(animationEvent) {
        if (animationEvent is AnimationEvent.FloatingDamage || animationEvent is AnimationEvent.FloatingHeal
            || animationEvent is AnimationEvent.FloatingMiss) {
            floatAlpha.snapTo(1f)
            floatOffsetY.snapTo(0f)
            launch { floatOffsetY.animateTo(-60f, tween(ANIM_DURATION_MS.toInt())) }
            launch {
                delay(ANIM_DURATION_MS - 200)
                floatAlpha.animateTo(0f, tween(200))
            }
        }
    }

    // ── Flat arrays: replace per-frame Pair/HashMap lookups with O(1) array reads ──
    val cols = gridCols
    val rows = gridRows
    val gridSize = cols * rows

    val attackableArr = remember(state.attackableTiles, cols, rows) {
        BooleanArray(gridSize).also { a ->
            state.attackableTiles.forEach { (c, r) -> a[r * cols + c] = true }
        }
    }
    val selectedArr = remember(state.selectedTargetTiles, cols, rows) {
        BooleanArray(gridSize).also { a ->
            state.selectedTargetTiles.forEach { (c, r) -> a[r * cols + c] = true }
        }
    }
    val visibleArr = remember(state.visibleTiles, cols, rows) {
        BooleanArray(gridSize).also { a ->
            state.visibleTiles.forEach { (c, r) -> a[r * cols + c] = true }
        }
    }
    val exploredArr = remember(state.exploredTiles, cols, rows) {
        BooleanArray(gridSize).also { a ->
            state.exploredTiles.forEach { (c, r) -> a[r * cols + c] = true }
        }
    }
    val perimeterArr = remember(state.perimeterTiles, cols, rows) {
        BooleanArray(gridSize).also { a ->
            state.perimeterTiles.forEach { (c, r) -> a[r * cols + c] = true }
        }
    }
    val tileOverlayArr = remember(state.tileTypes, cols, rows) {
        arrayOfNulls<Color>(gridSize).also { a ->
            state.tileTypes.forEach { (pair, type) ->
                a[pair.second * cols + pair.first] = terrainOverlayColors[type]
            }
        }
    }
    val tilesArr = remember(tiles, cols, rows) {
        arrayOfNulls<ImageBitmap>(gridSize).also { a ->
            tiles.forEach { (pair, bmp) -> a[pair.second * cols + pair.first] = bmp }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ColorFieldBg)
            .onSizeChanged { fieldSize = it }
            // Pinch-to-zoom + two-finger pan
            .transformable(state = transformableState)
            // Scroll-wheel zoom — zooms towards the cursor position
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Scroll) {
                            val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scrollY == 0f) continue
                            val curZoom = zoomState.value
                            val factor  = if (scrollY < 0) 1.12f else 0.88f
                            val newZoom = (curZoom * factor).coerceIn(ZOOM_MIN, ZOOM_MAX)
                            val bcs = if (fieldSize.width > 0) fieldSize.width / gridCols.toFloat() else 0f
                            val cursor  = event.changes.firstOrNull()?.position
                            val cx = cursor?.x ?: (fieldSize.width  / 2f)
                            val cy = cursor?.y ?: (fieldSize.height / 2f)
                            val zf = newZoom / curZoom
                            val newMaxPanX = (gridCols * bcs * newZoom - fieldSize.width).coerceAtLeast(0f)
                            val newMaxPanY = (gridRows * bcs * newZoom - fieldSize.height).coerceAtLeast(0f)
                            val npx = ((panX.value + cx) * zf - cx).coerceIn(0f, newMaxPanX)
                            val npy = ((panY.value + cy) * zf - cy).coerceIn(0f, newMaxPanY)
                            zoomState.value = newZoom
                            scope.launch { panX.snapTo(npx) }
                            scope.launch { panY.snapTo(npy) }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            // Single-pointer tap + drag-to-pan
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var drag = androidx.compose.ui.geometry.Offset.Zero
                    var isDragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            // Pointer released — treat as tap if no drag occurred
                            if (!isDragging && fieldSize.width > 0) {
                                val cs  = (if (fieldSize.width > 0) fieldSize.width / gridCols.toFloat() else 0f) * zoomState.value
                                val tapX = down.position.x + panX.value
                                val tapY = down.position.y + panY.value
                                val col = (tapX / cs).toInt().coerceIn(0, gridCols - 1)
                                val row = (tapY / cs).toInt().coerceIn(0, gridRows - 1)
                                onCellTap(col, row)
                            }
                            break
                        }
                        val delta = change.position - change.previousPosition
                        drag += delta
                        if (!isDragging && drag.getDistance() > touchSlop) isDragging = true
                        if (isDragging) {
                            change.consume()
                            val cs  = (if (fieldSize.width > 0) fieldSize.width / gridCols.toFloat() else 0f) * zoomState.value
                            val mpx = (gridCols * cs - fieldSize.width).coerceAtLeast(0f)
                            val mpy = (gridRows * cs - fieldSize.height).coerceAtLeast(0f)
                            scope.launch {
                                panX.snapTo((panX.value - delta.x).coerceIn(0f, mpx))
                                panY.snapTo((panY.value - delta.y).coerceIn(0f, mpy))
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ox = panX.value
            val oy = panY.value
            // Square cells — base fills full width, multiplied by zoom
            val cs = (size.width / gridCols) * zoom
            val cw = cs
            val ch = cs

            // 1. Draw tile background — Throneroom images with tactical overlays
            val startC = (ox / cw).toInt().coerceAtLeast(0)
            val endC   = ((ox + size.width)  / cw).toInt().coerceAtMost(gridCols - 1)
            val startR = (oy / ch).toInt().coerceAtLeast(0)
            val endR   = ((oy + size.height) / ch).toInt().coerceAtMost(gridRows - 1)

            val fowActive = state.exploredTiles.isNotEmpty() || state.visibleTiles.isNotEmpty()

            for (r in startR..endR) {
                for (c in startC..endC) {
                    val screenX = c * cw - ox
                    val screenY = r * ch - oy

                    val tileIdx    = r * cols + c
                    val isAtk      = attackableArr[tileIdx]
                    val isSel      = selectedArr[tileIdx]
                    val tileBitmap = tilesArr[tileIdx]
                    val topLeft    = Offset(screenX, screenY)
                    val tileSize   = Size(cw, ch)

                    if (tileBitmap != null) {
                        drawImage(
                            image         = tileBitmap,
                            dstOffset     = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                            dstSize       = IntSize(tileSize.width.toInt() + 1, tileSize.height.toInt() + 1),
                            filterQuality = FilterQuality.Low,
                        )
                    } else {
                        val base = if ((c + r) % 2 == 0) ColorTileA else ColorTileB
                        drawRect(color = base, topLeft = topLeft, size = tileSize)
                    }

                    // Terrain type overlay
                    val terrainOverlay = tileOverlayArr[tileIdx]
                    if (terrainOverlay != null) drawRect(terrainOverlay, topLeft, tileSize)

                    when {
                        isSel -> drawRect(ColorTileSelFill, topLeft, tileSize)
                        isAtk -> drawRect(ColorTileAtkFill, topLeft, tileSize)
                    }
                    when {
                        isSel -> drawRect(ColorTileSelRimFull, topLeft, tileSize, style = Stroke(2.5f))
                        isAtk -> drawRect(ColorTileAtkRimFull, topLeft, tileSize, style = Stroke(2f))
                    }

                    // Fog of War overlay — applied on top of terrain and tactical highlights
                    if (fowActive) {
                        when {
                            !exploredArr[tileIdx] && !perimeterArr[tileIdx] ->
                                // Never seen and not adjacent to visible — solid black
                                drawRect(Color.Black, topLeft, tileSize)
                            !visibleArr[tileIdx] ->
                                // Explored or perimeter (1 tile beyond visible) — dark veil
                                drawRect(ColorFowExplored, topLeft, tileSize)
                        }
                    }
                }
            }

            // 2. Grid lines
            val gridW = gridCols * cw
            val gridH = gridRows * ch
            for (i in 0..gridCols) {
                val x = i * cw - ox
                if (x < 0 || x > size.width) continue
                drawLine(ColorTileGrid, Offset(x, 0f.coerceAtLeast(-oy)), Offset(x, (gridH - oy).coerceAtMost(size.height)), 0.5f)
            }
            for (j in 0..gridRows) {
                val y = j * ch - oy
                if (y < 0 || y > size.height) continue
                drawLine(ColorTileGrid, Offset(0f.coerceAtLeast(-ox), y), Offset((gridW - ox).coerceAtMost(size.width), y), 0.5f)
            }

            // 3. Units (skip the currently-moving unit; draw it separately at lerped position)
            val movingId = (animationEvent as? AnimationEvent.UnitMove)?.entityId
            state.units.forEach { unit ->
                // Hide enemy units outside current visible set
                if (unit.faction == Faction.ENEMY &&
                    Pair(unit.position.col, unit.position.row) !in state.visibleTiles) return@forEach
                if (unit.entityId != movingId) drawUnit(unit, cw, ch, ox, oy)
            }

            // 3b. Moving unit at interpolated position
            if (animationEvent is AnimationEvent.UnitMove) {
                val mv = animationEvent
                val t = moveProgress.value
                val cx = (mv.fromNormX + (mv.toNormX - mv.fromNormX) * t) * gridCols * cw - ox
                val cy = (mv.fromNormY + (mv.toNormY - mv.fromNormY) * t) * gridRows * ch - oy
                val movingUnit = state.units.find { it.entityId == mv.entityId }
                if (movingUnit != null) drawUnitAtCenter(movingUnit, cx, cy, minOf(cw, ch))
            }

            // 4. Projectile overlay
            if (animationEvent is AnimationEvent.ProjectileSkill) {
                drawProjectile(animationEvent, projectileProgress.value, gridCols * cw, gridRows * ch, ox, oy)
            }
        }

        // 5. Floating number text overlay
        if (fieldSize.width > 0) {
            val (nx, ny, text, color) = when (val e = animationEvent) {
                is AnimationEvent.FloatingDamage -> Quad(e.x, e.y, "-${e.amount}", damageColor(e.damageType))
                is AnimationEvent.FloatingHeal   -> Quad(e.x, e.y, "+${e.amount}", Color(0xFF3fb950))
                is AnimationEvent.FloatingMiss   -> Quad(e.x, e.y, "Miss!", Color(0xFFc9d1d9))
                else                             -> null
            } ?: return@Box

            val xPx = (nx * gridCols * cellW - panX.value).toInt()
            val yPx = (ny * gridRows * cellH - panY.value - 50 + floatOffsetY.value).toInt()
            Text(
                text       = text,
                color      = color.copy(alpha = floatAlpha.value),
                fontSize   = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier   = Modifier.absoluteOffset { IntOffset(xPx - 20, yPx) },
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun DrawScope.drawUnit(unit: UnitUiState, cellW: Float, cellH: Float, ox: Float, oy: Float) {
    val cx = (unit.position.col + 0.5f) * cellW - ox
    val cy = (unit.position.row + 0.5f) * cellH - oy
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
    gridW: Float,
    gridH: Float,
    ox: Float,
    oy: Float,
) {
    val px = (event.fromX + (event.toX - event.fromX) * progress) * gridW - ox
    val py = (event.fromY + (event.toY - event.fromY) * progress) * gridH - oy
    when (event.animationType) {
        AnimationType.MELEE -> {
            val color = meleeColor(event.skillId)
            drawCircle(color,                          6.dp.toPx(), Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.7f), 3.dp.toPx(), Offset(px, py))
        }
        else -> {
            val color = projectileColor(event.skillId)
            drawCircle(color.copy(alpha = 0.35f), 14.dp.toPx(), Offset(px, py))
            drawCircle(color,                      7.dp.toPx(),  Offset(px, py))
            drawCircle(Color.White.copy(alpha = 0.8f), 3.dp.toPx(), Offset(px, py))
        }
    }
}

private fun projectileColor(skillId: String): Color = when (skillId) {
    "fireball"      -> Color(0xFFFF6B00)
    "magic_missile" -> Color(0xFF58a6ff)
    "thorn_whip"    -> Color(0xFF3fb950)
    else            -> Color.White
}

private fun meleeColor(skillId: String): Color = when (skillId) {
    "basic_attack" -> Color(0xFFE3B341)   // amber/gold — fist impact
    else           -> Color(0xFFc9d1d9)   // light grey fallback
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
private fun BattleLogPanel(log: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val recent = log.takeLast(8)
    LaunchedEffect(log.size) { if (recent.isNotEmpty()) listState.animateScrollToItem(recent.lastIndex) }
    LazyColumn(
        state = listState,
        modifier = modifier
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
private fun UnitStatusRow(
    state: BattleUiState,
    onUnitTap: (de.jackbeback.pocketquest.ecs.core.EntityId) -> Unit,
) {
    val skillArmed = state.selectedSkill != null
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.units) { unit ->
            val cell = Pair(unit.position.col, unit.position.row)
            UnitStatusCard(
                unit = unit,
                isSkillArmed = skillArmed,
                isValidTarget = cell in state.attackableTiles,
                isPendingTarget = unit.entityId in state.pendingTargetIds,
                onTargetTap = { onUnitTap(unit.entityId) },
            )
        }
    }
}

@Composable
private fun UnitStatusCard(
    unit: UnitUiState,
    isSkillArmed: Boolean = false,
    isValidTarget: Boolean = false,
    isPendingTarget: Boolean = false,
    onTargetTap: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = when {
        isPendingTarget -> Color(0xFFd29922)          // gold — already queued
        isSkillArmed && isValidTarget -> ColorTileAtkRim  // red — tappable target
        unit.faction == Faction.PLAYER -> ColorPlayer
        else -> ColorEnemy
    }

    Column(
        modifier = Modifier
            .width(if (expanded && !isSkillArmed) 160.dp else 110.dp)
            .background(
                if (isSkillArmed && isValidTarget) Color(0xFF2a1010) else ColorFieldBg,
                RoundedCornerShape(6.dp),
            )
            .border(
                width = if (isSkillArmed && isValidTarget || isPendingTarget) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(8.dp)
            .pointerInput(isSkillArmed) {
                detectTapGestures {
                    if (isSkillArmed) onTargetTap() else expanded = !expanded
                }
            },
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
        if (unit.conditions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            ConditionBadges(conditions = unit.conditions)
        }
        // Expanded attributes panel (tap card to toggle)
        if (expanded && unit.stats != null) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color(0xFF30363d), thickness = 0.5.dp)
            Spacer(Modifier.height(4.dp))
            val s = unit.stats
            val attrs = listOf(
                "STR" to s.str, "DEX" to s.dex, "CON" to s.con,
                "INT" to s.intelligence, "WIS" to s.wis, "CHA" to s.cha,
            )
            attrs.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    pair.forEach { (abbrev, value) ->
                        val mod = attributeModifier(value)
                        val modStr = if (mod >= 0) "+$mod" else "$mod"
                        Text(
                            "$abbrev $value ($modStr)",
                            color = Color(0xFF8b949e),
                            fontSize = 9.sp,
                        )
                    }
                }
            }
            Text("AC ${s.ac}", color = Color(0xFF8b949e), fontSize = 9.sp)
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
    playerMana: de.jackbeback.pocketquest.ecs.components.core.ManaComponent,
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
        val canCast = phase == TurnPhase.PlayerPhase && !isLocked && playerMana.current > 0

        Button(
            onClick = onOpenSkillSheet,
            enabled = canCast,
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
            isLocked                               -> "…"
            phase != TurnPhase.PlayerPhase         -> ""
            playerMana.current > 0 && movesRemaining > 0 -> "Move or cast a spell"
            playerMana.current > 0                 -> "Cast a spell (${playerMana.current} MP)"
            movesRemaining > 0                     -> "${movesRemaining} move(s) left — no mana"
            else                                   -> "End your turn"
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

// ── Directional D-pad ────────────────────────────────────────────────────────

@Composable
private fun DPad(
    phase: TurnPhase,
    movesRemaining: Int,
    isLocked: Boolean,
    playerPos: PositionComponent?,
    reachableTiles: Set<Pair<Int, Int>>,
    onMoveDirection: (Int, Int) -> Unit,
) {
    val canMove = phase == TurnPhase.PlayerPhase && movesRemaining > 0 && !isLocked && playerPos != null

    fun canDir(dx: Int, dy: Int): Boolean {
        if (!canMove || playerPos == null) return false
        return Pair(playerPos.col + dx, playerPos.row + dy) in reachableTiles
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorFieldBg)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            DPadButton("▲", canDir(0, -1))  { onMoveDirection(0, -1) }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                DPadButton("◄", canDir(-1, 0)) { onMoveDirection(-1, 0) }
                Box(Modifier.size(36.dp))
                DPadButton("►", canDir(1, 0))  { onMoveDirection(1, 0) }
            }
            DPadButton("▼", canDir(0, 1))   { onMoveDirection(0, 1) }
        }
    }
}

@Composable
private fun DPadButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1f2d3d),
            contentColor = ColorPlayer,
            disabledContainerColor = Color(0xFF161b22),
            disabledContentColor = Color(0xFF484f58),
        ),
    ) {
        Text(label, fontSize = 14.sp)
    }
}

// ── Post-battle result overlay ────────────────────────────────────────────────

private val ColorRelicBorder   = Color(0xFF30363d)
private val ColorRelicSelected = Color(0xFFd29922)
private val ColorRelicBg       = Color(0xFF0d1117)

@Composable
private fun BattleResultOverlay(
    result: BattleResult,
    onRelicChosen: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val accentColor = if (result.victory) ColorHpBar else ColorEnemy
    val titleText   = if (result.victory) "Victory!" else "Defeated"
    var selectedRelicId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .background(ColorFieldBg, RoundedCornerShape(12.dp))
                .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = titleText,
                color = accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
            )

            if (result.victory) {
                // Stats rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enemies defeated", color = Color(0xFF8b949e), fontSize = 13.sp)
                    Text("${result.enemiesDefeated}", color = Color(0xFFc9d1d9), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("XP earned", color = Color(0xFF8b949e), fontSize = 13.sp)
                    Text("+${result.xpEarned}", color = Color(0xFFd29922), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                if (result.leveledUp) {
                    Text(
                        text = "Level Up!  →  Lv ${result.newLevel}",
                        color = Color(0xFFd29922),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(Color(0xFF2d2a00), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                // Relic pick section
                if (result.relicCandidates.isNotEmpty()) {
                    HorizontalDivider(color = Color(0xFF30363d), thickness = 0.5.dp)
                    Text(
                        "Choose a Relic",
                        color = Color(0xFFc9d1d9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        result.relicCandidates.forEach { relic ->
                            RelicCard(
                                relic = relic,
                                selected = selectedRelicId == relic.id,
                                modifier = Modifier.weight(1f),
                                onSelect = { selectedRelicId = relic.id },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    selectedRelicId?.let { onRelicChosen(it) }
                    onContinue()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (result.victory) "Continue" else "Try Again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RelicCard(
    relic: Relic,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val borderColor = if (selected) ColorRelicSelected else ColorRelicBorder
    val bgColor     = if (selected) Color(0xFF2d2a00) else ColorRelicBg
    Column(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .pointerInput(Unit) { detectTapGestures { onSelect() } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = relicIcon(relic.id),
            fontSize = 22.sp,
        )
        Text(
            text = relic.name,
            color = if (selected) ColorRelicSelected else Color(0xFFc9d1d9),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            text = relic.description,
            color = Color(0xFF8b949e),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun relicIcon(relicId: String): String = when (relicId) {
    "ancient_tome"   -> "📖"
    "iron_will"      -> "🛡"
    "war_trophy"     -> "⚔"
    "mage_stone"     -> "💎"
    "well_of_power"  -> "🌊"
    "scholars_ring"  -> "💍"
    "blood_pact"     -> "❤"
    "arcane_focus"   -> "✨"
    else             -> "★"
}

