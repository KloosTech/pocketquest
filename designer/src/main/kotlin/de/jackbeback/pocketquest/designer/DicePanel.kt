package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.ui.DiceRoll
import de.jackbeback.pocketquest.ui.VisualWorld
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkStepper
import kotlin.random.Random

/**
 * A visual-tuning bench for dice-roll animations, separate from any real encounter — no catalog,
 * no combat state, just [DiceRoll] wired to a Roll button so a new tumble/settle/shading tweak can
 * be eyeballed in a tight loop instead of needing a real playtest fight to trigger an actual roll.
 * Home for every current and future die animation as they're added (currently: just the d20).
 */
@Composable
fun DicePanel(modifier: Modifier = Modifier) {
    // DiceRoll only reads VisualWorld.scaled() (i.e. .speed) — the GameState behind it is otherwise
    // unused set-dressing, so the cheapest legal one will do.
    val world = remember {
        VisualWorld(
            initial = GameState(
                entities = emptyList(),
                map = BattleMap(width = 1, height = 1),
                turn = TurnState(round = 1, order = emptyList(), activeIndex = 0, phase = TurnPhase.Main),
                rng = RngState(seed = 0L),
            ),
            tilePx = 48f,
        )
    }
    var picked by remember { mutableStateOf(20) }
    var result by remember { mutableStateOf(20) }
    var trigger by remember { mutableStateOf(0L) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        InkLabel("d20 — pick a result and roll, or roll random. Landing face/number placement is what this tab exists to eyeball.")
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            InkStepper(value = picked, onValueChange = { picked = it.coerceIn(1, 20) }, min = 1)
            InkButton("Roll", modifier = Modifier.padding(start = 12.dp), onClick = {
                result = picked
                trigger++
            })
            InkButton("Roll Random", modifier = Modifier.padding(start = 8.dp), onClick = {
                result = Random.nextInt(1, 21)
                picked = result
                trigger++
            })
        }
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            DiceRoll(result = result, trigger = trigger, world = world, modifier = Modifier.size(240.dp))
        }
    }
}
