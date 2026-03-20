package de.jackbeback.pocketquest.ui.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.ui.designer.panels.*

@Composable
fun EncounterDesignerApp() {
    val vm = remember { DesignerViewModel() }
    val state by vm.state.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DC.Background,
            surface    = DC.Panel,
            primary    = DC.Primary,
            onBackground = DC.Text,
            onSurface  = DC.Text,
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(DC.Background)) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Top toolbar ───────────────────────────────────────────
                DesignerToolbar(state = state, vm = vm)

                HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

                // ── Graph mode toolbar (CAMPAIGN tab only) ────────────────
                if (state.activeTab == EditorTab.CAMPAIGN && state.activeCampaign != null) {
                    GraphModeToolbar(
                        mode          = state.graphMode,
                        nodeType      = state.graphNodeTypeToPlace,
                        campaignDirty = state.campaignDirty,
                        lastSavedMs   = state.campaignLastSavedMs,
                        onSetMode     = vm::setGraphMode,
                        onSetNodeType = vm::setGraphNodeTypeToPlace,
                        onSave        = vm::saveCampaignNow,
                    )
                    HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)
                }

                // ── Main 3-column layout ──────────────────────────────────
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    // Left: Library
                    LibraryPanel(
                        state               = state,
                        onSelectEncounter   = vm::selectEncounter,
                        onNewEncounter      = vm::newEncounter,
                        onSelectEnemy       = vm::selectEnemy,
                        onNewEnemy          = vm::newEnemy,
                        onSelectSkill       = vm::selectSkill,
                        onNewSkill          = vm::newSkill,
                        onSelectOverworld   = vm::selectOverworld,
                        onNewOverworld      = vm::newOverworld,
                        onOpenCampaignBrowser = vm::showCampaignBrowser,
                        modifier            = Modifier.width(240.dp).fillMaxHeight(),
                    )

                    Box(
                        Modifier.width(1.dp).fillMaxHeight().background(DC.PanelBorder)
                    )

                    // Center: Context editor
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        when (state.activeTab) {
                            EditorTab.ENCOUNTER -> {
                                val enc = state.encounters.find { it.id == state.selectedEncounterId }
                                if (enc != null) {
                                    EncounterEditorPanel(
                                        encounter      = enc,
                                        enemyLibrary   = state.enemyLibrary,
                                        availableMaps  = state.maps,
                                        onUpdate       = vm::updateEncounter,
                                        onDelete       = { vm.deleteEncounter(enc.id) },
                                        onAddEnemy     = { vm.addEnemyToEncounter(it, enc.id) },
                                        onRemoveEnemy  = { vm.removeEnemyFromEncounter(enc.id, it) },
                                        onPlayPreview  = { vm.startBattlePreview(enc) },
                                        modifier       = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    EmptyEditorPlaceholder(
                                        message = "Select or create an encounter to begin",
                                        hint    = "Use the Library panel on the left →",
                                    )
                                }
                            }
                            EditorTab.ENEMY -> {
                                val enemy = state.enemyLibrary.find { it.id == state.selectedEnemyId }
                                if (enemy != null) {
                                    EnemyEditorPanel(
                                        enemy              = enemy,
                                        availableSkillIds  = state.skillLibrary.map { it.id },
                                        onUpdate           = vm::updateEnemy,
                                        onDelete           = { vm.deleteEnemy(enemy.id) },
                                        modifier           = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    EmptyEditorPlaceholder(
                                        message = "Select or create an enemy to begin",
                                        hint    = "Enemies define the characters that appear in encounters",
                                    )
                                }
                            }
                            EditorTab.SKILL -> {
                                val skill = state.skillLibrary.find { it.id == state.selectedSkillId }
                                if (skill != null) {
                                    SkillEditorPanel(
                                        skill    = skill,
                                        onUpdate = vm::updateSkill,
                                        onDelete = { vm.deleteSkill(skill.id) },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    EmptyEditorPlaceholder(
                                        message = "Select or create a skill to begin",
                                        hint    = "Skills can be assigned to any enemy or player",
                                    )
                                }
                            }
                            EditorTab.MAP -> {
                                MapEditorPanel(
                                    maps          = state.maps,
                                    selectedMapId = state.selectedMapId,
                                    onMapExported = vm::addMap,
                                    onMapLoaded   = vm::addMap,
                                    onSelectMap   = vm::selectMap,
                                    modifier      = Modifier.fillMaxSize(),
                                )
                            }
                            EditorTab.CAMPAIGN -> {
                                val campaign  = state.activeCampaign
                                val overworld = campaign?.overworlds?.find { it.id == state.activeOverworldId }
                                when {
                                    campaign == null -> EmptyEditorPlaceholder(
                                        message = "No campaign open",
                                        hint    = "Open or create a campaign to get started",
                                    )
                                    overworld == null -> EmptyEditorPlaceholder(
                                        message = "No overworld selected",
                                        hint    = "Select or create one in the library",
                                    )
                                    else -> OverworldGraphPanel(
                                        overworld         = overworld,
                                        backgroundMap     = state.maps.find { it.id == overworld.backgroundMapId },
                                        interactionMode   = state.graphMode,
                                        nodeTypeToPlace   = state.graphNodeTypeToPlace,
                                        selection         = state.graphSelection,
                                        edgePendingFromId = state.edgePendingFromId,
                                        onPlaceNode       = { t, x, y -> vm.placeNode(overworld.id, t, x, y) },
                                        onMoveNode        = { nid, x, y -> vm.moveNode(overworld.id, nid, x, y) },
                                        onSelectNode      = { vm.selectGraphNode(overworld.id, it) },
                                        onSelectEdge      = { f, t -> vm.selectGraphEdge(overworld.id, f, t) },
                                        onDeleteNode      = { vm.deleteNode(overworld.id, it) },
                                        onDeleteEdge      = { f, t -> vm.deleteEdge(overworld.id, f, t) },
                                        onBeginEdge       = { vm.beginEdgePlacement(overworld.id, it) },
                                        onCompleteEdge    = { vm.completeEdgePlacement(overworld.id, it) },
                                        onClearSelection  = vm::clearGraphSelection,
                                        onChangeNodeType  = { nodeId, type -> vm.changeNodeType(overworld.id, nodeId, type) },
                                        modifier          = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        Modifier.width(1.dp).fillMaxHeight().background(DC.PanelBorder)
                    )

                    // Right: Inspector
                    if (state.activeTab == EditorTab.CAMPAIGN) {
                        CampaignInspectorPanel(
                            state                  = state,
                            onRenameCapmaign       = vm::renameCampaign,
                            onUpdateOverworldMeta  = vm::updateOverworldMeta,
                            onUpdateNodeLabel      = vm::updateNodeLabel,
                            onUpdateNodeHealPercent = vm::updateNodeHealPercent,
                            onAssignEncounter      = vm::assignEncounterToNode,
                            onCreateEncounterForNode = vm::createEncounterForNode,
                            onEditEncounter        = vm::editEncounterForNode,
                            onDeleteEdge           = vm::deleteEdge,
                            modifier               = Modifier.width(220.dp).fillMaxHeight().background(DC.Panel),
                        )
                    } else {
                        DesignerInspectorPanel(
                            state    = state,
                            modifier = Modifier.width(220.dp).fillMaxHeight().background(DC.Panel),
                        )
                    }
                }

                // ── Status bar ────────────────────────────────────────────
                StatusBar(
                    message            = state.statusMessage,
                    isError            = state.statusIsError,
                    isDirty            = state.isDirty || state.campaignDirty,
                    filePath           = state.currentFilePath,
                    campaignName       = state.activeCampaign?.name,
                    campaignLastSavedMs = state.campaignLastSavedMs,
                    campaignDirty      = state.campaignDirty,
                )
            }

            // ── Battle preview overlay ────────────────────────────────────
            if (state.showBattlePreview) {
                val previewVm = vm.previewBattleVm
                if (previewVm != null) {
                    val enc = state.encounters.find { it.id == state.selectedEncounterId }
                    BattlePreviewOverlay(
                        viewModel     = previewVm,
                        encounterName = enc?.name ?: "Preview",
                        onClose       = vm::closeBattlePreview,
                    )
                }
            }

            // ── Campaign browser overlay ──────────────────────────────────
            if (state.showCampaignBrowser) {
                CampaignBrowser(
                    config    = state.designerConfig,
                    onOpen    = vm::openCampaign,
                    onNew     = { name, _ -> vm.newCampaign(name) },
                    onDismiss = if (state.activeCampaign != null) vm::closeCampaignBrowser else null,
                )
            }
        }
    }
}
