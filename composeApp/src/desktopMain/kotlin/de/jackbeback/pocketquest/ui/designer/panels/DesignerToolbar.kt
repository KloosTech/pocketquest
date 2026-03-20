package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.designer.model.OverworldNodeType
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.DesignerState
import de.jackbeback.pocketquest.ui.designer.DesignerViewModel
import de.jackbeback.pocketquest.ui.designer.EditorTab
import de.jackbeback.pocketquest.ui.designer.GraphInteractionMode
import de.jackbeback.pocketquest.ui.designer.util.showOpenDialog
import de.jackbeback.pocketquest.ui.designer.util.showSaveDialog

@Composable
internal fun DesignerToolbar(state: DesignerState, vm: DesignerViewModel) {
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
            Text("PocketQuest Designer", color = DC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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

        // "← Campaign" button when in ENCOUNTER tab with active campaign
        if (state.activeCampaign != null && state.activeTab == EditorTab.ENCOUNTER) {
            Box(Modifier.width(1.dp).height(20.dp).background(DC.Surface1))
            ToolbarButton("← Campaign") { vm.setActiveTab(EditorTab.CAMPAIGN) }
        }

        Spacer(Modifier.weight(1f))

        // Tab switcher
        TabButton("Campaign", state.activeTab == EditorTab.CAMPAIGN, DC.Mauve) { vm.setActiveTab(EditorTab.CAMPAIGN) }
        TabButton("Encounter", state.activeTab == EditorTab.ENCOUNTER, DC.Blue) { vm.setActiveTab(EditorTab.ENCOUNTER) }
        TabButton("Enemy", state.activeTab == EditorTab.ENEMY, DC.Red) { vm.setActiveTab(EditorTab.ENEMY) }
        TabButton("Skill", state.activeTab == EditorTab.SKILL, DC.Yellow) { vm.setActiveTab(EditorTab.SKILL) }
        TabButton("Map", state.activeTab == EditorTab.MAP, DC.Green) { vm.setActiveTab(EditorTab.MAP) }

        Spacer(Modifier.weight(1f))

        // Play preview
        val enc = state.encounters.find { it.id == state.selectedEncounterId }
        if (enc != null && state.activeTab == EditorTab.ENCOUNTER) {
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
internal fun GraphModeToolbar(
    mode: GraphInteractionMode,
    nodeType: OverworldNodeType,
    campaignDirty: Boolean,
    lastSavedMs: Long,
    onSetMode: (GraphInteractionMode) -> Unit,
    onSetNodeType: (OverworldNodeType) -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DC.Mantle)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("MODE:", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

        ModeButton("SELECT", mode == GraphInteractionMode.SELECT, DC.Blue) { onSetMode(GraphInteractionMode.SELECT) }
        ModeButton("ADD NODE", mode == GraphInteractionMode.ADD_NODE, DC.Green) { onSetMode(GraphInteractionMode.ADD_NODE) }
        ModeButton("ADD EDGE", mode == GraphInteractionMode.ADD_EDGE, DC.Sapphire) { onSetMode(GraphInteractionMode.ADD_EDGE) }
        ModeButton("DELETE", mode == GraphInteractionMode.DELETE, DC.Red) { onSetMode(GraphInteractionMode.DELETE) }

        Box(Modifier.width(1.dp).height(18.dp).background(DC.Surface1))
        Text("TYPE:", color = DC.Overlay0, fontSize = 10.sp, letterSpacing = 0.5.sp)
        OverworldNodeType.values().forEach { t ->
            val label = when (t) {
                OverworldNodeType.START  -> "▶ START"
                OverworldNodeType.BATTLE -> "⚔ BATTLE"
                OverworldNodeType.REST   -> "⛺ REST"
                OverworldNodeType.BOSS   -> "☠ BOSS"
            }
            val color = when (t) {
                OverworldNodeType.START  -> DC.Green
                OverworldNodeType.BATTLE -> DC.Blue
                OverworldNodeType.REST   -> DC.Yellow
                OverworldNodeType.BOSS   -> DC.Red
            }
            ModeButton(label, nodeType == t, color) { onSetNodeType(t) }
        }

        Spacer(Modifier.weight(1f))

        // Save status
        val saveText = if (campaignDirty) {
            "● Unsaved"
        } else if (lastSavedMs > 0L) {
            val ago = (System.currentTimeMillis() - lastSavedMs) / 1000
            when {
                ago < 5   -> "Saved just now"
                ago < 60  -> "Saved ${ago}s ago"
                ago < 3600 -> "Saved ${ago / 60}m ago"
                else      -> "Saved"
            }
        } else ""
        if (saveText.isNotEmpty()) {
            Text(
                saveText,
                color = if (campaignDirty) DC.Warning else DC.Success,
                fontSize = 10.sp,
            )
        }

        ToolbarButton("Save Campaign") { onSave() }
    }
}

@Composable
internal fun ModeButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val bg = if (active) activeColor.copy(alpha = 0.15f) else DC.Surface0.copy(alpha = 0.4f)
    val border = if (active) activeColor.copy(alpha = 0.4f) else DC.Surface1
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) activeColor else DC.Subtext0,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun ToolbarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
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
internal fun TabButton(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
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
