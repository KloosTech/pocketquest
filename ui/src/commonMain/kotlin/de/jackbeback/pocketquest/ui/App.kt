package de.jackbeback.pocketquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState

private const val TILE_PX = 48f

/**
 * doc07: "the grid is one Canvas, not 400 composables." No animation yet —
 * VisualWorld/Director/Beat/AnimationPlayer are their own, later pass; this
 * redraws straight from GameState whenever it changes, same end result as
 * doc07's settle() when there is nothing to animate. Faction-colored
 * circles stand in for real sprites — there is no art yet.
 */
@Composable
fun Board(state: GameState, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawGrid(state)
        state.entities.forEach { entity -> drawEntity(entity) }
    }
}

private fun DrawScope.drawGrid(state: GameState) {
    val map = state.map
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
    map.blockedTiles.forEach { pos ->
        drawRect(
            color = Color.Black,
            topLeft = Offset(pos.col * TILE_PX, pos.row * TILE_PX),
            size = Size(TILE_PX, TILE_PX),
        )
    }
}

private fun DrawScope.drawEntity(entity: Entity) {
    val pos = entity.pos ?: return
    val center = Offset((pos.col + 0.5f) * TILE_PX, (pos.row + 0.5f) * TILE_PX)
    val color = when (entity.actor?.faction) {
        Faction.Player -> Color(0xFF2196F3)
        Faction.Enemy -> Color(0xFFE53935)
        Faction.Neutral -> Color(0xFF9E9E9E)
        null -> Color(0xFF757575)
    }
    drawCircle(color = color, radius = TILE_PX * 0.35f, center = center)
}

/**
 * The whole of :ui for now: one screen, no navigation, no ViewModel — a
 * smoke test proving the module boundary (only :ui imports Compose) and
 * the render path work. :app runs the real engine/persistence pipeline and
 * hands the resulting GameState and a plain-text event log here.
 */
@Composable
fun App(state: GameState, log: List<String>) {
    Row(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Board(state = state, modifier = Modifier.weight(1f).padding(16.dp))
        LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
            items(log) { line -> BasicText(line) }
        }
    }
}
