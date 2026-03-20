package de.jackbeback.pocketquest.game.run

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** State that resets when the player dies. */
data class RunScopedState(
    val characterTemplateId: String,
    val level: Int = 1,
    val exp: Int = 0,
    val difficultyCounter: Int = 0,
    val playerHp: Int? = null,
    val playerMana: Int? = null,
    val relics: List<String> = emptyList(),
    val areaNumber: Int = 1,
    val unspentAttributePoints: Int = 0,
    val attributeBonuses: Map<String, Int> = emptyMap(),
    /** Graph node the player is currently standing on. */
    val currentNodeId: String = "start",
    /** Nodes whose events have been fully resolved (battle won, rest taken/skipped). */
    val completedNodeIds: Set<String> = setOf("start"),
)

data class LevelUpResult(
    val didLevelUp: Boolean,
    val newLevel: Int,
)

data class PersistentState(
    val unlockedCharacterIds: List<String> = listOf("wizard"),
    val inventory: List<String> = emptyList(),
    val globalSkillUnlocks: List<String> = emptyList(),
)

class RunStateHolder {
    private val _run = MutableStateFlow<RunScopedState?>(null)
    val run: StateFlow<RunScopedState?> = _run

    val persistent: PersistentState = PersistentState()

    fun startRun(characterTemplateId: String) {
        _run.value = RunScopedState(characterTemplateId = characterTemplateId)
    }

    fun resetRun() {
        _run.value = null
    }

    fun incrementDifficulty() {
        _run.update { it?.copy(difficultyCounter = (it.difficultyCounter + 1)) }
    }

    fun savePlayerState(hp: Int, mana: Int) {
        _run.update { it?.copy(playerHp = hp, playerMana = mana) }
    }

    fun addRelic(relicId: String) {
        _run.update { it?.copy(relics = it.relics + relicId) }
    }

    /** Move the player to a new graph node (call before triggering the node's event). */
    fun moveToNode(nodeId: String) {
        _run.update { it?.copy(currentNodeId = nodeId) }
    }

    /** Mark a graph node as completed, unlocking its outgoing edges. */
    fun completeNode(nodeId: String) {
        _run.update { it?.copy(completedNodeIds = it.completedNodeIds + nodeId) }
    }

    /** Called when the player clears all events and moves to the next area. */
    fun startNextArea() {
        _run.update {
            it?.copy(
                areaNumber       = it.areaNumber + 1,
                currentNodeId    = "start",
                completedNodeIds = setOf("start"),
            )
        }
    }

    fun spendAttributePoint(attr: String) {
        _run.update { state ->
            if (state == null || state.unspentAttributePoints <= 0) return@update state
            val updated = state.attributeBonuses.toMutableMap()
            updated[attr] = (updated[attr] ?: 0) + 1
            state.copy(
                unspentAttributePoints = state.unspentAttributePoints - 1,
                attributeBonuses = updated,
            )
        }
    }

    fun previewGainExp(amount: Int): LevelUpResult {
        val state = _run.value ?: return LevelUpResult(didLevelUp = false, newLevel = 1)
        val newExp  = state.exp + amount
        val leveled = newExp >= state.level * 100
        return LevelUpResult(didLevelUp = leveled, newLevel = if (leveled) state.level + 1 else state.level)
    }

    fun gainExp(amount: Int): LevelUpResult {
        val state = _run.value ?: return LevelUpResult(didLevelUp = false, newLevel = 1)
        var newExp   = state.exp + amount
        var newLevel = state.level
        val threshold = newLevel * 100
        val leveled = newExp >= threshold
        if (leveled) {
            newExp -= threshold
            newLevel++
        }
        _run.value = state.copy(
            exp   = newExp,
            level = newLevel,
            unspentAttributePoints = state.unspentAttributePoints + if (leveled) 2 else 0,
        )
        return LevelUpResult(didLevelUp = leveled, newLevel = newLevel)
    }
}
