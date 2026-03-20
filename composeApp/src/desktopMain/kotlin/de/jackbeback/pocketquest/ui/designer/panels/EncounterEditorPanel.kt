package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.map.MapConfig
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.designer.model.EncounterBundle
import de.jackbeback.pocketquest.designer.model.EnemyDefinition
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS
import de.jackbeback.pocketquest.game.battle.BattleTileCache
import de.jackbeback.pocketquest.ui.designer.DC
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun EncounterEditorPanel(
    encounter: EncounterBundle,
    enemyLibrary: List<EnemyDefinition>,
    availableMaps: List<TileMap> = emptyList(),
    onUpdate: (EncounterBundle) -> Unit,
    onDelete: () -> Unit,
    onAddEnemy: (EnemyDefinition) -> Unit,
    onRemoveEnemy: (String) -> Unit,
    onPlayPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedInstanceId by remember(encounter.id) { mutableStateOf<String?>(null) }
    val selectedInstance = encounter.enemies.find { it.id == selectedInstanceId }

    // Build a tile cache for the currently assigned map so the grid preview shows real tiles
    val selectedMap = availableMaps.find { it.id == encounter.mapId }
    val tileCache = remember(encounter.mapId) {
        selectedMap?.let { map ->
            BattleTileCache(MapConfig(
                id         = map.id,
                levelCount = 1,
                tileWidth  = map.tileWidthPx,
                tileHeight = map.tileHeightPx,
                colCount   = map.cols,
                rowCount   = map.rows,
            ))
        }
    }
    @Suppress("UNCHECKED_CAST")
    val tiles by remember(tileCache) {
        tileCache?.tiles ?: MutableStateFlow(emptyMap<Pair<Int, Int>, ImageBitmap>())
    }.collectAsState()

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

        // ── Map ───────────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Map")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Map:", color = DC.Subtext0, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                    // "(none)" option + all available maps
                    val options = listOf("(none)") + availableMaps.map { it.id }
                    val currentIndex = options.indexOf(encounter.mapId ?: "(none)").coerceAtLeast(0)
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DC.InputBg)
                                .border(1.dp, DC.InputBorder, RoundedCornerShape(4.dp))
                                .clickable { expanded = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(options[currentIndex], color = DC.Text, fontSize = 12.sp)
                            Text("▾", color = DC.Overlay0, fontSize = 10.sp)
                        }
                        if (expanded) {
                            Popup(
                                onDismissRequest = { expanded = false },
                                alignment = Alignment.TopStart,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .background(DC.Surface0, RoundedCornerShape(4.dp))
                                        .border(1.dp, DC.PanelBorder, RoundedCornerShape(4.dp))
                                        .padding(4.dp),
                                ) {
                                    options.forEach { opt ->
                                        Text(
                                            opt,
                                            color = if (opt == (encounter.mapId ?: "(none)")) DC.Blue else DC.Text,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onUpdate(encounter.copy(mapId = if (opt == "(none)") null else opt))
                                                    expanded = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Amber warning if no map is selected but enemies have spawn positions
                    if (encounter.mapId == null && encounter.enemies.isNotEmpty()) {
                        Text("⚠ No map assigned", color = DC.Warning, fontSize = 10.sp)
                    }
                }
            }
        }

        // ── Player Spawn ──────────────────────────────────────────────────
        item {
            val maxCol = (selectedMap?.cols ?: BATTLE_COLS) - 1
            val maxRow = (selectedMap?.rows ?: BATTLE_ROWS) - 1
            EditorCard {
                CardLabel("Player Spawn")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("Column (0–$maxCol)") {
                            IntField(encounter.playerSpawnCol) { onUpdate(encounter.copy(playerSpawnCol = it.coerceIn(0, maxCol))) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Row (0–$maxRow)") {
                            IntField(encounter.playerSpawnRow) { onUpdate(encounter.copy(playerSpawnRow = it.coerceIn(0, maxRow))) }
                        }
                    }
                }
            }
        }

        // ── Battle Grid ───────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Battle Grid Preview  (click enemy → select · click empty → move enemy or set player spawn)")
                BattleGridPreview(
                    encounter = encounter,
                    selectedInstanceId = selectedInstanceId,
                    selectedMap = selectedMap,
                    tiles = tiles,
                    onSelectInstance = { selectedInstanceId = it },
                    onMoveEnemy = { instanceId, col, row ->
                        val updated = encounter.enemies.map { e ->
                            if (e.id == instanceId) e.copy(spawnCol = col, spawnRow = row) else e
                        }
                        onUpdate(encounter.copy(enemies = updated))
                    },
                    onMovePlayer = { col, row ->
                        val maxCol = (selectedMap?.cols ?: BATTLE_COLS) - 1
                        val maxRow = (selectedMap?.rows ?: BATTLE_ROWS) - 1
                        onUpdate(encounter.copy(
                            playerSpawnCol = col.coerceIn(0, maxCol),
                            playerSpawnRow = row.coerceIn(0, maxRow),
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(280.dp),
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
            val maxCol = (selectedMap?.cols ?: BATTLE_COLS) - 1
            val maxRow = (selectedMap?.rows ?: BATTLE_ROWS) - 1
            EnemyInstanceRow(
                enemy = enemy,
                isSelected = selectedInstanceId == enemy.id,
                maxCol = maxCol,
                maxRow = maxRow,
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
    maxCol: Int = BATTLE_COLS - 1,
    maxRow: Int = BATTLE_ROWS - 1,
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
                    IntField(enemy.spawnCol) { onUpdate(enemy.copy(spawnCol = it.coerceIn(0, maxCol))) }
                }
                Text("Row", color = DC.Overlay0, fontSize = 11.sp)
                Box(Modifier.width(50.dp)) {
                    IntField(enemy.spawnRow) { onUpdate(enemy.copy(spawnRow = it.coerceIn(0, maxRow))) }
                }
                Spacer(Modifier.weight(1f))
                Text("Skills: ${enemy.skillIds.joinToString(", ")}", color = DC.Sapphire, fontSize = 10.sp)
            }
        }
    }
}

