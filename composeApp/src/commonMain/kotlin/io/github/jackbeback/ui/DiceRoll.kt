package io.github.jackbeback.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.*
import pocketquest.composeapp.generated.resources.*
import kotlin.random.*

enum class DiceState {
    IDLE,
    ROLLING,
    SUCCESS,
    FAILED
}

@Composable
fun DiceRoll(
    modifier: Modifier = Modifier,
    diceSize: Int = 100,
    rollDuration: Long = 1000, // Anpassbare Dauer der Würfelanimation in Millisekunden
    frameDuration: Long = 50L,
    isSuccessful: Boolean? = null, // Neuer Parameter für das Ergebnis
    onRollFinished: (success: Boolean) -> Unit = {},
) {
    val density = LocalDensity.current.density

    val rollingFrames = listOf(
        Res.drawable.roll_1,
        Res.drawable.roll_2,
        Res.drawable.roll_3,
    )

    val failedFrames = listOf(
        Res.drawable.roll_4,
        Res.drawable.roll_failed
    )

    val successFrames = listOf(
        Res.drawable.roll_4,
        Res.drawable.roll_success_1,
        Res.drawable.roll_success_3
    )

    var diceState by remember { mutableStateOf(DiceState.IDLE) }
    var currentRollingFrameIndex by remember { mutableStateOf(0) }
    var currentSuccessFrameIndex by remember { mutableStateOf(0) }
    var currentFailedFrameIndex by remember { mutableStateOf(0) }
    val rollResult = remember { mutableStateOf<Boolean?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Funktion zum Auslösen des Würfelwurfs
    fun rollDice() {
        // Nur würfeln, wenn isSuccessful nicht null ist
        if (isSuccessful == null) return

        coroutineScope.launch {
            // Würfelanimation starten
            diceState = DiceState.ROLLING
            rollResult.value = isSuccessful

            // Animation während des Rollens
             // Zeit pro Frame während des Rollens
            val frameCount = (rollDuration / frameDuration).toInt()

            repeat(frameCount) {
                currentRollingFrameIndex = Random.nextInt(rollingFrames.size)
                delay(frameDuration)
            }

            // Ergebnis anzeigen
            diceState = if (rollResult.value == true) DiceState.SUCCESS else DiceState.FAILED

            // Bei Erfolg: Erfolgsanimation abspielen
            if (rollResult.value == true) {
                repeat(successFrames.size) {
                    currentSuccessFrameIndex = it
                    delay(frameDuration)
                }
            }

            // On Loss
            if (rollResult.value == false) {
                repeat(failedFrames.size) {
                    currentFailedFrameIndex = it
                    delay(frameDuration)
                }
            }

            // Callback auslösen
            onRollFinished(rollResult.value ?: false)
        }
    }

    // Starte die Animation wenn isSuccessful gesetzt wird
    LaunchedEffect(isSuccessful) {
        if (isSuccessful != null) {
            rollDice()
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val resourceToDraw = when (diceState) {
            DiceState.IDLE -> rollingFrames.first()
            DiceState.ROLLING -> rollingFrames[currentRollingFrameIndex]
            DiceState.SUCCESS -> successFrames[currentSuccessFrameIndex]
            DiceState.FAILED -> failedFrames[currentFailedFrameIndex]
        }

        DiceImage(
            drawableResource = resourceToDraw,
            modifier = Modifier.size(diceSize.dp/density),
            onClick = {
                if (diceState != DiceState.ROLLING && isSuccessful != null) {
                    rollDice()
                }
            }
        )
    }
}

@Composable
private fun DiceImage(
    drawableResource: DrawableResource,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    onClick: () -> Unit = {}
) {
    val painter = painterResource(drawableResource)

    Image(
        painter = painter,
        contentDescription = "Dice",
        modifier = modifier,
        colorFilter = colorFilter
    )
}
