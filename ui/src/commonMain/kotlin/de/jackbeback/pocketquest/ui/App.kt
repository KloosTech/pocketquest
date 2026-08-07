package de.jackbeback.pocketquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.action.perform
import de.jackbeback.pocketquest.core.rules.action.preview
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import de.jackbeback.pocketquest.core.rules.resolver.run as runResolver
import de.jackbeback.pocketquest.core.rules.targeting.affectedBy
import de.jackbeback.pocketquest.core.rules.targeting.legalTargets
import kotlinx.coroutines.launch

private const val TILE_PX = 48f

/**
 * Layout size per tile, in dp — deliberately a separate unit from [TILE_PX] (the raw pixel unit
 * [DrawScope]/pointer-offset math uses inside the Canvas). Board's Canvas must be given an
 * explicit size rather than `Modifier.weight(1f)`: a Row-weighted Canvas drew fine but its
 * pointer-input hit-test bounds silently didn't match its rendered bounds, so every tap on it was
 * dropped — found by empirical isolation (a plain fixed-size Canvas elsewhere in the same window
 * received clicks correctly; the same Canvas under `weight(1f)` never did). Root cause not fully
 * understood (a Compose Desktop/Skiko quirk in this environment, not this codebase's logic), but
 * the fix is real: give the board's Canvas a concrete size.
 */
private val TILE_DP = 40.dp

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
 * doc07: "the grid is one Canvas, not 400 composables." Grid lines and
 * blocked tiles come from [BattleMap] (static for the battle); token
 * positions/HP/scale/alpha come from [VisualWorld] (animated). [legalTiles]
 * highlights doc15's "Reachable"/targeting mode; taps only matter while
 * something is selected — [onTileTap] is a no-op otherwise.
 */
@Composable
private fun Board(
    map: BattleMap,
    world: VisualWorld,
    colors: Map<EntityId, Color>,
    legalTiles: Set<GridPos>,
    onTileTap: (GridPos) -> Unit,
    modifier: Modifier = Modifier,
) {
    // detectTapGestures's double-tap disambiguation wait never resolved a tap to onTap in this
    // environment (confirmed empirically — zero taps registered across many real clicks, while a
    // plain Modifier.clickable fired reliably every time). clickable's own tap recognition works,
    // so it drives the actual click; a separate lightweight down-position tracker (no gesture
    // disambiguation, just "where was the last press") supplies the tile coordinate.
    var lastPressPos by remember { mutableStateOf(Offset.Zero) }
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastPressPos = down.position
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTileTap(lastPressPos.toGridPos(TILE_PX)) },
    ) {
        drawGrid(map)
        legalTiles.forEach { pos -> drawHighlight(pos) }
        world.entities.forEach { (id, entity) ->
            drawEntity(entity, colors[id] ?: Color.Gray)
        }
        world.overlays.forEach { overlay ->
            drawOverlay(overlay)
        }
    }
}

private fun DrawScope.drawGrid(map: BattleMap) {
    val width = map.width * TILE_PX
    val height = map.height * TILE_PX
    for (col in 0..map.width) {
        val x = col * TILE_PX
        drawLine(Color.DarkGray, Offset(x, 0f), Offset(x, height))
    }
    for (row in 0..map.height) {
        val y = row * TILE_PX
        drawLine(Color.DarkGray, Offset(0f, y), Offset(width, y))
    }
    map.walls.forEach { pos ->
        drawRect(
            color = Color.Black,
            topLeft = Offset(pos.col * TILE_PX, pos.row * TILE_PX),
            size = Size(TILE_PX, TILE_PX),
        )
    }
}

private fun DrawScope.drawHighlight(pos: GridPos) {
    drawRect(
        color = Color(0x552E7D32),
        topLeft = Offset(pos.col * TILE_PX, pos.row * TILE_PX),
        size = Size(TILE_PX, TILE_PX),
    )
}

private fun DrawScope.drawEntity(entity: VisualEntity, color: Color) {
    drawCircle(color = color, radius = TILE_PX * 0.35f * entity.scale.value, center = entity.pos.value, alpha = entity.alpha.value)
}

private fun DrawScope.drawOverlay(overlay: Overlay) {
    // No text-in-Canvas dependency pulled in for one debug number — a small colored square
    // stands in for the real floating-number readout a font/text-measurer would draw.
    val color = if (overlay.amount < 0) Color(0xFFB71C1C) else Color(0xFF2E7D32)
    drawRect(color = color, topLeft = overlay.pos + Offset(TILE_PX * 0.3f, -TILE_PX * 0.6f), size = Size(TILE_PX * 0.25f, TILE_PX * 0.25f))
}

/**
 * Owns the whole live game: state, the persistent [AnimationPlayer] (never `close()`d — this is
 * the "keeps taking new enqueue() calls across a whole session" case the player's own doc comment
 * anticipated), and the player-input loop. A human's turn drives through [Selection]; an AI turn
 * runs to completion automatically via [runAiTurns] with the same perform()/EndTurn calls a human
 * action uses, so there is exactly one code path for "an entity acted," not two.
 */
@Composable
fun App(initialState: GameState, catalog: Catalog) {
    var state by remember { mutableStateOf(initialState) }
    val world = remember { VisualWorld(initialState, TILE_PX) }
    val player = remember { AnimationPlayer(world) }
    val colors = remember(initialState) { initialState.entities.associate { it.id to colorFor(it.actor?.faction) } }
    val log = remember { mutableStateListOf<String>() }
    var selection by remember { mutableStateOf<Selection>(Selection.None) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { player.run() }

    suspend fun applyStep(result: StepResult): Boolean = when (result) {
        is StepResult.Completed -> {
            result.resolver.emitted.forEach { log.add(0, it.toString()) }
            player.enqueue(result.resolver.emitted.flatMap { choreograph(it) })
            player.awaitDrained()
            world.settle(result.resolver.state)
            state = result.resolver.state
            true
        }
        is StepResult.Rejected -> {
            log.add(0, "rejected: ${result.reasons}")
            false
        }
        is StepResult.AwaitingInput -> {
            // A human-facing reaction prompt isn't built yet — no Reaction-cost action exists in
            // the demo catalog, so this never actually fires; logged rather than silently dropped
            // in case content changes that.
            log.add(0, "awaiting a decision (not supported yet): ${result.request}")
            false
        }
    }

    suspend fun endTurn(who: EntityId) {
        applyStep(runResolver(Resolver(state, stack = listOf(Effect.EndTurn(who))), catalog))
    }

    /** Runs every consecutive AI-controlled turn to completion, handing control back once the active entity is human (or nothing is left to do). */
    suspend fun runAiTurns() {
        while (true) {
            val activeId = state.turn.order.getOrNull(state.turn.activeIndex) ?: return
            val active = state.byId[activeId] ?: return
            if (active.actor?.controller is Controller.Human) return
            if ((active.health?.current ?: 1) > 0) {
                val decision = chooseAction(state, activeId, catalog)
                if (decision != null) applyStep(perform(state, activeId, decision.actionId, decision.ctx, catalog))
            }
            endTurn(activeId)
        }
    }

    val activeId = state.turn.order.getOrNull(state.turn.activeIndex)
    val active = activeId?.let { state.byId[it] }
    val isHumanTurn = active?.actor?.controller is Controller.Human

    Row(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Board(
            map = state.map,
            world = world,
            colors = colors,
            legalTiles = (selection as? Selection.ActionPicked)?.legal ?: emptySet(),
            modifier = Modifier.size(TILE_DP * state.map.width, TILE_DP * state.map.height).padding(16.dp),
            onTileTap = tap@{ pos ->
                val picked = selection as? Selection.ActionPicked ?: return@tap
                if (pos !in picked.legal) return@tap
                val def = catalog.actionDef(picked.actionId)
                val targets = affectedBy(state, def, activeId!!, pos)
                val ctx = ActionCtx(activeId, targets, point = pos)
                selection = Selection.TargetPicked(picked.actionId, ctx, preview(state, activeId, picked.actionId, ctx, catalog))
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            if (!isHumanTurn) {
                BasicText("Enemy turn…", modifier = Modifier.padding(16.dp))
            } else {
                when (val sel = selection) {
                    is Selection.None -> {
                        val archetype = catalog.archetype(active.archetype)
                        archetype.actions.forEach { actionId ->
                            BasicText(
                                actionId.raw,
                                modifier = Modifier.padding(8.dp).clickable {
                                    val def = catalog.actionDef(actionId)
                                    selection = if (def.targeting.mode == TargetMode.SelfOnly) {
                                        val ctx = ActionCtx(activeId, listOf(activeId), point = active.pos)
                                        Selection.TargetPicked(actionId, ctx, preview(state, activeId, actionId, ctx, catalog))
                                    } else {
                                        Selection.ActionPicked(actionId, legalTargets(state, activeId, def, catalog))
                                    }
                                },
                            )
                        }
                        BasicText(
                            "End Turn",
                            modifier = Modifier.padding(8.dp).clickable {
                                scope.launch {
                                    endTurn(activeId)
                                    runAiTurns()
                                }
                            },
                        )
                    }
                    is Selection.ActionPicked -> {
                        BasicText("${sel.actionId.raw}: pick a highlighted tile", modifier = Modifier.padding(8.dp))
                        BasicText("Cancel", modifier = Modifier.padding(8.dp).clickable { selection = Selection.None })
                    }
                    is Selection.TargetPicked -> {
                        BasicText("${sel.actionId.raw} expects ${sel.preview.events.size} events", modifier = Modifier.padding(8.dp))
                        BasicText(
                            "Confirm",
                            modifier = Modifier.padding(8.dp).clickable {
                                scope.launch {
                                    applyStep(perform(state, activeId, sel.actionId, sel.ctx, catalog))
                                    selection = Selection.None
                                }
                            },
                        )
                        BasicText("Cancel", modifier = Modifier.padding(8.dp).clickable { selection = Selection.None })
                    }
                }
            }
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(log) { line -> BasicText(line) }
            }
        }
    }
}
