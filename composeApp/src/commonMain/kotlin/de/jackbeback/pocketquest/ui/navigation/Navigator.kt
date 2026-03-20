package de.jackbeback.pocketquest.ui.navigation

import de.jackbeback.pocketquest.content.dsl.UnitTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Parameters describing the battle that is about to start. */
data class BattleParams(
    /** The overworld event that triggered this battle. Used to complete it on victory. */
    val eventId: String,
    /** Enemy templates to spawn in the battle arena. */
    val enemies: List<UnitTemplate>,
    /** Optional map id to load terrain data from. Null means no terrain effects. */
    val mapId: String? = null,
)

sealed class Screen {
    object CharacterSelect : Screen()
    object Overworld : Screen()
    object Battle : Screen()
    object Character : Screen()
}

class Navigator {
    private val _screen = MutableStateFlow<Screen>(Screen.CharacterSelect)
    val screen: StateFlow<Screen> = _screen

    /** Set while the Battle screen is active; null otherwise. */
    var currentBattle: BattleParams? = null
        private set

    fun goToCharacterSelect() {
        currentBattle = null
        _screen.value = Screen.CharacterSelect
    }

    fun goToOverworld() {
        _screen.value = Screen.Overworld
    }

    fun goToBattle(params: BattleParams) {
        currentBattle = params
        _screen.value = Screen.Battle
    }

    fun returnToOverworld() {
        currentBattle = null
        _screen.value = Screen.Overworld
    }

    fun goToCharacter() {
        _screen.value = Screen.Character
    }
}
