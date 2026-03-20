package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.designer.model.EncounterBundle
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS

@Composable
fun BattleGridPreview(
    encounter: EncounterBundle,
    selectedInstanceId: String?,
    selectedMap: TileMap?,
    tiles: Map<Pair<Int, Int>, ImageBitmap>,
    onSelectInstance: (String?) -> Unit,
    onMoveEnemy: (instanceId: String, col: Int, row: Int) -> Unit,
    onMovePlayer: (col: Int, row: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = selectedMap?.cols ?: BATTLE_COLS
    val rows = selectedMap?.rows ?: BATTLE_ROWS

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0D1117))
            .pointerInput(encounter, selectedMap) {
                detectTapGestures { offset ->
                    val cellSize = minOf(size.width.toFloat() / cols, size.height.toFloat() / rows)
                    val originX  = (size.width  - cols * cellSize) / 2f
                    val originY  = (size.height - rows * cellSize) / 2f
                    val tapCol = ((offset.x - originX) / cellSize).toInt().coerceIn(0, cols - 1)
                    val tapRow = ((offset.y - originY) / cellSize).toInt().coerceIn(0, rows - 1)

                    val tappedEnemy = encounter.enemies.firstOrNull { e -> e.spawnCol == tapCol && e.spawnRow == tapRow }
                    val isPlayerTile = tapCol == encounter.playerSpawnCol && tapRow == encounter.playerSpawnRow
                    when {
                        tappedEnemy != null ->
                            onSelectInstance(if (selectedInstanceId == tappedEnemy.id) null else tappedEnemy.id)
                        selectedInstanceId != null ->
                            onMoveEnemy(selectedInstanceId, tapCol, tapRow)
                        !isPlayerTile ->
                            onMovePlayer(tapCol, tapRow)
                        else -> {}
                    }
                }
            }
    ) {
        val cellSize = minOf(size.width / cols, size.height / rows)
        val originX  = (size.width  - cols * cellSize) / 2f
        val originY  = (size.height - rows * cellSize) / 2f

        for (col in 0 until cols) {
            for (row in 0 until rows) {
                val x = originX + col * cellSize
                val y = originY + row * cellSize
                val bmp = tiles[Pair(col, row)]
                if (bmp != null) {
                    drawImage(
                        image         = bmp,
                        dstOffset     = IntOffset(x.toInt(), y.toInt()),
                        dstSize       = IntSize(cellSize.toInt() + 1, cellSize.toInt() + 1),
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    val shade = if ((col + row) % 2 == 0) Color(0xFF161B22) else Color(0xFF0D1117)
                    drawRect(shade, topLeft = Offset(x, y), size = Size(cellSize, cellSize))
                }
                drawRect(Color(0xFF21262D), topLeft = Offset(x, y), size = Size(cellSize, cellSize), style = Stroke(0.5f))
            }
        }

        drawSpawnCell(encounter.playerSpawnCol, encounter.playerSpawnRow, Color(0xFF89B4FA), cellSize, originX, originY)

        encounter.enemies.forEach { enemy ->
            val isSelected = enemy.id == selectedInstanceId
            drawEnemyCell(
                col      = enemy.spawnCol,
                row      = enemy.spawnRow,
                color    = if (isSelected) Color(0xFFF38BA8) else Color(0xFFF38BA8).copy(alpha = 0.6f),
                outline  = if (isSelected) Color(0xFFF38BA8) else Color(0xFFF38BA8).copy(alpha = 0.3f),
                cellSize = cellSize,
                originX  = originX,
                originY  = originY,
            )
        }
    }
}

private fun DrawScope.drawSpawnCell(
    col: Int, row: Int,
    color: Color,
    cellSize: Float, originX: Float, originY: Float,
) {
    val x   = originX + col * cellSize
    val y   = originY + row * cellSize
    val pad = (cellSize * 0.1f).coerceAtLeast(2f)
    drawRect(color.copy(alpha = 0.2f), Offset(x + pad, y + pad), Size(cellSize - pad * 2, cellSize - pad * 2))
    drawRect(color.copy(alpha = 0.6f), Offset(x + pad, y + pad), Size(cellSize - pad * 2, cellSize - pad * 2), style = Stroke(1.5f))
}

private fun DrawScope.drawEnemyCell(
    col: Int, row: Int,
    color: Color, outline: Color,
    cellSize: Float, originX: Float, originY: Float,
) {
    val x   = originX + col * cellSize
    val y   = originY + row * cellSize
    val pad = (cellSize * 0.1f).coerceAtLeast(2f)
    drawRect(color.copy(alpha = 0.3f), Offset(x + pad, y + pad), Size(cellSize - pad * 2, cellSize - pad * 2))
    drawRect(outline, Offset(x + pad, y + pad), Size(cellSize - pad * 2, cellSize - pad * 2), style = Stroke(1.5f))
}
