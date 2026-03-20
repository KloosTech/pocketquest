package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.designer.model.CampaignBundle
import de.jackbeback.pocketquest.designer.model.EncounterBundle
import de.jackbeback.pocketquest.designer.model.OverworldNodeType
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.DesignerState
import de.jackbeback.pocketquest.ui.designer.GraphSelectionKind
import de.jackbeback.pocketquest.ui.designer.GraphSelectionState

@Composable
fun CampaignInspectorPanel(
    state: DesignerState,
    onRenameCapmaign: (name: String, description: String) -> Unit,
    onUpdateOverworldMeta: (id: String, name: String, backgroundMapId: String?) -> Unit,
    onUpdateNodeLabel: (overworldId: String, nodeId: String, label: String) -> Unit,
    onUpdateNodeHealPercent: (overworldId: String, nodeId: String, v: Float) -> Unit,
    onAssignEncounter: (overworldId: String, nodeId: String, encId: String) -> Unit,
    onCreateEncounterForNode: (overworldId: String, nodeId: String) -> Unit,
    onEditEncounter: (encId: String) -> Unit,
    onDeleteEdge: (overworldId: String, fromId: String, toId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val campaign = state.activeCampaign ?: return
    val sel = state.graphSelection
    val overworld = campaign.overworlds.find { it.id == sel.overworldId }
        ?: campaign.overworlds.find { it.id == state.activeOverworldId }

    Column(
        modifier = modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (sel.kind) {
            GraphSelectionKind.NONE, GraphSelectionKind.CAMPAIGN -> {
                CampaignSection(campaign = campaign, onRename = onRenameCapmaign)
            }
            GraphSelectionKind.OVERWORLD -> {
                if (overworld != null) {
                    OverworldSection(
                        overworld = overworld,
                        mapOptions = listOf("(none)") + state.maps.map { it.id },
                        onUpdate = { name, bgMapId ->
                            onUpdateOverworldMeta(overworld.id, name, bgMapId?.takeIf { it != "(none)" })
                        },
                    )
                }
            }
            GraphSelectionKind.NODE -> {
                val nodeId = sel.nodeId ?: return@Column
                val node = overworld?.nodes?.find { it.id == nodeId } ?: return@Column
                NodeSection(
                    node = node,
                    overworldId = overworld.id,
                    campaignEncounters = state.campaignEncounters,
                    onUpdateLabel = { label -> onUpdateNodeLabel(overworld.id, nodeId, label) },
                    onUpdateHealPercent = { v -> onUpdateNodeHealPercent(overworld.id, nodeId, v) },
                    onAssignEncounter = { encId -> onAssignEncounter(overworld.id, nodeId, encId) },
                    onCreateEncounter = { onCreateEncounterForNode(overworld.id, nodeId) },
                    onEditEncounter = onEditEncounter,
                )
            }
            GraphSelectionKind.EDGE -> {
                val fromId = sel.edgeFromId ?: return@Column
                val toId = sel.edgeToId ?: return@Column
                EdgeSection(
                    fromLabel = overworld?.nodes?.find { it.id == fromId }?.label ?: fromId,
                    toLabel = overworld?.nodes?.find { it.id == toId }?.label ?: toId,
                    onDeleteEdge = {
                        if (overworld != null) onDeleteEdge(overworld.id, fromId, toId)
                    },
                )
            }
        }
    }
}

@Composable
private fun CampaignSection(campaign: CampaignBundle, onRename: (String, String) -> Unit) {
    var name by remember(campaign.id) { mutableStateOf(campaign.name) }
    var desc by remember(campaign.id) { mutableStateOf(campaign.description) }

    Text("CAMPAIGN", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

    FieldRow("Name") {
        DField(value = name, onValueChange = {
            name = it
            onRename(it, desc)
        })
    }
    FieldRow("Description") {
        DField(value = desc, singleLine = false, onValueChange = {
            desc = it
            onRename(name, it)
        })
    }
    Text("Overworlds: ${campaign.overworlds.size}", color = DC.Subtext0, fontSize = 11.sp)
}

@Composable
private fun OverworldSection(
    overworld: de.jackbeback.pocketquest.designer.model.OverworldDef,
    mapOptions: List<String>,
    onUpdate: (name: String, backgroundMapId: String?) -> Unit,
) {
    var name by remember(overworld.id) { mutableStateOf(overworld.name) }
    var selectedMap by remember(overworld.id) { mutableStateOf(overworld.backgroundMapId ?: "(none)") }

    Text("OVERWORLD", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

    FieldRow("Name") {
        DField(value = name, onValueChange = {
            name = it
            onUpdate(it, selectedMap)
        })
    }
    FieldRow("Background Map") {
        DropdownField(
            selected = selectedMap,
            options = mapOptions,
            onSelect = { opt ->
                selectedMap = opt
                onUpdate(name, opt)
            },
        )
    }
    Text("Nodes: ${overworld.nodes.size}", color = DC.Subtext0, fontSize = 11.sp)
    Text("Edges: ${overworld.edges.size}", color = DC.Subtext0, fontSize = 11.sp)
}

@Composable
private fun NodeSection(
    node: de.jackbeback.pocketquest.designer.model.OverworldNodeDef,
    overworldId: String,
    campaignEncounters: List<EncounterBundle>,
    onUpdateLabel: (String) -> Unit,
    onUpdateHealPercent: (Float) -> Unit,
    onAssignEncounter: (String) -> Unit,
    onCreateEncounter: () -> Unit,
    onEditEncounter: (String) -> Unit,
) {
    var label by remember(node.id) { mutableStateOf(node.label) }

    val typeLabel = when (node.type) {
        OverworldNodeType.START -> "START NODE"
        OverworldNodeType.BATTLE -> "BATTLE NODE"
        OverworldNodeType.REST -> "REST NODE"
        OverworldNodeType.BOSS -> "BOSS NODE"
    }

    Text(typeLabel, color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

    FieldRow("Label") {
        DField(value = label, onValueChange = {
            label = it
            onUpdateLabel(it)
        })
    }

    Text(
        "Position: ${"%.2f".format(node.x)}, ${"%.2f".format(node.y)}",
        color = DC.Subtext0,
        fontSize = 11.sp,
    )

    when (node.type) {
        OverworldNodeType.REST -> {
            FieldRow("Heal %") {
                FloatField(value = node.healPercent, onValueChange = onUpdateHealPercent)
            }
        }
        OverworldNodeType.BATTLE, OverworldNodeType.BOSS -> {
            HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
            Text("ENCOUNTER", color = DC.Overlay0, fontSize = 10.sp, letterSpacing = 0.8.sp)

            val encOptions = listOf("(none)") + campaignEncounters.map { it.id }
            val selectedEnc = node.encounterId ?: "(none)"
            DropdownField(
                selected = campaignEncounters.find { it.id == node.encounterId }?.name ?: "(none)",
                options = listOf("(none)") + campaignEncounters.map { it.name },
                onSelect = { opt ->
                    val enc = campaignEncounters.find { it.name == opt }
                    onAssignEncounter(enc?.id ?: "")
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Create New", onClick = onCreateEncounter)
                if (node.encounterId != null) {
                    SecondaryButton("Edit", onClick = { onEditEncounter(node.encounterId) })
                }
            }
        }
        OverworldNodeType.START -> { /* no extra fields */ }
    }
}

@Composable
private fun EdgeSection(
    fromLabel: String,
    toLabel: String,
    onDeleteEdge: () -> Unit,
) {
    Text("EDGE", color = DC.Overlay0, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
    Text("From: $fromLabel", color = DC.Subtext1, fontSize = 12.sp)
    Text("To: $toLabel", color = DC.Subtext1, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    DangerButton("Delete Edge", onClick = onDeleteEdge)
}
