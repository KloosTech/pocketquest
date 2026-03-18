package de.jackbeback.pocketquest.game.run

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** State that resets when the player dies. */
data class RunScopedState(
    val characterTemplateId: String,
    val level: Int = 1,
    val exp: Int = 0,
    /** Increments once per won encounter; used to scale enemy difficulty. */
    val difficultyCounter: Int = 0,
    /** Saved player HP between encounters; null = use template default (full HP). */
    val playerHp: Int? = null,
    /** Saved player mana between encounters; null = use template default. */
    val playerMana: Int? = null,
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
}
