package io.github.jackbeback.vm

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NavigationViewModel: ViewModel() {
    private val _currentScreen = MutableStateFlow(GameScreen.Map)
    val currentScreen = _currentScreen.asStateFlow()

    companion object{
        val instance = NavigationViewModel()
    }

    fun navigateTo(screen: GameScreen){
        _currentScreen.update { screen }
    }
}


enum class GameScreen {
    Map,
    Unit,
    Skill,
    Settings
}
