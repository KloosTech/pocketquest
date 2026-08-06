package io.github.jackbeback.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import io.github.jackbeback.data.*
import io.github.jackbeback.vm.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.*
import pocketquest.composeapp.generated.resources.*
import pocketquest.composeapp.generated.resources.Res
import kotlin.uuid.*

@OptIn(ExperimentalUuidApi::class)
@Composable
fun Wizard(unit: UnitEntity){
    val unitVm = remember { UnitViewModel.instance }

    val selectedPlayer by unitVm.selectedPlayer.collectAsState()

    val mapVm = remember { MapViewModel.instance }
    val mapScale by mapVm.mapScale.collectAsState()

    val self = unitVm.units.collectAsState().value.firstOrNull() { it.id == unit.id } ?: return

    val density = LocalDensity.current.density
    val size = 100.dp/density

    LaunchedEffect(self){
        if (self.roll.wins.isNotEmpty()){
            delay(2000)
            unitVm.updateUnit(self.setRoll(Roll()))
        }
    }

    BoxWithConstraints(Modifier.size(size).offset(y = size/2).scale(mapScale).clickable {
        if (selectedPlayer == null) {unitVm.select(self.id)} else unitVm.deselectPlayer()
    }, contentAlignment = Alignment.Center){
        if (selectedPlayer != null) {
            Image(
                painter = painterResource(Res.drawable.wizard),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                colorFilter = ColorFilter.tint(
                    Color.Red,
                    blendMode = BlendMode.SrcAtop
                )
            )
        }

        Image(painter = painterResource(Res.drawable.wizard), "", modifier = Modifier.scale(0.9f))

        if (selectedPlayer != null) {
            // HealthBar anzeigen
            HealthBar(self, Modifier.align(Alignment.TopCenter))

            // Aktionskreise anzeigen
            if (self is Player) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(self.resources.action.current) {
                        ActionCircle(Modifier.offset ( y = 0.dp ))
                    }
                }
            }
            if (self.roll.wins.isNotEmpty()){
                Row {
                    self.roll.wins.forEachIndexed { index, win ->
                        DiceRoll(modifier = Modifier, diceSize = 50, isSuccessful = win)
                    }
                }
            }
        }
    }
}

