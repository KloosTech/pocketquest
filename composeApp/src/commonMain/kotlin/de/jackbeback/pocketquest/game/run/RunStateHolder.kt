package de.jackbeback.pocketquest.game.run

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** State that resets when the player dies. */
data class RunScopedState(
    val characterTemplateId: String,
    val level: Int = 1,
    /** XP within the current level. Resets to 0 on each level-up. */
    val exp: Int = 0,
    /** Increments once per won encounter; used to scale enemy difficulty. */
    val difficultyCounter: Int = 0,
    /** Saved player HP between encounters; null = use template default (full HP). */
    val playerHp: Int? = null,
    /** Saved player mana between encounters; null = use template default. */
    val playerMana: Int? = null,
    /** IDs of relics held this run. Applied at the start of every encounter. */
    val relics: List<String> = emptyList(),
    /** How many areas (map resets) the player has completed this run. Starts at 1. */
    val areaNumber: Int = 1,
)

/** Result of a [RunStateHolder.gainExp] call. */
data class LevelUpResult(
    val didLevelUp: Boolean,
    val newLevel: Int,
)

/** State that persists across runs (survives death). */
data class PersistentState(
    val unlockedCharacterIds: List<String> = listOf("wizard"),
    val inventory: List<String> = emptyList(),
    val globalSkillUnlocks: List<String> = emptyList(),
)

/**
 * Single source of truth for roguelike progression.
 *
 * [run] is null between runs (on CharacterSelect screen) and non-null during an active run.
 * [persistent] survives across all runs — inventory and unlocked characters live here.
 */
class RunStateHolder {
    private val _run = MutableStateFlow<RunScopedState?>(null)
    val run: StateFlow<RunScopedState?> = _run

    val persistent: PersistentState = PersistentState()

    fun startRun(characterTemplateId: String) {
        _run.value = RunScopedState(characterTemplateId = characterTemplateId)
    }

    /** Called on player death — wipes all run-scoped progress. */
    fun resetRun() {
        _run.value = null
    }

    /** Called after each won encounter. */
    fun incrementDifficulty() {
        _run.update { it?.copy(difficultyCounter = (it.difficultyCounter + 1)) }
    }

    /** Saves current player HP/Mana to be restored at the start of the next encounter. */
    fun savePlayerState(hp: Int, mana: Int) {
        _run.update { it?.copy(playerHp = hp, playerMana = mana) }
    }

    /** Adds a relic (by id) to the current run's collection. */
    fun addRelic(relicId: String) {
        _run.update { it?.copy(relics = it.relics + relicId) }
    }

    /** Called when the player clears all events on the current map and moves to the next area. */
    fun startNextArea() {
        _run.update { it?.copy(areaNumber = it.areaNumber + 1) }
    }

    /**
     * Pure preview of what [gainExp] would return without modifying state.
     * Use this to show level-up info in UI before committing the XP.
     */
    fun previewGainExp(amount: Int): LevelUpResult {
        val state = _run.value ?: return LevelUpResult(didLevelUp = false, newLevel = 1)
        val newExp    = state.exp + amount
        val leveled   = newExp >= state.level * 100
        return LevelUpResult(didLevelUp = leveled, newLevel = if (leveled) state.level + 1 else state.level)
    }

    /**
     * Awards [amount] XP to the current run.
     *
     * Level-up threshold: level N requires N×100 XP (level 1→2: 100 XP, 2→3: 200 XP, etc.).
     * At most one level-up per call.
     *
     * @return [LevelUpResult] indicating whether a level-up occurred and the new level.
     */
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
        _run.value = state.copy(exp = newExp, level = newLevel)
        return LevelUpResult(didLevelUp = leveled, newLevel = newLevel)
    }
}
