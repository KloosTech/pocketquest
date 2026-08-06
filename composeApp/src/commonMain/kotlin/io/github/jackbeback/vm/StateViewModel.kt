package io.github.jackbeback.vm

import androidx.lifecycle.*
import kotlinx.coroutines.flow.*

class StateViewModel : ViewModel() {
    companion object {
        val instance = StateViewModel()
    }

    private val _currentState = MutableStateFlow<GameState>(GameState.Initializing)
    val currentState: StateFlow<GameState> = _currentState.asStateFlow()

    fun updateGameState(state: GameState){
        _currentState.update { state }
    }

    fun advanceGameState(){
        _currentState.update {
            when(_currentState.value){
                GameState.Initializing -> GameState.PlayerTurnStart
                GameState.PlayerTurnStart -> GameState.PlayersTurn
                GameState.PlayersTurn -> GameState.PlayersTurnEnd
                GameState.PlayersTurnEnd -> GameState.EnemiesTurnStart
                GameState.EnemiesTurnStart -> GameState.EnemiesTurn
                GameState.EnemiesTurn -> GameState.EnemiesTurnEnd
                GameState.EnemiesTurnEnd -> GameState.EnvironmentTurn
                GameState.EnvironmentTurn -> GameState.PlayerTurnStart
            }
        }
    }


}

enum class GameState {
    Initializing,
    PlayerTurnStart,
    PlayersTurn,
    PlayersTurnEnd,
    EnemiesTurnStart,
    EnemiesTurn,
    EnemiesTurnEnd,
    EnvironmentTurn
}
