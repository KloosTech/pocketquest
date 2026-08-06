package io.github.jackbeback.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.jackbeback.vm.GameScreen
import io.github.jackbeback.vm.MapViewModel
import io.github.jackbeback.vm.NavigationViewModel

@Composable
fun GameScreen() {
    val currentScreen by NavigationViewModel.instance.currentScreen.collectAsState()
    val mapState = remember { MapViewModel.instance.throneroom}

    val modifier = Modifier.fillMaxSize()
    when(currentScreen){
        GameScreen.Map -> {
            MapContainer(modifier = modifier, mapState)
        }
        GameScreen.Unit -> {
           Settings(modifier = modifier)
        }
        GameScreen.Skill -> {
            TODO()
        }
        GameScreen.Settings -> {
           Settings(modifier = modifier)
        }
    }
}
