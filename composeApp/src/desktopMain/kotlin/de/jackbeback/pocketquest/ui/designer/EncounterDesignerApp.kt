package de.jackbeback.pocketquest.ui.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.designer.io.EncounterFileIo
import de.jackbeback.pocketquest.ui.designer.panels.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

@Composable
fun EncounterDesignerApp() {
    val vm = remember { DesignerViewModel() }
    val state by vm.state.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DC.Background,
            surface = DC.Panel,
            primary = DC.Primary,
            onBackground = DC.Text,
            onSurface = DC.Text,
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(DC.Background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top toolbar ───────────────────────────────────────────
                DesignerToolbar(
                    state = state,
                    vm = vm,
                )

                HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

                // ── Main 3-column layout ──────────────────────────────────
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Left: Library
                    LibraryPanel(
                        state = state,
                        onSelectEncounter = vm::selectEncounter,
                        onNewEncounter = vm::newEncounter,
                        onSelectEnemy = vm::selectEnemy,
                        onNewEnemy = vm::newEnemy,
                        onSelectSkill = vm::selectSkill,
                        onNewSkill = vm::newSkill,
                        modifier = Modifier.width(240.dp).fillMaxHeight(),
                    )

                    // Divider
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DC.PanelBorder))

                    // Center: Editor
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (state.activeTab) {
                            EditorTab.ENCOUNTER -> {
                                val enc = state.encounters.find { it.id == state.selectedEncounterId }
                                if (enc != null) {
                                    EncounterEditorPanel(
                                        encounter = enc,
                                        enemyLibrary = state.enemyLibrary,
                                        onUpdate = vm::updateEncounter,
                                        onDelete = { vm.deleteEncounter(enc.id) },
                                        onAddEnemy = { vm.addEnemyToEncounter(it, enc.id) },
                                        onRemoveEnemy = { vm.removeEnemyFromEncounter(enc.id, it) },
                                        onPlayPreview = { vm.startBattlePreview(enc) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    EmptyEditorPlaceholder(
                                        message = "Select or create an encounter to begin",
                                        hint = "Use the Library panel on the left →",
                                    )
                                }
                            }
                            EditorTab.ENEMY -> {
                                val enemy = state.enemyLibrary.find { it.id == state.selectedEnemyId }
                                if (enemy != null) {
                                    EnemyEditorPanel(
                                        enemy = enemy,
                                        availableSkillIds = state.skillLibrary.map { it.id },
                                        onUpdate = vm::updateEnemy,
                                        onDelete = { vm.deleteEnemy(enemy.id) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    EmptyEditorPlaceholder(
                                        message = "Select or create an enemy to begin",
                                        hint = "Enemies define the characters that appear in encounters",
                                    )
                                }
                            }
                            EditorTab.SKILL -> {
                                val skill = state.skillLibrary.find { it.id == state.selectedSkillId }
                                if (skill != null) {
                                    SkillEditorPanel(
                                        skill = skill,
                                        onUpdate = vm::updateSkill,
                                        onDelete = { vm.deleteSkill(skill.id) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    EmptyEditorPlaceholder(
                                        message = "Select or create a skill to begin",
                                        hint = "Skills can be assigned to any enemy or player",
                                    )
                                }
                            }
                        }
                    }

                    // Divider
                    Box(Modifier.width(1.dp).fillMaxHeight().background(DC.PanelBorder))

                    // Right: Quick Stats Panel
                    DesignerInspectorPanel(
                        state = state,
                        modifier = Modifier.width(220.dp).fillMaxHeight(),
                    )
                }

                // ── Status bar ────────────────────────────────────────────
                StatusBar(
                    message = state.statusMessage,
                    isError = state.statusIsError,
                    isDirty = state.isDirty,
                    filePath = state.currentFilePath,
                )
            }

            // ── Battle preview overlay (full-screen) ──────────────────────
            if (state.showBattlePreview) {
                val previewVm = vm.previewBattleVm
                if (previewVm != null) {
                    val enc = state.encounters.find { it.id == state.selectedEncounterId }
                    BattlePreviewOverlay(
                        viewModel = previewVm,
                        encounterName = enc?.name ?: "Preview",
                        onClose = vm::closeBattlePreview,
                    )
                }
            }
        }
    }
}

// ── Toolbar ───────────────────────────────────────────────────────────────────

@Composable
private fun DesignerToolbar(state: DesignerState, vm: DesignerViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DC.Mantle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Logo
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(DC.Primary),
                contentAlignment = Alignment.Center,
            ) { Text("⚔", fontSize = 11.sp) }
            Text("Encounter Designer", color = DC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.width(8.dp))

        // File ops
        ToolbarButton("New Encounter") { vm.newEncounter() }
        ToolbarButton("New Enemy") { vm.newEnemy() }
        ToolbarButton("New Skill") { vm.newSkill() }

        Box(Modifier.width(1.dp).height(20.dp).background(DC.Surface1))

        ToolbarButton("Open File…") {
            val file = showOpenDialog()
            if (file != null) vm.loadEncounterFromFile(file)
        }

        val selectedEncounter = state.encounters.find { it.id == state.selectedEncounterId }
        ToolbarButton("Save…", enabled = selectedEncounter != null) {
            if (selectedEncounter != null) {
                val file = showSaveDialog(selectedEncounter.name)
                if (file != null) vm.saveEncounter(selectedEncounter, file)
            }
        }

        Spacer(Modifier.weight(1f))

        // Tab switcher
        TabButton("Encounter", state.activeTab == EditorTab.ENCOUNTER, DC.Blue) { vm.setActiveTab(EditorTab.ENCOUNTER) }
        TabButton("Enemy", state.activeTab == EditorTab.ENEMY, DC.Red) { vm.setActiveTab(EditorTab.ENEMY) }
        TabButton("Skill", state.activeTab == EditorTab.SKILL, DC.Mauve) { vm.setActiveTab(EditorTab.SKILL) }

        Spacer(Modifier.weight(1f))

        // Play preview
        val enc = state.encounters.find { it.id == state.selectedEncounterId }
        if (enc != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DC.Green.copy(alpha = 0.15f))
                    .border(1.dp, DC.Green.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .clickable { vm.startBattlePreview(enc) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶  Play Encounter", color = DC.Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ToolbarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) DC.Subtext1 else DC.Overlay0
    Text(
        label,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun TabButton(label: String, active: Boolean, activeColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val bg = if (active) activeColor.copy(alpha = 0.15f) else DC.Surface0.copy(alpha = 0.5f)
    val border = if (active) activeColor.copy(alpha = 0.4f) else DC.Surface1
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) activeColor else DC.Subtext0,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Inspector ─────────────────────────────────────────────────────────────────

@Composable
private fun DesignerInspectorPanel(state: DesignerState, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(DC.Panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("STATS", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

        StatRow("Encounters", state.encounters.size.toString())
        StatRow("Enemy Types", state.enemyLibrary.size.toString())
        StatRow("Skills", state.skillLibrary.size.toString())

        val enc = state.encounters.find { it.id == state.selectedEncounterId }
        if (enc != null) {
            HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
            Text("CURRENT ENCOUNTER", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            StatRow("Name", enc.name.ifBlank { "—" })
            StatRow("Enemies", enc.enemies.size.toString())
            StatRow("Player spawn", "${enc.playerSpawnCol}, ${enc.playerSpawnRow}")
            if (enc.enemies.isNotEmpty()) {
                HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
                Text("ENEMIES", color = DC.Overlay0, fontSize = 10.sp, letterSpacing = 0.8.sp)
                enc.enemies.forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(e.name, color = DC.Subtext1, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("HP ${e.maxHealth}", color = DC.Overlay0, fontSize = 10.sp)
                    }
                }
            }
        }

        val skill = state.skillLibrary.find { it.id == state.selectedSkillId }
        if (skill != null && state.activeTab == EditorTab.SKILL) {
            HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
            Text("SELECTED SKILL", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            StatRow("Name", skill.name)
            StatRow("Mana", skill.manaCost.toString())
            StatRow("Range", skill.range.toString())
            StatRow("Effects", skill.effects.size.toString())
        }

        val enemy = state.enemyLibrary.find { it.id == state.selectedEnemyId }
        if (enemy != null && state.activeTab == EditorTab.ENEMY) {
            HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
            Text("SELECTED ENEMY", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            StatRow("HP", enemy.maxHealth.toString())
            StatRow("AC", enemy.stats.ac.toString())
            StatRow("Initiative", enemy.initiative.toString())
            StatRow("AI", enemy.aiStrategy)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DC.Subtext0, fontSize = 11.sp)
        Text(value, color = DC.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(message: String, isError: Boolean, isDirty: Boolean, filePath: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DC.Mantle)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isError) DC.Error else if (isDirty) DC.Warning else DC.Success)
        )
        Text(
            message,
            color = if (isError) DC.Error else DC.Subtext0,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        if (isDirty) Text("● Unsaved changes", color = DC.Warning, fontSize = 10.sp)
        if (filePath != null) {
            Text(
                filePath.let { if (it.length > 55) "…${it.takeLast(52)}" else it },
                color = DC.Overlay0,
                fontSize = 10.sp,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyEditorPlaceholder(message: String, hint: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(DC.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚔", fontSize = 40.sp, color = DC.Surface1)
            Text(message, color = DC.Subtext0, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = DC.Overlay0, fontSize = 12.sp)
        }
    }
}

// ── File dialogs (AWT) ────────────────────────────────────────────────────────

private fun showOpenDialog(): File? {
    val dialog = FileDialog(null as Frame?, "Open Encounter", FileDialog.LOAD).apply {
        directory = EncounterFileIo.defaultDirectory.absolutePath
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json") }
        isVisible = true
    }
    val dir  = dialog.directory ?: return null
    val file = dialog.file     ?: return null
    return File(dir, file)
}

private fun showSaveDialog(suggestedName: String): File? {
    val dialog = FileDialog(null as Frame?, "Save Encounter", FileDialog.SAVE).apply {
        directory = EncounterFileIo.defaultDirectory.absolutePath
        file = "$suggestedName.json"
        isVisible = true
    }
    val dir  = dialog.directory ?: return null
    val name = dialog.file     ?: return null
    val f = File(dir, if (name.endsWith(".json")) name else "$name.json")
    return f
}
