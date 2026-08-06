package io.github.jackbeback.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.jackbeback.data.UnitEntity
import io.github.jackbeback.util.json
import io.github.jackbeback.util.load
import io.github.jackbeback.util.save
import io.github.jackbeback.vm.GameScreen
import io.github.jackbeback.vm.NavigationViewModel
import io.github.jackbeback.vm.UnitViewModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun Settings(modifier: Modifier) {
    val unitVM = UnitViewModel.instance
    val nav = NavigationViewModel.instance

    val units by unitVM.units.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2E2E2E),
                Color(0xFF1A1A1A)
            )
        )
    )) {
        BattleLog()
        Row(modifier = Modifier.align(Alignment.BottomCenter)) {
            Button(onClick = {nav.navigateTo(GameScreen.Map)}){
                Text("Back")
            }
            Button(onClick = {
                unitVM.save()
            }){
                Text("Save")
            }
            Button(onClick = {
                unitVM.load()
            }){
                Text("Load")
            }
            Button(onClick = {
                unitVM.initEnemies()
            }){
                Text("Spawn Enemies")
            }
        }
    }
}
