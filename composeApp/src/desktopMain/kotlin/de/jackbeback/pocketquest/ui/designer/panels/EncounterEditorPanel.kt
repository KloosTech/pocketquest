package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.designer.model.EncounterBundle
import de.jackbeback.pocketquest.designer.model.EnemyDefinition
import de.jackbeback.pocketquest.ui.designer.DC

private const val GRID_COLS = 14
private const val GRID_ROWS = 9

@Composable
fun EncounterEditorPanel(
    encounter: EncounterBundle,
    enemyLibrary: List<EnemyDefinition>,
    onUpdate: (EncounterBundle) -> Unit,
    onDelete: () -> Unit,
    onAddEnemy: (EnemyDefinition) -> Unit,
    onRemoveEnemy: (String) -> Unit,
    onPlayPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedInstanceId by remember(encounter.id) { mutableStateOf<String?>(null) }
    val selectedInstance = encounter.enemies.find { it.id == selectedInstanceId }

    LazyColumn(
        modifier = modifier
            .background(DC.Background)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { EditorSectionTitle("ENCOUNTER EDITOR", DC.Blue) }

        // ── Identity ──────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Encounter")
                FieldRow("Name") {
                    DField(encounter.name) { onUpdate(encounter.copy(name = it)) }
                }
                FieldRow("ID") {
                    DField(encounter.id, enabled = false) {}
                }
                FieldRow("Description") {
                    DField(encounter.description, singleLine = false) { onUpdate(encounter.copy(description = it)) }
                }
            }
        }

        // ── Player Spawn ──────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Player Spawn")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("Column (0–13)") {
                            IntField(encounter.playerSpawnCol) { onUpdate(encounter.copy(playerSpawnCol = it.coerceIn(0, 13))) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Row (0–8)") {
                            IntField(encounter.playerSpawnRow) { onUpdate(encounter.copy(playerSpawnRow = it.coerceIn(0, 8))) }
                        }
                    }
                }
            }
        }

        // ── Battle Grid ───────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Battle Grid Preview  (click enemy to select, drag to move)")
                BattleGridPreview(
                    encounter = encounter,
                    selectedInstanceId = selectedInstanceId,
                    onSelectInstance = { selectedInstanceId = it },
                    onMoveEnemy = { instanceId, col, row ->
                        val updated = encounter.enemies.map { e ->
                            if (e.id == instanceId) e.copy(spawnCol = col, spawnRow = row) else e
                        }
                        onUpdate(encounter.copy(enemies = updated))
                    },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
        }

        // ── Enemies in encounter ──────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("ENEMIES IN ENCOUNTER")
                if (encounter.enemies.isEmpty()) {
                    Text("No enemies yet — add from the library below", color = DC.Overlay0, fontSize = 12.sp)
                }
            }
        }
        items(encounter.enemies, key = { it.id }) { enemy ->
            EnemyInstanceRow(
                enemy = enemy,
                isSelected = selectedInstanceId == enemy.id,
                onSelect = { selectedInstanceId = if (selectedInstanceId == enemy.id) null else enemy.id },
                onUpdate = { updated ->
                    val newEnemies = encounter.enemies.map { if (it.id == enemy.id) updated else it }
                    onUpdate(encounter.copy(enemies = newEnemies))
                },
                onRemove = {
                    if (selectedInstanceId == enemy.id) selectedInstanceId = null
                    onRemoveEnemy(enemy.id)
                },
            )
        }

        // ── Add from library ──────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("ADD ENEMY FROM LIBRARY")
                if (enemyLibrary.isEmpty()) {
                    Text("Library is empty — create enemies in the Enemy editor", color = DC.Overlay0, fontSize = 12.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        enemyLibrary.forEach { libEnemy ->
                            ActionChip("+ ${libEnemy.name}") { onAddEnemy(libEnemy) }
                        }
                    }
                }
            }
        }

        // ── Actions ───────────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SuccessChip("▶  Play Encounter", onPlayPreview)
                Spacer(Modifier.weight(1f))
                DangerButton("Delete Encounter", onDelete)
            }
        }
    }
}

@Composable
private fun EnemyInstanceRow(
    enemy: EnemyDefinition,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (EnemyDefinition) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) DC.Selected else DC.Panel)
            .border(
                1.dp,
                if (isSelected) DC.Red.copy(alpha = 0.4f) else DC.PanelBorder,
                RoundedCornerShape(6.dp),
            )
            .clickable { onSelect() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(DC.Red)
            )
            Spacer(Modifier.width(8.dp))
            Text(enemy.name, color = DC.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("HP ${enemy.maxHealth}  AC ${enemy.stats.ac}  Init ${enemy.initiative}", color = DC.Overlay0, fontSize = 11.sp)
            Spacer(Modifier.width(12.dp))
            Text("✕", color = DC.Overlay0, fontSize = 13.sp, modifier = Modifier.clickable { onRemove() })
        }
        if (isSelected) {
            HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
            // Inline spawn position editor when selected
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Spawn:", color = DC.Subtext0, fontSize = 11.sp)
                Text("Col", color = DC.Overlay0, fontSize = 11.sp)
                Box(Modifier.width(50.dp)) {
                    IntField(enemy.spawnCol) { onUpdate(enemy.copy(spawnCol = it.coerceIn(0, 13))) }
                }
                Text("Row", color = DC.Overlay0, fontSize = 11.sp)
                Box(Modifier.width(50.dp)) {
                    IntField(enemy.spawnRow) { onUpdate(enemy.copy(spawnRow = it.coerceIn(0, 8))) }
                }
                Spacer(Modifier.weight(1f))
                Text("Skills: ${enemy.skillIds.joinToString(", ")}", color = DC.Sapphire, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun BattleGridPreview(
    encounter: EncounterBundle,
    selectedInstanceId: String?,
    onSelectInstance: (String?) -> Unit,
    onMoveEnemy: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragTarget by remember { mutableStateOf<String?>(null) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0D1117))
            .pointerInput(encounter) {
                detectTapGestures { offset ->
                    val cellW = size.width.toFloat() / GRID_COLS
                    val cellH = size.height.toFloat() / GRID_ROWS
                    val tapCol = (offset.x / cellW).toInt().coerceIn(0, GRID_COLS - 1)
                    val tapRow = (offset.y / cellH).toInt().coerceIn(0, GRID_ROWS - 1)

                    // Check if we tapped an enemy
                    val tapped = encounter.enemies.firstOrNull { e -> e.spawnCol == tapCol && e.spawnRow == tapRow }
                    if (tapped != null) {
                        onSelectInstance(if (selectedInstanceId == tapped.id) null else tapped.id)
                    } else if (selectedInstanceId != null) {
                        // Move selected enemy to tapped empty cell
                        onMoveEnemy(selectedInstanceId, tapCol, tapRow)
                    } else {
                        onSelectInstance(null)
                    }
                }
            }
    ) {
        val cellW = size.width / GRID_COLS
        val cellH = size.height / GRID_ROWS

        // Draw grid
        for (col in 0 until GRID_COLS) {
            for (row in 0 until GRID_ROWS) {
                val x = col * cellW
                val y = row * cellH
                // Alternate subtle tile shade
                val shade = if ((col + row) % 2 == 0) Color(0xFF161B22) else Color(0xFF0D1117)
                drawRect(color = shade, topLeft = Offset(x, y), size = Size(cellW, cellH))
                drawRect(
                    color = Color(0xFF21262D),
                    topLeft = Offset(x, y),
                    size = Size(cellW, cellH),
                    style = Stroke(width = 0.5f),
                )
            }
        }

        // Draw player spawn
        drawSpawnCell(encounter.playerSpawnCol, encounter.playerSpawnRow, Color(0xFF89B4FA), cellW, cellH, "P")

        // Draw enemies
        encounter.enemies.forEach { enemy ->
            val isSelected = enemy.id == selectedInstanceId
            drawEnemyCell(
                col = enemy.spawnCol,
                row = enemy.spawnRow,
                color = if (isSelected) Color(0xFFF38BA8) else Color(0xFFF38BA8).copy(alpha = 0.6f),
                outline = if (isSelected) Color(0xFFF38BA8) else Color(0xFFF38BA8).copy(alpha = 0.3f),
                label = enemy.name.take(1),
                cellW = cellW,
                cellH = cellH,
            )
        }
    }
}

private fun DrawScope.drawSpawnCell(col: Int, row: Int, color: Color, cellW: Float, cellH: Float, label: String) {
    val x = col * cellW
    val y = row * cellH
    val pad = 3f
    drawRect(
        color = color.copy(alpha = 0.2f),
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellW - pad * 2, cellH - pad * 2),
    )
    drawRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellW - pad * 2, cellH - pad * 2),
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawEnemyCell(
    col: Int, row: Int,
    color: Color, outline: Color,
    label: String,
    cellW: Float, cellH: Float,
) {
    val x = col * cellW
    val y = row * cellH
    val pad = 3f
    drawRect(
        color = color.copy(alpha = 0.3f),
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellW - pad * 2, cellH - pad * 2),
    )
    drawRect(
        color = outline,
        topLeft = Offset(x + pad, y + pad),
        size = Size(cellW - pad * 2, cellH - pad * 2),
        style = Stroke(width = 1.5f),
    )
}
