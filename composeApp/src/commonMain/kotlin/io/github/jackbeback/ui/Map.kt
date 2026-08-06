@file:OptIn(ExperimentalUuidApi::class, ExperimentalUuidApi::class)

package io.github.jackbeback.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import io.github.jackbeback.data.*
import io.github.jackbeback.util.*
import io.github.jackbeback.vm.*
import kotlinx.coroutines.*
import kotlinx.io.*
import org.jetbrains.compose.resources.*
import ovh.plrapps.mapcompose.api.*
import ovh.plrapps.mapcompose.core.TileStreamProvider
import ovh.plrapps.mapcompose.ui.*
import ovh.plrapps.mapcompose.ui.state.*
import pocketquest.composeapp.generated.resources.Res
import kotlin.uuid.*

@OptIn(ExperimentalResourceApi::class)
fun makeStaticTileStreamProvider(path: String) =
    TileStreamProvider { row, col, zoomLvl ->
        try {
            val buffer = Buffer()
            Res.readBytes("$path/$col-$row.png").let {
                buffer.write(it)
                buffer
            }
        } catch (e: Exception) {
            null
        }
    }

@OptIn(ExperimentalResourceApi::class)
fun makeOverlayTileStreamProvider(vm: OverlayViewModel) =
    TileStreamProvider { row, col, zoomLvl ->
        try {
            vm.getOverlayFor(row, col, zoomLvl)
        } catch (e: Exception) {
            null
        }
    }


@Composable
fun MapContainer(
    modifier: Modifier = Modifier,
    mapState: MapState
) {
    val unitVm = remember { UnitViewModel.instance }
    val stateVm = remember { StateViewModel.instance }
    val mapVm = remember { MapViewModel.instance }
    val nav = remember { NavigationViewModel.instance }
    val skillVm = remember { SkillViewModel.instance }
    val overlayVm = remember { OverlayViewModel.instance }

    var overlayId by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val units by unitVm.units.collectAsState()

    val gameState by stateVm.currentState.collectAsState()

    val mapScale by mapVm.mapScale.collectAsState()

    val selected by unitVm.selectedUnit.collectAsState()
    val selectedPlayer by unitVm.selectedPlayer.collectAsState()
    val selectedEnemy by unitVm.selectedEnemy.collectAsState()
    val currentSkill by skillVm.skillQueue.collectAsState()
    val overlayChanged by overlayVm.overlayVersion.collectAsState()

    LaunchedEffect(overlayChanged) {
        mapState.removeLayer(overlayId)
        overlayId = mapState.addLayer(makeOverlayTileStreamProvider(overlayVm))
    }

    LaunchedEffect(gameState) {
        when (gameState) {
            GameState.Initializing -> {
                println("Initializing")
                delay(1000)
                stateVm.advanceGameState()
            }

            GameState.PlayerTurnStart -> {
                println("Player Turn Start")
                delay(1000)
                unitVm.save()
                stateVm.advanceGameState()
            }

            GameState.PlayersTurn -> {
                units.firstOrNull { it.type == UnitType.PLAYER }?.let { player ->
                    mapState.scrollTo(player)
                }
            }

            GameState.PlayersTurnEnd -> {
                println("Player Turn End")
                delay(1000)
                units.filter { it.type == UnitType.PLAYER }.forEach { unit ->
                    unitVm.updateUnit((unit as Player).resetActions())
                }
                stateVm.advanceGameState()
            }

            GameState.EnemiesTurnStart -> {
                println("Enemies Turn Start")
                unitVm.enemyConditionTick()
                delay(1000)
                stateVm.advanceGameState()
            }

            GameState.EnemiesTurn -> {
                println("Enemies Turn")
                delay(1000)
                stateVm.advanceGameState()
            }

            GameState.EnemiesTurnEnd -> {
                println("Enemies Turn End")
                delay(1000)
                stateVm.advanceGameState()
            }

            GameState.EnvironmentTurn -> {
                println("Environment Turn")
                delay(1000)
                stateVm.advanceGameState()
            }
        }
    }

    LaunchedEffect(units, selectedPlayer) {
        //Handle Clicks on Map
        mapState.onTap { x, y ->
            selectedPlayer?.let { unit ->
                unitVm.updateUnit(unit.moveTo(Position(x, y)))
            }
        }
    }

    LaunchedEffect(currentSkill) {
        currentSkill.firstOrNull()?.let {
            mapState.animateAttack(it, onComplete = {
                mapState.removeMarker(it.skill.name)

                //on Hit apply action on Target
                selectedEnemy?.let { p1 -> unitVm.updateUnit(it.skill.action.onTarget(p1)) }

                skillVm.removeSkill()
            })
        }
    }

    LaunchedEffect(units) {
        units.forEach { unit ->
            mapState.animateUnit(unit) {

            }
        }
    }

    LaunchedEffect(mapState.scale) {
        mapVm.updateMapScale(mapState.scale)
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            // MapUI mit dem aktuellen Zustand
            MapUI(modifier, state = mapState)

            // Debug-Info
            Text(
                """Pos = ${mapState.centroidX.toFixed(2)}, ${mapState.centroidY.toFixed(2)}
                    |Scale = ${mapScale.toFixed(2)}
                    |${selected?.name ?: "No unit selected"}
                    |${selectedPlayer?.name ?: "No Source selected"} ${selectedPlayer?.resources?.health?.current}
                    |${selectedEnemy?.name ?: "No Enemy selected"}
                    |${gameState}
                    |${currentSkill.firstOrNull()?.skill?.name ?: "No Skill selected"}
                """.trimMargin(),
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .clickable { stateVm.advanceGameState() }
            )

            SkillSelection(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                active = selectedPlayer != null
            )

            Icon(
                Icons.Default.Settings,
                "Settings",
                Modifier.align(Alignment.TopEnd).padding(16.dp).size(50.dp).clickable {
                    nav.navigateTo(GameScreen.Settings)
                }, tint = Color.White
            )

            if (units.firstOrNull {
                    it is Enemy && it.resources.health.current > 0
                } == null) {
                Text(
                    "You Won",
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).align(Alignment.Center).clip(
                        RoundedCornerShape(25.dp)
                    ).clickable {},
                    color = Color.White,
                    fontSize = 32.sp
                )
            }
        }
    }
}





