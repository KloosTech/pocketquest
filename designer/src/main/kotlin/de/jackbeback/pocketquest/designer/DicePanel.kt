package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
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
import de.jackbeback.pocketquest.core.model.RollBreakdown
import de.jackbeback.pocketquest.core.model.RollTerm
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.ui.DiceRollOverlay
import de.jackbeback.pocketquest.ui.RollCard
import de.jackbeback.pocketquest.ui.VisualWorld
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkStepper
import kotlin.random.Random

/**
 * A visual-tuning bench for the docs/22-dice-roll-ui-and-ability-checks.md roll card — no catalog,
 * no combat state, just [RollCard] wired to a Roll button so a tumble/settle/shading/layout tweak
 * can be eyeballed in a tight loop instead of needing a real playtest fight or event to trigger an
 * actual roll. Home for every current and future roll-card variant as they're added (currently:
 * the d20 attack/save/check card, with an extra bonus chip and an Advantage dual-die toggle).
 */
@Composable
fun DicePanel(modifier: Modifier = Modifier) {
    // RollCard/DiceRoll only read VisualWorld.scaled() (i.e. .speed) — the GameState behind it is
    // otherwise unused set-dressing, so the cheapest legal one will do.
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
    var dc by remember { mutableStateOf(12) }
    var bonus by remember { mutableStateOf(3) }
    var advantage by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<DiceRollOverlay?>(null) }
    var nextId by remember { mutableStateOf(0L) }

    fun roll(result: Int) {
        // Real Advantage always resolves to the higher of the pair (d20Detailed guarantees this) —
        // an unconstrained random "other" here could exceed the picked result and make the card
        // look like it faded out the winning die, which isn't a real roll-card bug, just this bench
        // generating an impossible pair. Constraining it to 1..result keeps every preview honest.
        val other = if (advantage) Random.nextInt(1, result + 1) else null
        val breakdown = RollBreakdown(listOf(RollTerm("Str", bonus)))
        overlay = DiceRollOverlay(
            id = nextId++,
            title = "Attack Roll",
            result = result,
            target = dc,
            breakdown = breakdown,
            succeeded = result + bonus >= dc,
            otherResult = other,
        )
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        InkLabel("Roll-card preview — pick a result/DC/bonus and roll, or roll random. This tab exists to eyeball landing/centering/breakdown/dual-die display, not just the bare die.")
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            InkLabel("Result", modifier = Modifier.padding(end = 4.dp))
            InkStepper(value = picked, onValueChange = { picked = it.coerceIn(1, 20) }, min = 1)
            InkLabel("DC", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            InkStepper(value = dc, onValueChange = { dc = it.coerceAtLeast(0) }, min = 0)
            InkLabel("Bonus", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            InkStepper(value = bonus, onValueChange = { bonus = it }, min = -10)
        }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            InkButton("Advantage: ${if (advantage) "On" else "Off"}", emphasized = advantage, onClick = { advantage = !advantage })
        }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            InkButton("Roll", onClick = { roll(picked) })
            InkButton("Roll Random", modifier = Modifier.padding(start = 8.dp), onClick = {
                picked = Random.nextInt(1, 21)
                roll(picked)
            })
        }
        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            overlay?.let { RollCard(overlay = it, world = world) }
        }
    }
}
