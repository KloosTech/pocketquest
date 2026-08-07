package de.jackbeback.pocketquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

private const val TILE_PX = 48f

private fun colorFor(faction: Faction?): Color = when (faction) {
    Faction.Player -> Color(0xFF2196F3)
    Faction.Enemy -> Color(0xFFE53935)
    Faction.Neutral -> Color(0xFF9E9E9E)
    null -> Color(0xFF757575)
}

/**
 * doc07: "the grid is one Canvas, not 400 composables." Grid lines and
 * blocked tiles come from [BattleMap] (static for the battle); token
 * positions/HP/scale/alpha come from [VisualWorld] (animated).
 * `colors` is faction-per-entity, computed once outside VisualWorld —
 * doc07 keeps VisualWorld itself free of rules data (no faction lookup
 * lives there), but *something* has to decide token color, so it is
 * passed in as presentation config rather than smuggled into VisualWorld.
 */
@Composable
fun Board(map: BattleMap, world: VisualWorld, colors: Map<EntityId, Color>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawGrid(map)
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
 * :app runs the real engine/persistence pipeline up front (sequential-
 * playback design: all steps computed first, then replayed as one
 * animated sequence) and hands the initial/final GameState plus the full
 * event list here. This composable owns the actual AnimationPlayer run
 * loop and the doc07 ordering: enqueue -> awaitDrained -> settle.
 */
@Composable
fun App(initialState: GameState, finalState: GameState, events: List<GameEvent>, log: List<String>) {
    val world = remember { VisualWorld(initialState, TILE_PX) }
    val player = remember { AnimationPlayer(world) }
    val colors = remember(initialState) { initialState.entities.associate { it.id to colorFor(it.actor?.faction) } }
    val scope = rememberCoroutineScope()
    var playerJob by remember { mutableStateOf<Job?>(null) }

    fun skip() {
        playerJob?.cancel()
        world.speed = 0f
        scope.launch { world.settle(finalState) }
    }

    LaunchedEffect(events) {
        playerJob = coroutineContext[Job]
        // Bounded playback, not a persistent player: enqueue the whole batch, close the
        // queue, then await run() returning directly. run()'s own coroutineScope only
        // returns once every beat it dispatched — including Parallel children like a
        // floating damage number's delay — has actually finished, so nothing here can
        // race ahead of unfinished animation work. (An earlier version used a detached
        // `launch { player.run() }` + awaitDrained() pair per doc07's literal sketch; that
        // left Parallel beats' coroutines dangling as un-awaited siblings of an
        // infinite consumer loop, and delay()-based cleanup — like removing a floating
        // number's overlay — silently never ran. Found by watching leftover overlay
        // squares never disappear from an actual run, not by inspection.)
        player.enqueue(events.flatMap { choreograph(it) })
        player.close()
        player.run()
        world.settle(finalState)
    }

    Row(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Board(map = initialState.map, world = world, colors = colors, modifier = Modifier.weight(1f).padding(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                "Skip",
                modifier = Modifier.padding(16.dp).clickable { skip() },
            )
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(log) { line -> BasicText(line) }
            }
        }
    }
}
