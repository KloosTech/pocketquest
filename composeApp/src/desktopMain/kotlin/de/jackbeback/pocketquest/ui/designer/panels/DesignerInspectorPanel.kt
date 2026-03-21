package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.DesignerState
import de.jackbeback.pocketquest.ui.designer.EditorTab

@Composable
internal fun DesignerInspectorPanel(state: DesignerState, modifier: Modifier) {
    Column(
        modifier = modifier
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
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DC.Subtext0, fontSize = 11.sp)
        Text(value, color = DC.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
