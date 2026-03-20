package de.jackbeback.pocketquest.ui.designer

import de.jackbeback.pocketquest.content.definitions.allSkills
import de.jackbeback.pocketquest.content.definitions.gruntTemplate
import de.jackbeback.pocketquest.content.definitions.veteranGruntTemplate
import de.jackbeback.pocketquest.content.definitions.chieftainTemplate
import de.jackbeback.pocketquest.content.definitions.wizardTemplate
import de.jackbeback.pocketquest.content.dsl.spawnIntoWorld
import de.jackbeback.pocketquest.content.map.MapConfig
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.map.throneRoomConfig
import de.jackbeback.pocketquest.designer.io.CampaignFileIo
import de.jackbeback.pocketquest.designer.io.EncounterFileIo
import de.jackbeback.pocketquest.designer.io.MapFileIo
import de.jackbeback.pocketquest.designer.model.CampaignBundle
import de.jackbeback.pocketquest.designer.model.DesignerConfig
import de.jackbeback.pocketquest.designer.model.EffectDto
import de.jackbeback.pocketquest.designer.model.EncounterBundle
import de.jackbeback.pocketquest.designer.model.EnemyDefinition
import de.jackbeback.pocketquest.designer.model.OverworldDef
import de.jackbeback.pocketquest.designer.model.OverworldEdgeDef
import de.jackbeback.pocketquest.designer.model.OverworldNodeDef
import de.jackbeback.pocketquest.designer.model.OverworldNodeType
import de.jackbeback.pocketquest.designer.model.SkillDefinition
import de.jackbeback.pocketquest.designer.model.builtInSkillDefinitions
import de.jackbeback.pocketquest.designer.model.toEnemyDefinition
import de.jackbeback.pocketquest.designer.model.toSkillDefinition
import de.jackbeback.pocketquest.designer.model.toSkillTemplate
import de.jackbeback.pocketquest.designer.model.toUnitTemplate
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.animation.AnimationEventCollector
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS
import de.jackbeback.pocketquest.game.battle.BattleTileCache
import de.jackbeback.pocketquest.game.battle.buildBattleSystemRegistry
import de.jackbeback.pocketquest.game.loop.GameLoop
import de.jackbeback.pocketquest.game.snapshot.BattleLog
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ui.battle.BattleViewModel
import de.jackbeback.pocketquest.ui.navigation.BattleParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class EditorTab { ENCOUNTER, ENEMY, SKILL, MAP, CAMPAIGN }
enum class SelectionType { ENCOUNTER, ENEMY, SKILL, NONE }

enum class GraphInteractionMode { SELECT, ADD_NODE, ADD_EDGE, DELETE }

enum class GraphSelectionKind { NONE, CAMPAIGN, OVERWORLD, NODE, EDGE }
data class GraphSelectionState(
    val kind: GraphSelectionKind = GraphSelectionKind.NONE,
    val overworldId: String? = null,
    val nodeId: String? = null,
    val edgeFromId: String? = null,
    val edgeToId: String? = null,
)

data class DesignerState(
    val encounters: List<EncounterBundle> = emptyList(),
    val enemyLibrary: List<EnemyDefinition> = emptyList(),
    val skillLibrary: List<SkillDefinition> = emptyList(),
    val maps: List<TileMap> = emptyList(),
    val activeTab: EditorTab = EditorTab.CAMPAIGN,
    val selectedEncounterId: String? = null,
    val selectedEnemyId: String? = null,
    val selectedSkillId: String? = null,
    val selectedMapId: String? = null,
    val isDirty: Boolean = false,
    val currentFilePath: String? = null,
    val showBattlePreview: Boolean = false,
    val statusMessage: String = "Ready — open or create a campaign to start",
    val statusIsError: Boolean = false,
    // Campaign fields
    val activeCampaign: CampaignBundle? = null,
    val campaignEncounters: List<EncounterBundle> = emptyList(),
    val activeOverworldId: String? = null,
    val graphSelection: GraphSelectionState = GraphSelectionState(),
    val graphMode: GraphInteractionMode = GraphInteractionMode.SELECT,
    val graphNodeTypeToPlace: OverworldNodeType = OverworldNodeType.BATTLE,
    val edgePendingFromId: String? = null,
    val campaignDirty: Boolean = false,
    val campaignLastSavedMs: Long = 0L,
    val showCampaignBrowser: Boolean = true,
    val designerConfig: DesignerConfig = DesignerConfig(),
)

class DesignerViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(
        DesignerState(
            skillLibrary = builtInSkillDefinitions,
            enemyLibrary = listOf(
                gruntTemplate.toEnemyDefinition(),
                veteranGruntTemplate.toEnemyDefinition(),
                chieftainTemplate.toEnemyDefinition(),
            ),
        )
    )
    val state: StateFlow<DesignerState> = _state

    init {
        findResourcesDir()
            ?.let { File(it, "files/maps") }
            ?.takeIf { it.isDirectory }
            ?.also { loadMapsFromDir(it) }

        CampaignFileIo.loadConfig().onSuccess { cfg ->
            _state.update { it.copy(designerConfig = cfg) }
            cfg.lastOpenedCampaignDir?.let { p ->
                val dir = File(p)
                if (dir.exists()) openCampaign(dir)
            }
        }

        scope.launch {
            while (true) {
                delay(60_000L)
                val s = _state.value
                if (s.campaignDirty && s.activeCampaign != null) saveCampaignNow()
            }
        }
    }

    // ── Preview battle setup ──────────────────────────────────────────────────
    private var _previewBattleVm: BattleViewModel? = null
    val previewBattleVm: BattleViewModel? get() = _previewBattleVm

    // ── Encounter CRUD ────────────────────────────────────────────────────────

    fun newEncounter() {
        val id = "encounter_${System.currentTimeMillis()}"
        val enc = EncounterBundle(id = id, name = "New Encounter")
        _state.update { s ->
            s.copy(
                encounters = s.encounters + enc,
                selectedEncounterId = id,
                activeTab = EditorTab.ENCOUNTER,
                isDirty = true,
                statusMessage = "Created new encounter",
            )
        }
    }

    fun updateEncounter(updated: EncounterBundle) {
        _state.update { s ->
            val activeCampaign = s.activeCampaign
            if (activeCampaign != null) {
                CampaignFileIo.saveEncounterInCampaign(activeCampaign.id, updated)
                s.copy(
                    encounters = s.encounters.map { if (it.id == updated.id) updated else it },
                    campaignEncounters = s.campaignEncounters.map { if (it.id == updated.id) updated else it },
                    isDirty = true,
                    campaignDirty = true,
                )
            } else {
                s.copy(
                    encounters = s.encounters.map { if (it.id == updated.id) updated else it },
                    isDirty = true,
                )
            }
        }
    }

    fun deleteEncounter(id: String) {
        _state.update { s ->
            val sel = if (s.selectedEncounterId == id) null else s.selectedEncounterId
            s.copy(
                encounters = s.encounters.filter { it.id != id },
                selectedEncounterId = sel,
                isDirty = true,
                statusMessage = "Encounter deleted",
            )
        }
    }

    fun selectEncounter(id: String) {
        _state.update { it.copy(selectedEncounterId = id, activeTab = EditorTab.ENCOUNTER) }
    }

    // ── Enemy library CRUD ────────────────────────────────────────────────────

    fun newEnemy() {
        val id = "enemy_${System.currentTimeMillis()}"
        val enemy = EnemyDefinition(id = id, name = "New Enemy")
        _state.update { s ->
            s.copy(
                enemyLibrary = s.enemyLibrary + enemy,
                selectedEnemyId = id,
                activeTab = EditorTab.ENEMY,
                isDirty = true,
                statusMessage = "Created new enemy",
            )
        }
    }

    fun updateEnemy(updated: EnemyDefinition) {
        _state.update { s ->
            s.copy(
                enemyLibrary = s.enemyLibrary.map { if (it.id == updated.id) updated else it },
                isDirty = true,
            )
        }
    }

    fun deleteEnemy(id: String) {
        _state.update { s ->
            val sel = if (s.selectedEnemyId == id) null else s.selectedEnemyId
            s.copy(
                enemyLibrary = s.enemyLibrary.filter { it.id != id },
                selectedEnemyId = sel,
                isDirty = true,
                statusMessage = "Enemy deleted",
            )
        }
    }

    fun selectEnemy(id: String) {
        _state.update { it.copy(selectedEnemyId = id, activeTab = EditorTab.ENEMY) }
    }

    fun addEnemyToEncounter(enemyDef: EnemyDefinition, encounterId: String) {
        _state.update { s ->
            val enc = s.encounters.find { it.id == encounterId } ?: return@update s
            val instanceId = "${enemyDef.id}_${System.currentTimeMillis()}"
            val instance = enemyDef.copy(id = instanceId)
            val updated = enc.copy(enemies = enc.enemies + instance)
            s.copy(
                encounters = s.encounters.map { if (it.id == encounterId) updated else it },
                isDirty = true,
                statusMessage = "${enemyDef.name} added to encounter",
            )
        }
    }

    fun updateEnemyInEncounter(encounterId: String, updated: EnemyDefinition) {
        _state.update { s ->
            val enc = s.encounters.find { it.id == encounterId } ?: return@update s
            val newEnc = enc.copy(enemies = enc.enemies.map { if (it.id == updated.id) updated else it })
            s.copy(
                encounters = s.encounters.map { if (it.id == encounterId) newEnc else it },
                isDirty = true,
            )
        }
    }

    fun removeEnemyFromEncounter(encounterId: String, enemyInstanceId: String) {
        _state.update { s ->
            val enc = s.encounters.find { it.id == encounterId } ?: return@update s
            val newEnc = enc.copy(enemies = enc.enemies.filter { it.id != enemyInstanceId })
            s.copy(
                encounters = s.encounters.map { if (it.id == encounterId) newEnc else it },
                isDirty = true,
                statusMessage = "Enemy removed from encounter",
            )
        }
    }

    // ── Skill library CRUD ────────────────────────────────────────────────────

    fun newSkill() {
        val id = "skill_${System.currentTimeMillis()}"
        val skill = SkillDefinition(
            id = id,
            name = "New Skill",
            effects = listOf(EffectDto("damage", count = 1, sides = 6, damageType = "BLUDGEONING")),
        )
        _state.update { s ->
            s.copy(
                skillLibrary = s.skillLibrary + skill,
                selectedSkillId = id,
                activeTab = EditorTab.SKILL,
                isDirty = true,
                statusMessage = "Created new skill",
            )
        }
    }

    fun updateSkill(updated: SkillDefinition) {
        _state.update { s ->
            s.copy(
                skillLibrary = s.skillLibrary.map { if (it.id == updated.id) updated else it },
                isDirty = true,
            )
        }
    }

    fun deleteSkill(id: String) {
        _state.update { s ->
            val sel = if (s.selectedSkillId == id) null else s.selectedSkillId
            s.copy(
                skillLibrary = s.skillLibrary.filter { it.id != id },
                selectedSkillId = sel,
                isDirty = true,
                statusMessage = "Skill deleted",
            )
        }
    }

    fun selectSkill(id: String) {
        _state.update { it.copy(selectedSkillId = id, activeTab = EditorTab.SKILL) }
    }

    // ── Map library CRUD ──────────────────────────────────────────────────────

    fun addMap(map: TileMap) {
        _state.update { s ->
            val exists = s.maps.any { it.id == map.id }
            val updated = if (exists) s.maps.map { if (it.id == map.id) map else it } else s.maps + map
            s.copy(
                maps = updated,
                selectedMapId = map.id,
                activeTab = EditorTab.MAP,
                statusMessage = "Map '${map.name}' loaded",
            )
        }
    }

    fun removeMap(id: String) {
        _state.update { s ->
            val sel = if (s.selectedMapId == id) null else s.selectedMapId
            s.copy(
                maps = s.maps.filter { it.id != id },
                selectedMapId = sel,
                statusMessage = "Map removed",
            )
        }
    }

    fun selectMap(id: String) {
        _state.update { it.copy(selectedMapId = id, activeTab = EditorTab.MAP) }
    }

    fun loadMapsFromDir(dir: File) {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return
        var count = 0
        files.forEach { file ->
            MapFileIo.loadTileMap(file).onSuccess { map ->
                _state.update { s ->
                    val exists = s.maps.any { it.id == map.id }
                    val updated = if (exists) s.maps.map { if (it.id == map.id) map else it } else s.maps + map
                    s.copy(maps = updated)
                }
                count++
            }
        }
        if (count > 0) setStatus("Loaded $count map(s) from ${dir.name}")
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    fun saveEncounter(encounter: EncounterBundle, file: File) {
        EncounterFileIo.save(encounter, file).fold(
            onSuccess = {
                _state.update { s ->
                    s.copy(
                        isDirty = false,
                        currentFilePath = file.absolutePath,
                        statusMessage = "Saved → ${file.name}",
                        statusIsError = false,
                    )
                }
            },
            onFailure = { e ->
                _state.update { s ->
                    s.copy(statusMessage = "Save failed: ${e.message}", statusIsError = true)
                }
            }
        )
    }

    fun loadEncounterFromFile(file: File) {
        EncounterFileIo.load(file).fold(
            onSuccess = { enc ->
                _state.update { s ->
                    val exists = s.encounters.any { it.id == enc.id }
                    val updated = if (exists) s.encounters.map { if (it.id == enc.id) enc else it }
                    else s.encounters + enc
                    s.copy(
                        encounters = updated,
                        selectedEncounterId = enc.id,
                        activeTab = EditorTab.ENCOUNTER,
                        currentFilePath = file.absolutePath,
                        isDirty = false,
                        statusMessage = "Loaded → ${file.name}",
                        statusIsError = false,
                    )
                }
            },
            onFailure = { e ->
                _state.update { s ->
                    s.copy(statusMessage = "Load failed: ${e.message}", statusIsError = true)
                }
            }
        )
    }

    // ── Campaign lifecycle ────────────────────────────────────────────────────

    fun newCampaign(name: String) {
        val id = "campaign_${System.currentTimeMillis()}"
        val overworldId = "overworld_${System.currentTimeMillis()}"
        val campaign = CampaignBundle(
            id = id,
            name = name,
            overworldSequence = listOf(overworldId),
            overworlds = listOf(OverworldDef(id = overworldId, name = "World 1")),
        )
        CampaignFileIo.saveCampaign(campaign)
        val configUpdate = _state.value.designerConfig.copy(
            recentCampaigns = (listOf(CampaignFileIo.defaultCampaignsRoot.resolve(id).absolutePath) +
                    _state.value.designerConfig.recentCampaigns).distinct().take(10),
            lastOpenedCampaignDir = CampaignFileIo.defaultCampaignsRoot.resolve(id).absolutePath,
        )
        CampaignFileIo.saveConfig(configUpdate)
        _state.update { s ->
            s.copy(
                activeCampaign = campaign,
                campaignEncounters = emptyList(),
                activeOverworldId = overworldId,
                activeTab = EditorTab.CAMPAIGN,
                showCampaignBrowser = false,
                campaignDirty = false,
                campaignLastSavedMs = System.currentTimeMillis(),
                designerConfig = configUpdate,
                graphSelection = GraphSelectionState(kind = GraphSelectionKind.OVERWORLD, overworldId = overworldId),
                statusMessage = "Campaign '$name' created",
            )
        }
    }

    fun openCampaign(dir: File) {
        val s = _state.value
        if (s.campaignDirty && s.activeCampaign != null) saveCampaignNow()
        CampaignFileIo.loadCampaign(dir).fold(
            onSuccess = { campaign ->
                val encounters = CampaignFileIo.loadAllEncountersInCampaign(campaign.id)
                val configUpdate = s.designerConfig.copy(
                    recentCampaigns = (listOf(dir.absolutePath) + s.designerConfig.recentCampaigns).distinct().take(10),
                    lastOpenedCampaignDir = dir.absolutePath,
                )
                CampaignFileIo.saveConfig(configUpdate)
                _state.update { it.copy(
                    activeCampaign = campaign,
                    campaignEncounters = encounters,
                    encounters = encounters,
                    activeOverworldId = campaign.overworldSequence.firstOrNull(),
                    activeTab = EditorTab.CAMPAIGN,
                    showCampaignBrowser = false,
                    campaignDirty = false,
                    campaignLastSavedMs = System.currentTimeMillis(),
                    designerConfig = configUpdate,
                    graphSelection = GraphSelectionState(),
                    statusMessage = "Opened campaign '${campaign.name}'",
                ) }
            },
            onFailure = { e ->
                _state.update { it.copy(statusMessage = "Failed to open campaign: ${e.message}", statusIsError = true) }
            }
        )
    }

    fun saveCampaignNow() {
        val s = _state.value
        val campaign = s.activeCampaign ?: return
        scope.launch(Dispatchers.IO) {
            CampaignFileIo.saveCampaign(campaign).fold(
                onSuccess = {
                    _state.update { it.copy(campaignDirty = false, campaignLastSavedMs = System.currentTimeMillis()) }
                },
                onFailure = { e ->
                    _state.update { it.copy(statusMessage = "Campaign save failed: ${e.message}", statusIsError = true) }
                }
            )
        }
    }

    fun showCampaignBrowser() {
        _state.update { it.copy(showCampaignBrowser = true) }
    }

    fun closeCampaignBrowser() {
        if (_state.value.activeCampaign != null) {
            _state.update { it.copy(showCampaignBrowser = false) }
        }
    }

    fun renameCampaign(name: String, description: String) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(name = name, description = description)
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    // ── Overworld CRUD ────────────────────────────────────────────────────────

    fun newOverworld() {
        val id = "overworld_${System.currentTimeMillis()}"
        val overworld = OverworldDef(id = id, name = "New World")
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(
                overworlds = campaign.overworlds + overworld,
                overworldSequence = campaign.overworldSequence + id,
            )
            s.copy(
                activeCampaign = updated,
                activeOverworldId = id,
                campaignDirty = true,
                graphSelection = GraphSelectionState(kind = GraphSelectionKind.OVERWORLD, overworldId = id),
            )
        }
    }

    fun selectOverworld(id: String) {
        _state.update { it.copy(
            activeOverworldId = id,
            graphSelection = GraphSelectionState(kind = GraphSelectionKind.OVERWORLD, overworldId = id),
        ) }
    }

    fun updateOverworldMeta(id: String, name: String, backgroundMapId: String?) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(
                overworlds = campaign.overworlds.map {
                    if (it.id == id) it.copy(name = name, backgroundMapId = backgroundMapId) else it
                }
            )
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    fun deleteOverworld(id: String) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(
                overworlds = campaign.overworlds.filter { it.id != id },
                overworldSequence = campaign.overworldSequence.filter { it != id },
            )
            val newActiveId = if (s.activeOverworldId == id) updated.overworldSequence.firstOrNull() else s.activeOverworldId
            s.copy(
                activeCampaign = updated,
                activeOverworldId = newActiveId,
                campaignDirty = true,
                graphSelection = GraphSelectionState(),
            )
        }
    }

    fun reorderOverworlds(newSequence: List<String>) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            s.copy(
                activeCampaign = campaign.copy(overworldSequence = newSequence),
                campaignDirty = true,
            )
        }
    }

    // ── Node CRUD ─────────────────────────────────────────────────────────────

    fun placeNode(overworldId: String, type: OverworldNodeType, x: Double, y: Double) {
        val id = "node_${System.currentTimeMillis()}"
        val label = when (type) {
            OverworldNodeType.START -> "Start"
            OverworldNodeType.BATTLE -> "Battle"
            OverworldNodeType.REST -> "Rest"
            OverworldNodeType.BOSS -> "Boss"
        }
        val newNode = OverworldNodeDef(id = id, type = type, label = label, x = x, y = y)
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                val existingNodes = if (type == OverworldNodeType.START) {
                    val startIds = ow.nodes.filter { it.type == OverworldNodeType.START }.map { it.id }.toSet()
                    ow.nodes.filter { it.id !in startIds }
                } else ow.nodes
                val prunedEdges = if (type == OverworldNodeType.START) {
                    val startIds = ow.nodes.filter { it.type == OverworldNodeType.START }.map { it.id }.toSet()
                    ow.edges.filter { it.fromId !in startIds && it.toId !in startIds }
                } else ow.edges
                ow.copy(nodes = existingNodes + newNode, edges = prunedEdges)
            })
            s.copy(
                activeCampaign = updated,
                campaignDirty = true,
                graphSelection = GraphSelectionState(kind = GraphSelectionKind.NODE, overworldId = overworldId, nodeId = id),
            )
        }
    }

    fun moveNode(overworldId: String, nodeId: String, x: Double, y: Double) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                ow.copy(nodes = ow.nodes.map { n -> if (n.id == nodeId) n.copy(x = x, y = y) else n })
            })
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    fun updateNodeLabel(overworldId: String, nodeId: String, label: String) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                ow.copy(nodes = ow.nodes.map { n -> if (n.id == nodeId) n.copy(label = label) else n })
            })
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    fun updateNodeHealPercent(overworldId: String, nodeId: String, v: Float) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                ow.copy(nodes = ow.nodes.map { n -> if (n.id == nodeId) n.copy(healPercent = v) else n })
            })
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    fun assignEncounterToNode(overworldId: String, nodeId: String, encId: String) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                ow.copy(nodes = ow.nodes.map { n -> if (n.id == nodeId) n.copy(encounterId = encId) else n })
            })
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    fun createEncounterForNode(overworldId: String, nodeId: String) {
        val encId = "encounter_${System.currentTimeMillis()}"
        val enc = EncounterBundle(id = encId, name = "New Encounter")
        CampaignFileIo.saveEncounterInCampaign(_state.value.activeCampaign?.id ?: return, enc)
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                ow.copy(nodes = ow.nodes.map { n -> if (n.id == nodeId) n.copy(encounterId = encId) else n })
            })
            s.copy(
                activeCampaign = updated,
                campaignEncounters = s.campaignEncounters + enc,
                encounters = s.encounters + enc,
                selectedEncounterId = encId,
                activeTab = EditorTab.ENCOUNTER,
                campaignDirty = true,
                statusMessage = "Created encounter for node",
            )
        }
    }

    fun editEncounterForNode(encId: String) {
        _state.update { it.copy(selectedEncounterId = encId, activeTab = EditorTab.ENCOUNTER) }
    }

    fun deleteNode(overworldId: String, nodeId: String) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                ow.copy(
                    nodes = ow.nodes.filter { it.id != nodeId },
                    edges = ow.edges.filter { it.fromId != nodeId && it.toId != nodeId },
                )
            })
            val sel = if (s.graphSelection.nodeId == nodeId) GraphSelectionState() else s.graphSelection
            s.copy(activeCampaign = updated, campaignDirty = true, graphSelection = sel)
        }
    }

    fun changeNodeType(overworldId: String, nodeId: String, newType: OverworldNodeType) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                // If promoting to START, demote any existing START node to BATTLE (keep edges)
                val nodes = if (newType == OverworldNodeType.START) {
                    ow.nodes.map { n ->
                        if (n.type == OverworldNodeType.START && n.id != nodeId)
                            n.copy(type = OverworldNodeType.BATTLE)
                        else n
                    }
                } else ow.nodes
                ow.copy(nodes = nodes.map { n -> if (n.id != nodeId) n else n.copy(type = newType) })
            })
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    // ── Edge CRUD ─────────────────────────────────────────────────────────────

    fun addEdge(overworldId: String, fromId: String, toId: String) {
        if (fromId == toId) return
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                val alreadyExists = ow.edges.any { it.fromId == fromId && it.toId == toId }
                if (alreadyExists) return@map ow
                ow.copy(edges = ow.edges + OverworldEdgeDef(fromId, toId))
            })
            s.copy(activeCampaign = updated, campaignDirty = true)
        }
    }

    fun deleteEdge(overworldId: String, fromId: String, toId: String) {
        _state.update { s ->
            val campaign = s.activeCampaign ?: return@update s
            val updated = campaign.copy(overworlds = campaign.overworlds.map { ow ->
                if (ow.id != overworldId) return@map ow
                ow.copy(edges = ow.edges.filter { !(it.fromId == fromId && it.toId == toId) })
            })
            val sel = if (s.graphSelection.edgeFromId == fromId && s.graphSelection.edgeToId == toId)
                GraphSelectionState() else s.graphSelection
            s.copy(activeCampaign = updated, campaignDirty = true, graphSelection = sel)
        }
    }

    // ── Graph interaction ─────────────────────────────────────────────────────

    fun setGraphMode(mode: GraphInteractionMode) {
        _state.update { it.copy(graphMode = mode, edgePendingFromId = null) }
    }

    fun setGraphNodeTypeToPlace(type: OverworldNodeType) {
        _state.update { it.copy(graphNodeTypeToPlace = type) }
    }

    fun selectGraphNode(overworldId: String, nodeId: String) {
        _state.update { it.copy(
            graphSelection = GraphSelectionState(
                kind = GraphSelectionKind.NODE,
                overworldId = overworldId,
                nodeId = nodeId,
            )
        ) }
    }

    fun selectGraphEdge(overworldId: String, fromId: String, toId: String) {
        _state.update { it.copy(
            graphSelection = GraphSelectionState(
                kind = GraphSelectionKind.EDGE,
                overworldId = overworldId,
                edgeFromId = fromId,
                edgeToId = toId,
            )
        ) }
    }

    fun clearGraphSelection() {
        _state.update { it.copy(graphSelection = GraphSelectionState()) }
    }

    fun beginEdgePlacement(overworldId: String, fromNodeId: String) {
        _state.update { it.copy(edgePendingFromId = fromNodeId) }
    }

    fun completeEdgePlacement(overworldId: String, toNodeId: String) {
        val fromId = _state.value.edgePendingFromId ?: return
        addEdge(overworldId, fromId, toNodeId)
        _state.update { it.copy(edgePendingFromId = null) }
    }

    // ── Battle preview ────────────────────────────────────────────────────────

    fun startBattlePreview(encounter: EncounterBundle) {
        val s = _state.value
        val allSkillDefs = (s.skillLibrary + encounter.customSkills)
            .distinctBy { it.id }
            .map { it.toSkillTemplate() }
            .let { it + allSkills }
            .distinctBy { it.id }

        val tileMap = encounter.mapId?.let { id -> s.maps.find { it.id == id } }

        val world = World()
        val skillRegistry = SkillRegistry(allSkillDefs)
        val battleLog = BattleLog()
        val animCollector = AnimationEventCollector(world, skillRegistry)
        val sysReg = buildBattleSystemRegistry(world, skillRegistry, battleLog, tileMap)
        val gameLoop = GameLoop(sysReg)
        val tileCache = if (tileMap != null) {
            BattleTileCache(MapConfig(
                id         = tileMap.id,
                levelCount = 1,
                tileWidth  = tileMap.tileWidthPx,
                tileHeight = tileMap.tileHeightPx,
                colCount   = tileMap.cols,
                rowCount   = tileMap.rows,
            ))
        } else {
            BattleTileCache(throneRoomConfig)
        }

        _previewBattleVm = BattleViewModel(
            world              = world,
            gameLoop           = gameLoop,
            skillRegistry      = skillRegistry,
            battleLog          = battleLog,
            animCollector      = animCollector,
            tileCache          = tileCache,
            initialTileMap     = tileMap,
            levelUpPreview     = { de.jackbeback.pocketquest.game.run.LevelUpResult(false, 1) },
            pickRelicCandidates = { emptyList() },
            resetAndSpawn      = { _ ->
                world.allEntities().toList().forEach { world.destroyEntity(it) }
                world.flushDestroys()
                val playerId = wizardTemplate.spawnIntoWorld(world)
                val maxCol = (tileMap?.cols ?: BATTLE_COLS) - 1
                val maxRow = (tileMap?.rows ?: BATTLE_ROWS) - 1
                world.set(playerId, PositionComponent(
                    col = encounter.playerSpawnCol.coerceIn(0, maxCol),
                    row = encounter.playerSpawnRow.coerceIn(0, maxRow),
                ))
                encounter.enemies.forEach { enemyDef ->
                    val template = enemyDef.toUnitTemplate()
                    template.spawnIntoWorld(world)
                }
                battleLog.clear()
            },
        )

        _state.update { it.copy(showBattlePreview = true, statusMessage = "Preview: ${encounter.name}") }
        _previewBattleVm?.prepareBattle(BattleParams(eventId = "preview", enemies = emptyList()))
    }

    fun closeBattlePreview() {
        _previewBattleVm = null
        _state.update { it.copy(showBattlePreview = false, statusMessage = "Preview closed") }
    }

    fun setActiveTab(tab: EditorTab) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun setStatus(message: String, isError: Boolean = false) {
        _state.update { it.copy(statusMessage = message, statusIsError = isError) }
    }
}

private fun findResourcesDir(): File? {
    var dir = File(System.getProperty("user.dir"))
    repeat(4) {
        val candidate = File(dir, "composeApp/src/commonMain/composeResources")
        if (candidate.exists()) return candidate
        dir = dir.parentFile ?: return null
    }
    return null
}
