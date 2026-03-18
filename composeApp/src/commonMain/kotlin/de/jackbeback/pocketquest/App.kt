package de.jackbeback.pocketquest

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.jackbeback.pocketquest.content.definitions.wizardTemplate
import de.jackbeback.pocketquest.content.events.allOverworldEvents
import de.jackbeback.pocketquest.game.overworld.OverworldEventRegistry
import de.jackbeback.pocketquest.game.run.RunStateHolder
import de.jackbeback.pocketquest.ui.battle.BattleScreen
import de.jackbeback.pocketquest.ui.battle.BattleViewModel
import de.jackbeback.pocketquest.ui.characterselect.CharacterSelectScreen
import de.jackbeback.pocketquest.ui.navigation.Navigator
import de.jackbeback.pocketquest.ui.navigation.Screen
import de.jackbeback.pocketquest.ui.overworld.OverworldScreen
import de.jackbeback.pocketquest.ui.overworld.OverworldViewModel
import org.koin.compose.koinInject

@Composable
fun App() {
    MaterialTheme {
        val navigator = koinInject<Navigator>()
        val screen by navigator.screen.collectAsState()

        when (screen) {
            Screen.CharacterSelect -> {
                val runStateHolder  = koinInject<RunStateHolder>()
                val eventRegistry   = koinInject<OverworldEventRegistry>()
                val overworldVm     = koinInject<OverworldViewModel>()
                CharacterSelectScreen(
                    availableCharacters = listOf(wizardTemplate),
                    runStateHolder = runStateHolder,
                    onRunStarted = {
                        // Reset events and re-add their markers for the new run
                        eventRegistry.reset(allOverworldEvents)
                        overworldVm.onRunReset(allOverworldEvents)
                        navigator.goToOverworld()
                    },
                )
            }
            Screen.Overworld -> {
                val vm = koinInject<OverworldViewModel>()
                OverworldScreen(vm)
            }
            Screen.Battle -> {
                val battleVm    = koinInject<BattleViewModel>()
                val overworldVm = koinInject<OverworldViewModel>()
                val runStateHolder = koinInject<RunStateHolder>()
                val params = navigator.currentBattle
                LaunchedEffect(Unit) { battleVm.prepareBattle(params) }
                BattleScreen(
                    viewModel = battleVm,
                    onBattleEnd = { victory ->
                        if (victory) {
                            runStateHolder.incrementDifficulty()
                            overworldVm.onBattleCompleted()
                            navigator.returnToOverworld()
                        } else {
                            runStateHolder.resetRun()
                            navigator.goToCharacterSelect()
                        }
                    },
                )
            }
        }
    }
}
