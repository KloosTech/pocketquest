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
)

sealed class Screen {
    object Overworld : Screen()
    object Battle : Screen()
}

class Navigator {
    private val _screen = MutableStateFlow<Screen>(Screen.Overworld)
    val screen: StateFlow<Screen> = _screen

    /** Set while the Battle screen is active; null on the Overworld. */
    var currentBattle: BattleParams? = null
        private set

    fun goToBattle(params: BattleParams) {
        currentBattle = params
        _screen.value = Screen.Battle
    }

    fun returnToOverworld() {
        currentBattle = null
        _screen.value = Screen.Overworld
    }
}
