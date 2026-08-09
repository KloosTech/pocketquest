package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import de.jackbeback.pocketquest.core.ai.chooseAction
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.PreviewResult
import de.jackbeback.pocketquest.core.model.PropLayer
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.TileType
import de.jackbeback.pocketquest.core.model.WallEdge
import de.jackbeback.pocketquest.ui.assets.GameAssetManifest
import de.jackbeback.pocketquest.ui.assets.GameSpriteLoader
import de.jackbeback.pocketquest.core.rules.action.allActions
import de.jackbeback.pocketquest.core.rules.action.perform
import de.jackbeback.pocketquest.core.rules.action.preview
import de.jackbeback.pocketquest.core.rules.beginCombat
import de.jackbeback.pocketquest.core.rules.combatOutcome
import de.jackbeback.pocketquest.core.rules.moveEntityTo
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import de.jackbeback.pocketquest.core.rules.resolver.run as runResolver
import de.jackbeback.pocketquest.core.rules.stat.stats
import de.jackbeback.pocketquest.core.rules.targeting.affectedBy
import de.jackbeback.pocketquest.core.rules.targeting.allThreatenedTiles
import de.jackbeback.pocketquest.core.rules.targeting.checkCombatStart
import de.jackbeback.pocketquest.core.rules.targeting.findPath
import de.jackbeback.pocketquest.core.rules.targeting.legalTargets
import de.jackbeback.pocketquest.core.rules.targeting.updateRevealedTiles
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TILE_PX = 48f

/** doc16: "integer scale factors" keep pixel art crisp — pan/zoom snaps to these steps, never a free/fractional value. */
const val MIN_ZOOM = 1
const val MAX_ZOOM = 4

/** doc15's "comfortable inner rectangle" — the active entity may roam this middle fraction of the viewport before the camera nudges to keep it in view. */
private const val CAMERA_DEAD_ZONE_MARGIN = 0.2f

/** How much of the viewport an AI actor+target pair must fit within (screen px, at current zoom) before the camera frames both instead of prioritising the target — doc15's "if they do not both fit, prioritise the target." */
private const val AI_FRAME_FIT_FRACTION = 0.7f


private fun colorFor(faction: Faction?): Color = when (faction) {
    Faction.Player -> Color(0xFF2196F3)
    Faction.Enemy -> Color(0xFFE53935)
    Faction.Neutral -> Color(0xFF9E9E9E)
    null -> Color(0xFF757575)
}

/**
 * doc15's Idle -> ActionSelected -> TargetPicked -> Confirm state machine, the actual player-
 * facing loop that was missing entirely before this: :app used to precompute a whole scripted
 * battle and hand [App] a fixed events/finalState pair to replay. Nothing called
 * legalTargets/canPerform/preview/perform in response to input. This is that loop, for real.
 */
private sealed interface Selection {
    data object None : Selection
    data class ActionPicked(val actionId: ActionId, val legal: Set<GridPos>) : Selection
    data class TargetPicked(val actionId: ActionId, val ctx: ActionCtx, val preview: PreviewResult) : Selection
}

/**
 * doc15: "who acts next, always visible" — now an overlay pinned to the top of the board itself
 * (not a separate row above it, per the user's explicit ask), so it stays on screen without shrinking
 * the map's own viewport. One token per `state.turn.order` entry (true interleaved initiative, not
 * side-based phases — every actor, not just the party, belongs here), the active one ringed.
 * [onSelectEntity] replaces doc15's original "a 'centre on active' button" — every token is now
 * clickable and centers the camera on THAT entity, not only the currently-active one, a strictly more
 * useful version of the same ask (per the user's explicit request to remove the separate button).
 * [onOpenLog] is doc15's battle log ask: "reachable from the turn strip" — placed at the strip's own
 * trailing end, past every turn token, per the user's explicit request. [threatOverlayOn]/
 * [onToggleThreat] is doc15's threat overlay toggle — "the highest-value quality-of-life feature
 * there is, and it is cheap."
 */
@Composable
private fun TurnOrderStrip(
    state: GameState,
    colors: Map<EntityId, Color>,
    onSelectEntity: (EntityId) -> Unit,
    onOpenLog: () -> Unit,
    threatOverlayOn: Boolean,
    onToggleThreat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp).background(PAPER_SHEET).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .border(1.dp, INK)
                .background(if (threatOverlayOn) Color(0xFFB71C1C) else PAPER)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleThreat)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            BasicText("⚠", style = TextStyle(color = if (threatOverlayOn) Color.White else INK, fontSize = 14.sp))
        }
        val activeId = state.turn.order.getOrNull(state.turn.activeIndex)
        state.turn.order.forEach { id ->
            val entity = state.byId[id] ?: return@forEach
            // Nothing removes a dead entity from turn.order in THIS demo (DestroyEntity exists as
            // an engine primitive since doc17 3.1, but nothing in the demo catalog calls it) —
            // endTurn already skips a dead entity's turn, so this strip just needs to render that
            // visually instead of showing it as a normal live token.
            val alive = (entity.health?.current ?: 1) > 0
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(32.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelectEntity(id) },
                contentAlignment = Alignment.Center,
            ) {
                if (id == activeId) {
                    Box(Modifier.size(32.dp).border(2.dp, INK, CircleShape))
                }
                Box(Modifier.size(22.dp).background((colors[id] ?: Color.Gray).copy(alpha = if (alive) 1f else 0.3f), CircleShape))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        InkButton("☰", onClick = onOpenLog)
    }
}

/**
 * doc15: "3 portraits, HP/mana, controller" — reads live `GameState`, not `RunState` (invariant 8
 * in doc11: `PartyMember.hp` is stale by design mid-encounter). Controller toggle (doc15's
 * AI/manual flip) is deferred — nothing in the demo catalog needs a party member ever AI-driven.
 */
@Composable
private fun PartyBar(state: GameState, catalog: Catalog, modifier: Modifier = Modifier) {
    val party = state.entities.filter { it.actor?.faction == Faction.Player }
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp).background(PAPER_SHEET).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        party.forEach { entity ->
            val s = entity.stats(catalog)
            Column(modifier = Modifier.padding(end = 16.dp)) {
                BasicText(catalog.archetype(entity.archetype).name, style = TextStyle(color = INK, fontSize = 12.sp))
                BasicText("HP ${entity.health?.current}/${s.maxHp}", style = TextStyle(color = INK_FAINT, fontSize = 10.sp))
                entity.resources?.let {
                    BasicText("Mana ${it.mana}/${s.maxMana}", style = TextStyle(color = INK_FAINT, fontSize = 10.sp))
                }
            }
        }
    }
}

/**
 * doc15's Inspect bottom-sheet state: read-only stats/statuses for whatever the player tapped
 * outside of an active targeting flow. Deliberately doesn't try to fake "threat range" or "last
 * action" — doc15 asks for both on an enemy, but nothing tracks either yet (no committed-AI-intent
 * concept exists — see doc15's own "Threat overlay, and the intent question"), so showing them
 * would be invented data, not a read of something real.
 */
@Composable
private fun InspectPanel(entityId: EntityId, state: GameState, catalog: Catalog, onBack: () -> Unit) {
    val entity = state.byId[entityId]
    if (entity == null) {
        BasicText("(no longer on the board)", style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
        Spacer(modifier = Modifier.size(8.dp))
        InkButton("Back", onClick = onBack)
        return
    }
    val s = entity.stats(catalog)
    BasicText(
        "${catalog.archetype(entity.archetype).name} — ${entity.actor?.faction ?: Faction.Neutral}",
        style = TextStyle(color = INK, fontSize = 16.sp),
    )
    Spacer(modifier = Modifier.size(4.dp))
    BasicText(
        "HP ${entity.health?.current}/${s.maxHp} · AC ${s.armorClass}" +
            (entity.resources?.let { " · AP ${it.ap}/${s.maxAp} · Mana ${it.mana}/${s.maxMana}" } ?: ""),
        style = TextStyle(color = INK_FAINT, fontSize = 13.sp),
    )
    if (entity.statuses.isNotEmpty()) {
        Spacer(modifier = Modifier.size(8.dp))
        entity.statuses.forEach { status ->
            BasicText("${catalog.statusDef(status.def).name} ×${status.stacks} (${status.expiry})", style = TextStyle(color = INK_FAINT, fontSize = 12.sp))
        }
    }
    Spacer(modifier = Modifier.size(12.dp))
    InkButton("Back", onClick = onBack)
}

/**
 * doc15's battle log: "reachable from the turn strip," full-screen so it reads as its own place
 * rather than squeezed into the Peek sheet. The background `clickable` with no action is there
 * purely to consume taps — without it, a tap on this panel would fall through to the board/sheet
 * underneath, since a plain `background()` doesn't claim pointer input on its own.
 */
@Composable
private fun CombatLogPanel(log: List<LogEntry>, onClose: () -> Unit) {
    // Resets to newest-first each time the panel reopens — a per-session display preference, not
    // state worth persisting across opens.
    var newestFirst by remember { mutableStateOf(true) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PAPER)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Battle Log", style = TextStyle(color = INK, fontSize = 18.sp))
                Spacer(modifier = Modifier.weight(1f))
                InkButton("Close", onClick = onClose)
            }
            // The list is stored newest-first (each event is prepended) — spelled out explicitly
            // rather than left implicit, since "top vs bottom = newest" isn't a universal log
            // convention. Clickable to flip the displayed order without re-fetching anything.
            BasicText(
                if (newestFirst) "▾ newest first" else "▴ oldest first",
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { newestFirst = !newestFirst },
                style = TextStyle(color = INK_FAINT, fontSize = 11.sp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            val displayed = if (newestFirst) log else log.asReversed()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(displayed) { entry -> BasicText(entry.text, style = TextStyle(color = entry.category.color(), fontSize = 13.sp)) }
            }
        }
    }
}

/** A resolved sheet + its column count — matches a manifest floor-texture entry's shape. */
private data class TexSheet(val bitmap: ImageBitmap, val cols: Int)

private data class PropSprite(val bitmap: ImageBitmap, val tilesW: Int, val tilesH: Int)

/**
 * Every image [Board] needs for one [BattleMap] — a floor-texture sheet plus a sprite per distinct
 * prop id actually placed on this map (not the whole catalog of placeable props, no point decoding
 * ones this map never uses). Wall rendering has no image at all — [drawWallHatch] draws it live.
 * Null fields mean "not configured" or "failed to load," and [Board] falls back to today's
 * flat-color rendering for those — same "missing asset just means no image" contract
 * `GameSpriteLoader` already establishes, never a crash.
 */
private data class MapAssets(val floor: TexSheet?, val props: Map<String, PropSprite>)

/**
 * Loads once per distinct [map] (`produceState`'s key in [Board]'s caller) — `:designer`'s
 * `MapEditorPanel.kt` proved this exact shape already (`floorSwatch`/prop-thumbnail lookups), this
 * is the same idea via [GameSpriteLoader]'s suspend/Compose-Resources loading instead of raw files.
 */
private suspend fun loadMapAssets(map: BattleMap): MapAssets {
    val manifest = GameAssetManifest.load()
    val floor = map.floorTexture?.let { id ->
        val meta = manifest.floorTexture(id) ?: return@let null
        GameSpriteLoader.load(meta.file)?.let { TexSheet(it, meta.tilesW ?: 1) }
    }
    val props = map.props.map { it.prop.raw }.distinct().mapNotNull { id ->
        val meta = manifest.prop(id) ?: return@mapNotNull null
        val bitmap = GameSpriteLoader.load(meta.file) ?: return@mapNotNull null
        id to PropSprite(bitmap, meta.tilesW ?: 1, meta.tilesH ?: 1)
    }.toMap()
    return MapAssets(floor, props)
}

/**
 * Picks a stable-but-varied sub-cell from a multi-cell texture sheet using the tile's own grid
 * position, so neighbouring cells don't all show the identical sub-image — the exact technique
 * `MapEditorPanel.kt`'s `floorSwatch`/`FloorPatch` already proved for the editor's own preview,
 * ported rather than reinvented.
 */
private fun subPatch(sheet: TexSheet, col: Int, row: Int): Pair<IntOffset, IntSize> {
    val cell = sheet.bitmap.width / sheet.cols
    val sc = (col + row) % sheet.cols
    val sr = (col * 3 + row * 5) % sheet.cols
    return IntOffset(sc * cell, sr * cell) to IntSize(cell, cell)
}

private fun DrawScope.drawTexturedCell(sheet: TexSheet, col: Int, row: Int, rect: Rect) {
    val (srcOffset, srcSize) = subPatch(sheet, col, row)
    drawImage(
        sheet.bitmap,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
    )
}

/**
 * doc07: "the grid is one Canvas, not 400 composables." Grid lines and
 * blocked tiles come from [BattleMap] (static for the battle); token
 * positions/HP/scale/alpha come from [VisualWorld] (animated), as is
 * `world.camera`/`world.zoom` — doc15's "pan + zoom, culled to viewport."
 * [legalTiles] highlights doc15's "Reachable"/targeting mode; taps only
 * matter while something is selected — [onTileTap] is a no-op otherwise.
 *
 * [canPan] is doc15's Camera rule: "never moves while the player is in
 * ActionSelected or TargetPicked" — a drag gesture during target-picking is
 * ambiguous with trying to tap a highlighted tile precisely, so manual
 * pan/zoom is disabled entirely in those states, not just auto-follow.
 */
@Composable
private fun Board(
    map: BattleMap,
    mapAssets: MapAssets?,
    world: VisualWorld,
    colors: Map<EntityId, Color>,
    legalTiles: Set<GridPos>,
    threatTiles: Set<GridPos>,
    selectedTile: GridPos?,
    canPan: Boolean,
    revealedTiles: Set<GridPos>,
    entityPositions: Map<EntityId, GridPos>,
    onTileTap: (GridPos) -> Unit,
    onViewportSizeChanged: (Size) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // detectTapGestures's double-tap disambiguation wait never resolved a tap to onTap in this
    // environment (confirmed empirically — zero taps registered across many real clicks, while a
    // plain Modifier.clickable fired reliably every time). clickable's own tap recognition works,
    // so it drives the actual click; a separate lightweight down-position tracker (no gesture
    // disambiguation, just "where was the last press") supplies the screen coordinate, converted
    // through the current camera/zoom to a world position and then a tile.
    var lastPressPos by remember { mutableStateOf(Offset.Zero) }
    // clickable's onClick lambda has no DrawScope/PointerInputScope receiver, so it can't read a
    // Canvas-local `size` the way the draw calls below do — the viewport size has to be captured
    // into ordinary Compose state via onSizeChanged instead.
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    Canvas(
        modifier = modifier
            .onSizeChanged {
                viewportSize = it.toSize()
                onViewportSizeChanged(viewportSize)
            }
            // Single-finger drag pans, two-finger pinch zooms — detectTransformGestures already
            // gates both behind its own touch-slop, so a plain tap below that threshold never
            // consumes the down/up pair and clickable (below) still sees and fires it normally.
            // Zoom snaps to MIN_ZOOM..MAX_ZOOM integer steps every frame of the pinch rather than
            // free-floating then settling — doc15's own acknowledged "feel stiffer" tradeoff for
            // snapped steps, not a missing feature.
            .pointerInput(canPan) {
                if (!canPan) return@pointerInput
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val steppedZoom = (world.zoom.targetValue * zoomChange).roundToInt().coerceIn(MIN_ZOOM, MAX_ZOOM)
                    scope.launch { world.zoom.snapTo(steppedZoom.toFloat()) }
                    scope.launch { world.camera.snapTo(world.camera.value - pan / world.zoom.targetValue) }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastPressPos = down.position
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val worldPos = screenToWorld(lastPressPos, world.camera.targetValue, world.zoom.targetValue, viewportSize)
                onTileTap(worldPos.toGridPos(TILE_PX))
            }
            .scrollWheelZoom(canPan) { direction ->
                val next = (world.zoom.targetValue.roundToInt() + direction).coerceIn(MIN_ZOOM, MAX_ZOOM)
                scope.launch { world.zoom.animateTo(next.toFloat()) }
            },
    ) {
        val camera = world.camera.value
        val zoom = world.zoom.value
        drawGrid(map, mapAssets, camera, zoom)
        drawProps(map, mapAssets, PropLayer.Floor, camera, zoom)
        threatTiles.forEach { pos -> drawThreatHatch(pos, camera, zoom) }
        legalTiles.forEach { pos -> drawHighlight(pos, camera, zoom) }
        selectedTile?.let { pos -> drawSelectedTile(pos, camera, zoom) }
        drawProps(map, mapAssets, PropLayer.Object, camera, zoom)
        world.entities.forEach { (id, entity) ->
            val pos = entityPositions[id]
            if (map.fogOfWar && pos != null && pos !in revealedTiles) return@forEach
            drawEntity(entity, colors[id] ?: Color.Gray, camera, zoom)
        }
        world.overlays.forEach { overlay ->
            drawOverlay(overlay, camera, zoom)
        }
        world.markers.forEach { marker ->
            drawMarker(marker.marker, camera, zoom)
        }
        drawProps(map, mapAssets, PropLayer.Overhead, camera, zoom)
        drawFogOfWar(map, revealedTiles, camera, zoom)
    }
}

/** [layer]-filtered pass over [BattleMap.props] — called three times from [Board] (Floor before highlights, Object before entities, Overhead after everything) so a single prop list drives every z-order slice without three separate stored lists. */
private fun DrawScope.drawProps(map: BattleMap, mapAssets: MapAssets?, layer: PropLayer, camera: Offset, zoom: Float) {
    val assets = mapAssets ?: return
    val screenTile = TILE_PX * zoom
    map.props.forEach { placement ->
        if (placement.layer != layer) return@forEach
        val sprite = assets.props[placement.prop.raw] ?: return@forEach
        val topLeft = worldToScreen(Offset(placement.at.col * TILE_PX, placement.at.row * TILE_PX), camera, zoom, size)
        drawImage(
            sprite.bitmap,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize((sprite.tilesW * screenTile).roundToInt(), (sprite.tilesH * screenTile).roundToInt()),
        )
    }
}

/** doc15: "cull to the viewport — draw only visible tiles plus one row of margin." */
private fun visibleTileBounds(map: BattleMap, camera: Offset, zoom: Float, viewport: Size): Pair<IntRange, IntRange> {
    val topLeftWorld = screenToWorld(Offset.Zero, camera, zoom, viewport)
    val bottomRightWorld = screenToWorld(Offset(viewport.width, viewport.height), camera, zoom, viewport)
    val cols = ((topLeftWorld.x / TILE_PX).toInt() - 1).coerceAtLeast(0)..((bottomRightWorld.x / TILE_PX).toInt() + 1).coerceAtMost(map.width - 1)
    val rows = ((topLeftWorld.y / TILE_PX).toInt() - 1).coerceAtLeast(0)..((bottomRightWorld.y / TILE_PX).toInt() + 1).coerceAtMost(map.height - 1)
    return cols to rows
}

private fun DrawScope.drawGrid(map: BattleMap, mapAssets: MapAssets?, camera: Offset, zoom: Float) {
    val viewport = size
    val (cols, rows) = visibleTileBounds(map, camera, zoom, viewport)
    if (cols.isEmpty() || rows.isEmpty()) return
    fun toScreen(world: Offset) = worldToScreen(world, camera, zoom, viewport)
    val screenTile = TILE_PX * zoom

    // Floor fill drawn before grid lines so a textured cell never paints over the line under it —
    // MapEditorPanel.kt's own drawTerrainCell/grid-line split settled this ordering first, ported
    // here rather than rediscovering it via the same "grid lines missing" bug.
    val floorSheet = mapAssets?.floor
    if (floorSheet != null) {
        for (col in cols.first..cols.last) {
            for (row in rows.first..rows.last) {
                val pos = GridPos(col, row)
                if (map.tileAt(pos) == TileType.Wall) continue
                val rect = Rect(toScreen(Offset(col * TILE_PX, row * TILE_PX)), Size(screenTile, screenTile))
                drawTexturedCell(floorSheet, col, row, rect)
            }
        }
    }

    val yTop = toScreen(Offset(0f, rows.first * TILE_PX)).y
    val yBottom = toScreen(Offset(0f, (rows.last + 1) * TILE_PX)).y
    for (col in cols.first..cols.last + 1) {
        val x = toScreen(Offset(col * TILE_PX, 0f)).x
        drawLine(INK_FAINT, Offset(x, yTop), Offset(x, yBottom))
    }
    val xLeft = toScreen(Offset(cols.first * TILE_PX, 0f)).x
    val xRight = toScreen(Offset((cols.last + 1) * TILE_PX, 0f)).x
    for (row in rows.first..rows.last + 1) {
        val y = toScreen(Offset(0f, row * TILE_PX)).y
        drawLine(INK_FAINT, Offset(xLeft, y), Offset(xRight, y))
    }

    // Walls drawn LAST, after grid lines, so they always paint fully over whatever grid line just
    // crossed that cell — previously drawn first, so every grid line was visible on top of a wall
    // (worst inside a hatched wall: drawWallHatch is only sparse hand-drawn strokes with no solid
    // backing of its own, so the grid showed clean through the gaps between strokes even once this
    // was drawn last). An opaque PAPER base fill under the hatch (same background tone used
    // everywhere else) closes those gaps; a flat (non-hatch) wall is already an opaque INK rect.
    //
    // drawWallHatch clips each Wall cell's strokes to that cell's own rect (found live: an earlier
    // whole-viewport "continuous field + punch every floor cell on top" version scanned the entire
    // visible area every frame regardless of how little of it was actually walls, heavy enough to
    // stutter panning/clicking) — no bleed past a wall cell's edge, so floor cells need no special
    // handling here at all, same as before this feature existed.
    if (map.wallHatch) {
        for (col in cols.first..cols.last) {
            for (row in rows.first..rows.last) {
                if (map.tileAt(GridPos(col, row)) != TileType.Wall) continue
                val rect = Rect(toScreen(Offset(col * TILE_PX, row * TILE_PX)), Size(screenTile, screenTile))
                drawRect(color = PAPER, topLeft = rect.topLeft, size = rect.size)
            }
        }
        drawWallHatch(isWall = { map.tileAt(it) == TileType.Wall }, cols = cols, rows = rows, tilePx = TILE_PX, zoom = zoom, ink = INK, toScreen = ::toScreen)
    } else {
        for (col in cols.first..cols.last) {
            for (row in rows.first..rows.last) {
                val pos = GridPos(col, row)
                if (map.tileAt(pos) != TileType.Wall) continue
                val rect = Rect(toScreen(Offset(col * TILE_PX, row * TILE_PX)), Size(screenTile, screenTile))
                drawRect(color = INK, topLeft = rect.topLeft, size = rect.size)
            }
        }
    }
    // Automatic outline around every Wall mass — a no-op visually on a flat wall (same INK color as
    // its own fill) but is what gives a hatched wall the clean solid border authored in :designer,
    // without a WallEdge hand-placed around every hatch region.
    for ((a, b) in wallOutlineSegments(map, cols, rows)) {
        drawLine(INK, toScreen(a), toScreen(b), strokeWidth = 4f * zoom)
    }
    // doc16's thin room-divider walls (WallEdge, layered on top of the whole-cell TileType.Wall
    // above) blocked movement/LoS correctly from the moment the engine gained them, but nothing
    // ever drew them here — a playtest launched from :designer showed an invisible wall.
    map.wallEdges.forEach { edge ->
        if (edge.pos.col !in cols || edge.pos.row !in rows) return@forEach
        val (a, b) = wallSegment(edge)
        drawLine(INK, toScreen(a), toScreen(b), strokeWidth = 4f * zoom)
    }
}

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
 * Derived, not authored — same as `:designer`'s `MapEditorPanel.kt`'s `wallOutlineSegments`: every
 * side of a whole-tile [TileType.Wall] cell bordering a non-Wall cell gets a solid outline, so a
 * painted Wall mass reads as one solid building with a clean border instead of needing a `WallEdge`
 * hand-placed around every hatch region. Culled to the visible [cols]/[rows] like `drawWallHatch`.
 */
private fun wallOutlineSegments(map: BattleMap, cols: IntRange, rows: IntRange): List<Pair<Offset, Offset>> {
    val segments = mutableListOf<Pair<Offset, Offset>>()
    for (col in cols) {
        for (row in rows) {
            val pos = GridPos(col, row)
            if (map.tileAt(pos) != TileType.Wall) continue
            for (side in Side.entries) {
                if (map.tileAt(pos.neighbor(side)) != TileType.Wall) segments += wallSegment(WallEdge(pos, side))
            }
        }
    }
    return segments
}

/** doc16: "Reachable — dotted ink outline, 8% warm tint" — a faint fill plus a dashed ink border, not a flat color fill. */
private fun DrawScope.drawHighlight(pos: GridPos, camera: Offset, zoom: Float) {
    val topLeft = worldToScreen(Offset(pos.col * TILE_PX, pos.row * TILE_PX), camera, zoom, size)
    val tileSize = Size(TILE_PX * zoom, TILE_PX * zoom)
    drawRect(color = INK.copy(alpha = 0.08f), topLeft = topLeft, size = tileSize)
    drawRect(
        color = INK,
        topLeft = topLeft,
        size = tileSize,
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
    )
}

/** The tile picked in Selection.TargetPicked, before Confirm — a solid green tint+border, deliberately not the dashed ink "Reachable" style so a confirmed-looking pick reads differently from "you could tap here." */
private fun DrawScope.drawSelectedTile(pos: GridPos, camera: Offset, zoom: Float) {
    val topLeft = worldToScreen(Offset(pos.col * TILE_PX, pos.row * TILE_PX), camera, zoom, size)
    val tileSize = Size(TILE_PX * zoom, TILE_PX * zoom)
    val green = Color(0xFF2E7D32)
    drawRect(color = green.copy(alpha = 0.25f), topLeft = topLeft, size = tileSize)
    drawRect(color = green, topLeft = topLeft, size = tileSize, style = Stroke(width = 3f))
}

/** doc16's visual spec for the threat overlay: "Enemy threat — Diagonal hatch, only while the threat overlay is on." */
private fun DrawScope.drawThreatHatch(pos: GridPos, camera: Offset, zoom: Float) {
    val topLeft = worldToScreen(Offset(pos.col * TILE_PX, pos.row * TILE_PX), camera, zoom, size)
    val tileSize = TILE_PX * zoom
    val color = Color(0xFFB71C1C).copy(alpha = 0.5f)
    clipRect(topLeft.x, topLeft.y, topLeft.x + tileSize, topLeft.y + tileSize) {
        val step = tileSize / 4f
        for (i in -3..3) {
            val offset = i * step
            drawLine(
                color = color,
                start = Offset(topLeft.x + offset, topLeft.y + tileSize),
                end = Offset(topLeft.x + offset + tileSize, topLeft.y),
                strokeWidth = 2f,
            )
        }
    }
}

/** Fog of war — drawn last so it covers terrain, props, highlights and entities alike on any tile never revealed. */
private fun DrawScope.drawFogOfWar(map: BattleMap, revealedTiles: Set<GridPos>, camera: Offset, zoom: Float) {
    if (!map.fogOfWar) return
    val viewport = size
    val (cols, rows) = visibleTileBounds(map, camera, zoom, viewport)
    if (cols.isEmpty() || rows.isEmpty()) return
    val tileSize = Size(TILE_PX * zoom, TILE_PX * zoom)
    for (col in cols) {
        for (row in rows) {
            val pos = GridPos(col, row)
            if (pos in revealedTiles) continue
            val topLeft = worldToScreen(Offset(col * TILE_PX, row * TILE_PX), camera, zoom, viewport)
            drawRect(color = INK, topLeft = topLeft, size = tileSize)
        }
    }
}

private fun DrawScope.drawEntity(entity: VisualEntity, color: Color, camera: Offset, zoom: Float) {
    drawCircle(
        color = color,
        radius = TILE_PX * zoom * 0.35f * entity.scale.value,
        center = worldToScreen(entity.pos.value, camera, zoom, size),
        alpha = entity.alpha.value,
    )
}

private fun DrawScope.drawOverlay(overlay: Overlay, camera: Offset, zoom: Float) {
    // No text-in-Canvas dependency pulled in for one debug number — a small colored square
    // stands in for the real floating-number readout a font/text-measurer would draw.
    val color = if (overlay.amount < 0) Color(0xFFB71C1C) else Color(0xFF2E7D32)
    val screenPos = worldToScreen(overlay.pos, camera, zoom, size)
    val screenTile = TILE_PX * zoom
    drawRect(color = color, topLeft = screenPos + Offset(screenTile * 0.3f, -screenTile * 0.6f), size = Size(screenTile * 0.25f, screenTile * 0.25f))
}

/** doc15: "an arc from the original target to the tank" (DamageRedirected) / "a blocked flash on the affected tile" (Fizzled, Rejection.Blocked). */
private fun DrawScope.drawMarker(marker: Marker, camera: Offset, zoom: Float) {
    when (marker) {
        is Marker.Arc -> drawLine(
            color = INK,
            start = worldToScreen(marker.from, camera, zoom, size),
            end = worldToScreen(marker.to, camera, zoom, size),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
        )
        is Marker.TileFlash -> drawRect(
            color = Color(0xFFB71C1C).copy(alpha = 0.35f),
            topLeft = worldToScreen(Offset(marker.pos.col * TILE_PX, marker.pos.row * TILE_PX), camera, zoom, size),
            size = Size(TILE_PX * zoom, TILE_PX * zoom),
        )
    }
}

/**
 * Owns the whole live game: state, the persistent [AnimationPlayer] (never `close()`d — this is
 * the "keeps taking new enqueue() calls across a whole session" case the player's own doc comment
 * anticipated), and the player-input loop. A human's turn drives through [Selection]; an AI turn
 * runs to completion automatically via [runAiTurns] with the same perform()/EndTurn calls a human
 * action uses, so there is exactly one code path for "an entity acted," not two.
 *
 * Layout is doc15's portrait anatomy: turn-order strip pinned at top, board in the middle, party
 * bar, then the bottom sheet. Bottom sheet has Peek (name/HP/AP/mana + action bar) and Inspect
 * (tap something outside an active targeting flow — read-only stats/statuses) — Prompt
 * (StepResult.AwaitingInput) is still deferred, nothing in the demo catalog ever triggers it.
 */
@Composable
fun App(initialState: GameState, catalog: Catalog, onEncounterEnd: (GameState) -> Unit = {}) {
    var state by remember { mutableStateOf(initialState) }
    var ended by remember { mutableStateOf(false) }
    val world = remember { VisualWorld(initialState, TILE_PX) }
    val player = remember { AnimationPlayer(world) }
    val colors = remember(initialState) { initialState.entities.associate { it.id to colorFor(it.actor?.faction) } }
    val log = remember { mutableStateListOf<LogEntry>() }
    var logOpen by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection>(Selection.None) }
    var inspected by remember { mutableStateOf<EntityId?>(null) }
    // Which of the player's own units exploration mode is currently walking around — irrelevant
    // (and unused) once state.combatStarted, where the normal turn-order Selection flow takes over.
    var exploringSelectedId by remember { mutableStateOf<EntityId?>(null) }
    var sheetExpanded by remember { mutableStateOf(true) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var threatOverlayOn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Reloads only when the map itself changes (a new encounter), not on every state update within
    // one — floorTexture/wallHatch/props are static for the battle, same as BattleMap's terrain.
    val mapAssets by produceState<MapAssets?>(initialValue = null, state.map) { value = loadMapAssets(state.map) }

    // doc15: "a toggle that hatches every tile an enemy could reach and attack next turn" —
    // recomputed only when the toggle flips or the game state actually changes, not on every
    // camera/zoom-driven recomposition.
    val threatTiles = remember(state, threatOverlayOn) {
        if (threatOverlayOn) allThreatenedTiles(state, Faction.Enemy, catalog) else emptySet()
    }

    // doc15 Camera: "never moves while the player is in ActionSelected or TargetPicked."
    val canPan = selection is Selection.None

    LaunchedEffect(Unit) { player.run() }

    /** doc15 Camera: "follows the active entity, but only when it leaves a comfortable inner rectangle." */
    suspend fun followIfNeeded(entityWorldPos: Offset) {
        if (viewportSize == Size.Zero) return
        val zoom = world.zoom.targetValue
        val camera = world.camera.targetValue
        val screenPos = worldToScreen(entityWorldPos, camera, zoom, viewportSize)
        val left = viewportSize.width * CAMERA_DEAD_ZONE_MARGIN
        val right = viewportSize.width * (1f - CAMERA_DEAD_ZONE_MARGIN)
        val top = viewportSize.height * CAMERA_DEAD_ZONE_MARGIN
        val bottom = viewportSize.height * (1f - CAMERA_DEAD_ZONE_MARGIN)
        val dx = when {
            screenPos.x < left -> screenPos.x - left
            screenPos.x > right -> screenPos.x - right
            else -> 0f
        }
        val dy = when {
            screenPos.y < top -> screenPos.y - top
            screenPos.y > bottom -> screenPos.y - bottom
            else -> 0f
        }
        if (dx != 0f || dy != 0f) world.camera.animateTo(camera + Offset(dx, dy) / zoom)
    }

    // snapshotFlow, not a plain LaunchedEffect(activeId) — the active entity's own VisualEntity.pos
    // keeps changing smoothly for the whole duration of a move animation, and the camera has to
    // track every frame of that, not just jump once when the active entity itself changes.
    LaunchedEffect(Unit) {
        snapshotFlow {
            val id = state.turn.order.getOrNull(state.turn.activeIndex)
            id?.let { world.entities[it]?.pos?.value }
        }.collect { pos ->
            if (pos != null && canPan) followIfNeeded(pos)
        }
    }

    /** doc15 Camera: "during AI turns, pans to keep both the actor and its target on screen; if they do not both fit, prioritise the target." */
    suspend fun frameActorAndTarget(actorPos: GridPos, targetPos: GridPos) {
        if (viewportSize == Size.Zero) return
        val zoom = world.zoom.targetValue
        val actorWorld = actorPos.toOffset(TILE_PX)
        val targetWorld = targetPos.toOffset(TILE_PX)
        val screenDelta = (targetWorld - actorWorld) * zoom
        val fits = abs(screenDelta.x) < viewportSize.width * AI_FRAME_FIT_FRACTION &&
            abs(screenDelta.y) < viewportSize.height * AI_FRAME_FIT_FRACTION
        val focus = if (fits) (actorWorld + targetWorld) / 2f else targetWorld
        world.camera.animateTo(focus)
    }

    /**
     * Exploration-mode movement (before [GameState.combatStarted]) — no AP, no turn order, no
     * resolver/perform() pipeline: [entityId] just walks the full path to [destination] one hop at
     * a time, checking after every single hop whether an enemy just became revealed. The walk stops
     * dead the instant that happens — the player reacts to a mid-walk ambush, not to something that
     * already fully happened by the time they see it. `world.entities[entityId]?.pos?.animateTo`
     * mirrors Director.kt's own `walk()` beat (private to that file, so reimplemented inline here
     * rather than reused) — each hop's animation finishing is what paces the loop, no extra delay().
     */
    suspend fun exploreMoveTo(entityId: EntityId, destination: GridPos) {
        val origin = state.byId[entityId]?.pos ?: return
        val path = findPath(origin, destination, state.map, state.occupancy) ?: return
        for (hop in path) {
            world.entities[entityId]?.pos?.animateTo(hop.toOffset(TILE_PX), tween(world.scaled(180)))
            val moved = checkCombatStart(updateRevealedTiles(moveEntityTo(state, entityId, hop)))
            state = moved
            if (canPan) followIfNeeded(hop.toOffset(TILE_PX))
            if (moved.combatStarted) {
                state = beginCombat(moved, catalog)
                log.add(0, LogEntry("An enemy spots you!", LogCategory.Info))
                exploringSelectedId = null
                return
            }
        }
    }

    suspend fun applyStep(result: StepResult): Boolean = when (result) {
        is StepResult.Completed -> {
            // Formatted against the PRE-update `state` — fine for entity-name resolution (archetype
            // never changes mid-encounter), and the only state that's actually in scope here; the
            // resolver's own final state isn't assigned to `state` until after this loop.
            result.resolver.emitted.forEach { event -> formatEvent(event, state, catalog)?.let { log.add(0, it) } }
            player.enqueue(result.resolver.emitted.flatMap { choreograph(it) })
            player.awaitDrained()
            // Recompute fog every completed step, not just on movement — any state change could
            // shift who's alive/where. No-op-safe: returns the same instance when nothing's newly
            // visible. checkCombatStart alongside it is a no-op once combatStarted is already true
            // (the normal case once real combat is underway) — cheap and correct either way.
            val updated = checkCombatStart(updateRevealedTiles(result.resolver.state))
            world.settle(updated)
            state = updated
            // docs/11-run-state.md's encounter handoff needs a real "combat is over" signal to call
            // finishEncounter from — nothing previously stopped the turn loop once one side was
            // wiped, so a boss kill (or a party wipe) just kept dealing out empty enemy turns forever.
            if (!ended) {
                state.combatOutcome()?.let {
                    ended = true
                    onEncounterEnd(state)
                }
            }
            true
        }
        is StepResult.Rejected -> {
            log.add(0, LogEntry("rejected: ${result.reasons}", LogCategory.Blocked))
            false
        }
        is StepResult.AwaitingInput -> {
            // A human-facing reaction prompt isn't built yet — no Reaction-cost action exists in
            // the demo catalog, so this never actually fires; logged rather than silently dropped
            // in case content changes that.
            log.add(0, LogEntry("awaiting a decision (not supported yet): ${result.request}", LogCategory.Info))
            false
        }
    }

    suspend fun endTurn(who: EntityId) {
        if (state.combatOutcome() != null) return
        applyStep(runResolver(Resolver(state, stack = listOf(Effect.EndTurn(who))), catalog))
    }

    /** Runs every consecutive AI-controlled turn to completion, handing control back once the active entity is human (or nothing is left to do). */
    suspend fun runAiTurns() {
        while (true) {
            if (state.combatOutcome() != null) return
            val activeId = state.turn.order.getOrNull(state.turn.activeIndex) ?: return
            val active = state.byId[activeId] ?: return
            if (active.actor?.controller is Controller.Human) return
            if ((active.health?.current ?: 1) > 0) {
                val decision = chooseAction(state, activeId, catalog)
                if (decision != null) {
                    val actorPos = active.pos
                    val targetPos = decision.ctx.targets.firstOrNull()?.let { state.byId[it]?.pos } ?: decision.ctx.point
                    if (actorPos != null && targetPos != null) frameActorAndTarget(actorPos, targetPos)
                    applyStep(perform(state, activeId, decision.actionId, decision.ctx, catalog))
                } else if (active.pos != null && active.pos !in state.revealedTiles && state.map.fogOfWar) {
                    // Mirrors ChooseAction.kt's own fog gate exactly, so this only fires when that's
                    // actually why nothing happened, not for a generic "no legal move" pass.
                    log.add(0, LogEntry("${catalog.archetype(active.archetype).name} remains hidden.", LogCategory.Info))
                }
            }
            endTurn(activeId)
        }
    }

    val activeId = state.turn.order.getOrNull(state.turn.activeIndex)
    val active = activeId?.let { state.byId[it] }
    val isHumanTurn = active?.actor?.controller is Controller.Human
    // `Path` targeting is the engine's own marker for "this is a movement action" — the resolver's
    // MoveAlong handling is triggered by the targeting mode itself, not an authored effect (same
    // reasoning :designer's DefaultContent.kt uses for its "move" action). Tapping the active
    // entity's own tile is a shortcut for whichever of its actions is Path-targeted, instead of a
    // separate action-bar button — see onTileTap/the action bar below.
    val moveActionId = active?.allActions(catalog)?.firstOrNull { catalog.actionDef(it).targeting.mode == TargetMode.Path }

    // Every prior demo/test fixture happened to start on a human's turn, so runAiTurns() only ever
    // needed a reactive trigger from the human's own "End Turn" button. A real startEncounter's
    // initiative roll has no such bias — when it rolls an AI-controlled entity first, nothing had
    // ever kicked off its turn, and the board just sat on "Enemy turn..." forever. Reacting to
    // activeId directly (which also fires on first composition) covers that turn-1 case for free,
    // so the explicit call after the button's own endTurn() is now redundant and removed below.
    LaunchedEffect(state.turn.round, activeId, state.combatStarted) {
        // Before combat starts, turn order/AP don't apply at all (exploration mode instead) —
        // enemies stay put rather than getting a cold-start runAiTurns() pass through initiative
        // order the moment the encounter loads.
        if (!state.combatStarted) return@LaunchedEffect
        if (activeId == null) return@LaunchedEffect
        if (isHumanTurn) {
            // AI turns already get a hard camera move every action via frameActorAndTarget() below;
            // a human turn had nothing equivalent — only the passive dead-zone nudge in the
            // snapshotFlow above, which does nothing if the human's (stationary, since they haven't
            // acted yet) token already happens to sit inside the dead zone, e.g. after the camera was
            // last left framing a distant enemy fight. Center on the new active human explicitly,
            // same animateTo the "centre on active" button already uses, so a player's turn is
            // always brought on screen the same way an AI's already is.
            if (canPan) active.pos?.let { pos -> world.camera.animateTo(pos.toOffset(TILE_PX)) }
        } else {
            runAiTurns()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(PAPER)) {
        // doc15: the board is a flex viewport (pan+zoom, culled), not sized to the map. BoxWithConstraints
        // gives Board's Canvas an explicit dp size matching the available space, rather than
        // `Modifier.weight(1f)` directly on the Canvas: a Row-weighted Canvas used to draw fine but its
        // pointer-input hit-test bounds silently didn't match its rendered bounds, so every tap was
        // dropped (a Compose Desktop/Skiko quirk in this dev environment, found by empirical isolation —
        // a plain fixed-size Canvas elsewhere in the same window received clicks correctly, the same
        // Canvas under `weight(1f)` never did). An ancestor claiming leftover space via weight(), like
        // this Box, is fine — only the Canvas itself may never carry weight() directly.
        //
        // The turn strip is drawn INSIDE this same Box, on top of the Board, rather than as its own
        // row above it (per the user's explicit ask) — it no longer shrinks the board's own viewport
        // height, it just floats over the top of it.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Board(
                map = state.map,
                mapAssets = mapAssets,
                world = world,
                colors = colors,
                legalTiles = (selection as? Selection.ActionPicked)?.legal ?: emptySet(),
                threatTiles = threatTiles,
                selectedTile = (selection as? Selection.TargetPicked)?.ctx?.point,
                canPan = canPan,
                revealedTiles = state.revealedTiles,
                entityPositions = state.entities.mapNotNull { e -> e.pos?.let { e.id to it } }.toMap(),
                modifier = Modifier.size(maxWidth, maxHeight),
                onViewportSizeChanged = { viewportSize = it },
                // doc15's targeting state machine: ActionPicked -> tap a legal tile -> TargetPicked;
                // TargetPicked -> tap the same (highlighted) tile again -> confirms, perform()s it;
                // TargetPicked -> tap anywhere else -> cancels back to Idle (not a re-inspect — the
                // player already has a pending action, tapping the board again means "never mind");
                // Idle -> tap own char/enemy/cell -> Inspect (whatever's on that tile, or nothing).
                onTileTap = tap@{ pos ->
                    if (!state.combatStarted) {
                        // Free-roam: tap any of your own units to select them, then tap a
                        // destination to walk there — no turn order, no AP, see exploreMoveTo.
                        val occupant = state.occupancy[pos]?.let { state.byId[it] }
                        if (occupant != null && occupant.actor?.faction == Faction.Player) {
                            exploringSelectedId = occupant.id
                            inspected = null
                        } else {
                            val mover = exploringSelectedId
                            if (mover != null) {
                                scope.launch { exploreMoveTo(mover, pos) }
                            } else if (!state.map.fogOfWar || pos in state.revealedTiles) {
                                inspected = state.occupancy[pos]
                            }
                        }
                        return@tap
                    }
                    when (val sel = selection) {
                        is Selection.ActionPicked -> {
                            if (pos !in sel.legal) return@tap
                            val def = catalog.actionDef(sel.actionId)
                            val targets = affectedBy(state, def, activeId!!, pos)
                            val ctx = ActionCtx(activeId, targets, point = pos)
                            selection = Selection.TargetPicked(sel.actionId, ctx, preview(state, activeId, sel.actionId, ctx, catalog))
                        }
                        is Selection.TargetPicked -> {
                            if (pos == sel.ctx.point) {
                                scope.launch {
                                    applyStep(perform(state, activeId!!, sel.actionId, sel.ctx, catalog))
                                    selection = Selection.None
                                }
                            } else {
                                selection = Selection.None
                            }
                        }
                        Selection.None -> {
                            // Tapping the active human's own tile is a shortcut for its Move action —
                            // same as pressing a "Move" button, without needing one in the action bar.
                            if (isHumanTurn && moveActionId != null && pos == active.pos) {
                                val legal = legalTargets(state, activeId, catalog.actionDef(moveActionId), catalog)
                                selection = Selection.ActionPicked(moveActionId, legal)
                            } else if (!state.map.fogOfWar || pos in state.revealedTiles) {
                                inspected = state.occupancy[pos]
                            } else {
                                inspected = null
                            }
                        }
                    }
                },
            )
            TurnOrderStrip(
                state,
                colors,
                onSelectEntity = { id ->
                    val pos = state.byId[id]?.pos ?: return@TurnOrderStrip
                    scope.launch { world.camera.animateTo(pos.toOffset(TILE_PX)) }
                },
                onOpenLog = { logOpen = true },
                threatOverlayOn = threatOverlayOn,
                onToggleThreat = { threatOverlayOn = !threatOverlayOn },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        PartyBar(state, catalog)

        // Bottom sheet — Peek/Inspect states (doc15). Flush against the party bar directly above
        // it (same PAPER_SHEET tone, no gap between them), so rounded top corners here just
        // exposed the outer PAPER background peeking through the corner cutouts — a real visual
        // glitch found by the user, not a stylistic choice. A hairline ink border reads as a
        // bordered card instead (same technique InkButton already uses), no rounding needed.
        // doc15: "dismissible only to half-height, never fully" — collapsed still shows the header
        // line, just hides the action bar/log so more board is visible. A tap-to-toggle handle for
        // now, not a drag gesture (velocity tracking/snap points are real work, deferred).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = INK_FAINT)
                .background(PAPER_SHEET)
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { sheetExpanded = !sheetExpanded },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(if (sheetExpanded) "▾" else "▴", style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
            }
            Spacer(modifier = Modifier.size(4.dp))

            // Before combat starts, turn.activeIndex is just wherever initiative happened to land
            // at spawn — meaningless while exploration mode is driving the board instead. Peek
            // follows whichever unit the player has actually selected to move.
            val peekEntity = if (state.combatStarted) active else exploringSelectedId?.let { state.byId[it] }
            if (peekEntity != null) {
                val s = peekEntity.stats(catalog)
                BasicText(
                    "${catalog.archetype(peekEntity.archetype).name} — HP ${peekEntity.health?.current}/${s.maxHp}" +
                        (peekEntity.resources?.takeIf { state.combatStarted }?.let { " · AP ${it.ap}/${s.maxAp} · Mana ${it.mana}/${s.maxMana}" } ?: ""),
                    style = TextStyle(color = INK, fontSize = 16.sp),
                )
            }

            if (!sheetExpanded) return@Column
            Spacer(modifier = Modifier.size(12.dp))

            val inspectedId = inspected
            if (inspectedId != null) {
                // Inspect deliberately looks different from Peek (doc15: "they must not look
                // alike") — no action bar, just read-only details plus Back.
                InspectPanel(inspectedId, state, catalog, onBack = { inspected = null })
            } else if (!state.combatStarted) {
                // No AP/turn order to show an action bar for — just enough feedback for the tap
                // flow in onTileTap above (select a unit, then a destination).
                BasicText(
                    if (exploringSelectedId == null) "Exploring — tap a party member to move them." else "Exploring — tap a tile to walk there.",
                    style = TextStyle(color = INK_FAINT, fontSize = 14.sp),
                )
            } else if (!isHumanTurn) {
                BasicText("Enemy turn…", style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
            } else {
                when (val sel = selection) {
                    is Selection.None -> {
                        Row {
                            // Move no longer gets its own button here — tapping the active entity's
                            // own tile on the board does the same thing (see onTileTap above).
                            active.allActions(catalog).filter { it != moveActionId }.forEach { actionId ->
                                InkButton(
                                    catalog.actionDef(actionId).name,
                                    modifier = Modifier.padding(end = 8.dp),
                                    onClick = {
                                        inspected = null
                                        val def = catalog.actionDef(actionId)
                                        selection = when {
                                            def.targeting.mode == TargetMode.SelfOnly -> {
                                                val ctx = ActionCtx(activeId, listOf(activeId), point = active.pos)
                                                Selection.TargetPicked(actionId, ctx, preview(state, activeId, actionId, ctx, catalog))
                                            }
                                            // Exactly one legal target: skip straight to TargetPicked instead of
                                            // making the player tap the only option on the board. Still requires
                                            // an explicit Confirm — this only removes a redundant tap, not the
                                            // safety net doc15's "nothing mutates before Confirm" is built on.
                                            else -> {
                                                val legal = legalTargets(state, activeId, def, catalog)
                                                val onlyTarget = legal.singleOrNull()
                                                if (onlyTarget != null) {
                                                    val targets = affectedBy(state, def, activeId, onlyTarget)
                                                    val ctx = ActionCtx(activeId, targets, point = onlyTarget)
                                                    Selection.TargetPicked(actionId, ctx, preview(state, activeId, actionId, ctx, catalog))
                                                } else {
                                                    Selection.ActionPicked(actionId, legal)
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            InkButton(
                                "End Turn",
                                onClick = {
                                    // runAiTurns() no longer needs an explicit call here — the
                                    // LaunchedEffect(activeId) above reacts the moment endTurn()
                                    // advances the active entity, whether it's this button or the
                                    // encounter's very first turn that turns out to need it.
                                    scope.launch { endTurn(activeId) }
                                },
                            )
                        }
                    }
                    is Selection.ActionPicked -> {
                        BasicText("${catalog.actionDef(sel.actionId).name}: pick a highlighted tile", style = TextStyle(color = INK, fontSize = 14.sp))
                        Spacer(modifier = Modifier.size(8.dp))
                        InkButton("Cancel", onClick = { selection = Selection.None })
                    }
                    is Selection.TargetPicked -> {
                        // No Confirm/Cancel buttons for any action — tapping the highlighted (green)
                        // tile again on the board confirms it, tapping anywhere else resets it (same
                        // gesture for every action, not just Move — see onTileTap above).
                        BasicText("${catalog.actionDef(sel.actionId).name} expects ${sel.preview.events.size} events", style = TextStyle(color = INK, fontSize = 14.sp))
                        Spacer(modifier = Modifier.size(8.dp))
                        BasicText("Tap the highlighted tile again to confirm — tap elsewhere to cancel.", style = TextStyle(color = INK_FAINT, fontSize = 12.sp))
                    }
                }
            }
        }
    }

        // doc15: "reachable from the turn strip" — a dedicated full-screen panel (☰ button above),
        // not squeezed inline into the Peek sheet alongside the action bar anymore.
        if (logOpen) {
            CombatLogPanel(log, onClose = { logOpen = false })
        }
    }
}
