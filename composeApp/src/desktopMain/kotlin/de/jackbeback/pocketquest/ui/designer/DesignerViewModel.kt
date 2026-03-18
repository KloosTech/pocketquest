package de.jackbeback.pocketquest.ui.designer

import de.jackbeback.pocketquest.content.definitions.allSkills
import de.jackbeback.pocketquest.content.definitions.gruntTemplate
import de.jackbeback.pocketquest.content.definitions.veteranGruntTemplate
import de.jackbeback.pocketquest.content.definitions.chieftainTemplate
import de.jackbeback.pocketquest.content.definitions.wizardTemplate
import de.jackbeback.pocketquest.content.dsl.spawnIntoWorld
import de.jackbeback.pocketquest.content.map.throneRoomConfig
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.designer.io.EncounterFileIo
import de.jackbeback.pocketquest.designer.model.EffectDto
import de.jackbeback.pocketquest.designer.model.EncounterBundle
import de.jackbeback.pocketquest.designer.model.EnemyDefinition
import de.jackbeback.pocketquest.designer.model.SkillDefinition
import de.jackbeback.pocketquest.designer.model.builtInSkillDefinitions
import de.jackbeback.pocketquest.designer.model.toEnemyDefinition
import de.jackbeback.pocketquest.designer.model.toSkillDefinition
import de.jackbeback.pocketquest.designer.model.toUnitTemplate
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.game.animation.AnimationEventCollector
import de.jackbeback.pocketquest.game.battle.BattleTileCache
import de.jackbeback.pocketquest.game.battle.buildBattleSystemRegistry
import de.jackbeback.pocketquest.game.loop.GameLoop
import de.jackbeback.pocketquest.game.snapshot.BattleLog
import de.jackbeback.pocketquest.ui.battle.BattleViewModel
import de.jackbeback.pocketquest.ui.navigation.BattleParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

enum class EditorTab { ENCOUNTER, ENEMY, SKILL }
enum class SelectionType { ENCOUNTER, ENEMY, SKILL, NONE }

data class DesignerState(
    val encounters: List<EncounterBundle> = emptyList(),
    /** Enemy archetypes in the designer library (not yet in any encounter). */
    val enemyLibrary: List<EnemyDefinition> = emptyList(),
    /** Skill definitions in the designer library. */
    val skillLibrary: List<SkillDefinition> = emptyList(),
    val activeTab: EditorTab = EditorTab.ENCOUNTER,
    val selectedEncounterId: String? = null,
    val selectedEnemyId: String? = null,
    val selectedSkillId: String? = null,
    val isDirty: Boolean = false,
    val currentFilePath: String? = null,
    val showBattlePreview: Boolean = false,
    val statusMessage: String = "Ready — create or load an encounter to start",
    val statusIsError: Boolean = false,
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

    // ── Preview battle setup (created fresh per preview) ─────────────────────
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
            s.copy(
                encounters = s.encounters.map { if (it.id == updated.id) updated else it },
                isDirty = true,
            )
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

    /** Add a library enemy to the currently selected encounter. */
    fun addEnemyToEncounter(enemyDef: EnemyDefinition, encounterId: String) {
        _state.update { s ->
            val enc = s.encounters.find { it.id == encounterId } ?: return@update s
            // Give a unique instance id so multiple of the same enemy can coexist
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

    /** Update an enemy instance that lives inside an encounter. */
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

    // ── Battle preview ────────────────────────────────────────────────────────

    fun startBattlePreview(encounter: EncounterBundle) {
        val s = _state.value
        // Merge library skills + encounter custom skills
        val allSkillDefs = (s.skillLibrary + encounter.customSkills)
            .distinctBy { it.id }
            .map { it.toSkillTemplate() }
            .let { it + allSkills } // also include built-in skills
            .distinctBy { it.id }

        val world = World()
        val skillRegistry = SkillRegistry(allSkillDefs)
        val battleLog = BattleLog()
        val animCollector = AnimationEventCollector(world, skillRegistry)
        val sysReg = buildBattleSystemRegistry(world, skillRegistry, battleLog)
        val gameLoop = GameLoop(sysReg)
        val tileCache = BattleTileCache(throneRoomConfig)

        _previewBattleVm = BattleViewModel(
            world              = world,
            gameLoop           = gameLoop,
            skillRegistry      = skillRegistry,
            battleLog          = battleLog,
            animCollector      = animCollector,
            tileCache          = tileCache,
            levelUpPreview     = { de.jackbeback.pocketquest.game.run.LevelUpResult(false, 1) },
            pickRelicCandidates = { emptyList() },
            resetAndSpawn      = { _ ->
                world.allEntities().toList().forEach { world.destroyEntity(it) }
                world.flushDestroys()
                // Spawn wizard (preview uses default spawn; encounters define enemy positions)
                wizardTemplate.spawnIntoWorld(world)
                // Spawn enemies
                encounter.enemies.forEach { enemyDef ->
                    val template = enemyDef.toUnitTemplate()
                    template.spawnIntoWorld(world)
                }
                battleLog.clear()
            },
        )

        _state.update { it.copy(showBattlePreview = true, statusMessage = "Preview: ${encounter.name}") }

        // Kick off the battle immediately
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
