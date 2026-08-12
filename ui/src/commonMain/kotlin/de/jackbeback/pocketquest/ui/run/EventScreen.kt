package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.model.EventPool
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.run.EventChoiceResolution
import de.jackbeback.pocketquest.core.run.GraphNode
import de.jackbeback.pocketquest.core.run.MemberId
import de.jackbeback.pocketquest.core.run.RunState
import de.jackbeback.pocketquest.core.run.previewEventCheck
import de.jackbeback.pocketquest.core.run.resolveEventChoice
import de.jackbeback.pocketquest.core.run.resolveEventNode
import de.jackbeback.pocketquest.ui.DiceRollOverlay
import de.jackbeback.pocketquest.ui.ModifierChip
import de.jackbeback.pocketquest.ui.RollCard
import de.jackbeback.pocketquest.ui.VisualWorld
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.PAPER

/**
 * docs/13-encounters-and-events.md's Events section. The picked [de.jackbeback.pocketquest.core.model.EventDef]
 * is resolved once per node visit (`remember(run.position)`), not re-rolled on every recomposition —
 * if the app restarts before a choice is made, the pick simply happens again from `run.rng` (which
 * was never advanced in the saved [RunState]), same accepted "no persisted mid-node state" tradeoff
 * doc13 already makes for a shop's offered stock.
 *
 * docs/22-dice-roll-ui-and-ability-checks.md: a checked [EventChoice] no longer auto-picks a roller
 * or auto-resolves the instant it's picked — the player chooses who attempts it (seeing every
 * candidate's own modifier first), then sees the DC/breakdown card and explicitly rolls, matching
 * combat's now-real roll card rather than the old silent one-line outcome.
 */
@Composable
fun EventNodeScreen(run: RunState, node: GraphNode, cat: Catalog, pools: List<EventPool>, onResolved: (RunState) -> Unit) {
    val (event, rngAfterPick) = remember(run.position) { resolveEventNode(run, node, pools, cat) }
    val runAfterPick = remember(run.position) { run.copy(rng = rngAfterPick) }
    var pendingChoice by remember(run.position) { mutableStateOf<EventChoice?>(null) }
    var pendingRoller by remember(run.position) { mutableStateOf<MemberId?>(null) }
    var resolution by remember(run.position) { mutableStateOf<EventChoiceResolution?>(null) }
    var rollTrigger by remember(run.position) { mutableStateOf(0L) }
    // DiceRoll/RollCard only ever read world.scaled() (i.e. .speed) — no real entities/board exist
    // here at all, this event screen isn't a battle, so the cheapest legal GameState is enough.
    val world = remember {
        VisualWorld(
            initial = GameState(entities = emptyList(), map = BattleMap(1, 1), turn = TurnState(1, emptyList(), 0, TurnPhase.Main), rng = RngState(0L)),
            tilePx = 48f,
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText(event.title, style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(8.dp))
        BasicText(event.body, style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
        Spacer(modifier = Modifier.size(16.dp))

        val result = resolution
        val choice = pendingChoice
        val roller = pendingRoller
        when {
            result != null -> {
                result.checkOutcome?.let { outcome ->
                    val overlay = DiceRollOverlay(
                        id = rollTrigger,
                        title = choice?.check?.let { it.skill?.name ?: it.ability.name } ?: "Check",
                        result = outcome.d20,
                        target = outcome.dc,
                        breakdown = outcome.breakdown,
                        succeeded = outcome.success,
                        otherResult = outcome.otherD20,
                    )
                    RollCard(overlay = overlay, world = world)
                    Spacer(modifier = Modifier.size(16.dp))
                }
                BasicText(result.text, style = TextStyle(color = INK, fontSize = 14.sp))
                Spacer(modifier = Modifier.size(16.dp))
                InkButton("Continue", onClick = { onResolved(result.run) })
            }
            choice != null && choice.check != null -> {
                // Smart-cast doesn't flow choice.check's non-null-ness from the when condition into
                // the lambdas below (forEach/onClick) — proven true by the condition, just made explicit.
                val check = checkNotNull(choice.check)
                if (roller == null) {
                    BasicText("Who attempts this?", style = TextStyle(color = INK, fontSize = 14.sp))
                    Spacer(modifier = Modifier.size(8.dp))
                    runAfterPick.party.forEach { member ->
                        val preview = previewEventCheck(runAfterPick, check, member.memberId, cat)
                        val sign = if (preview.total >= 0) "+" else ""
                        InkButton(
                            "${member.name} ($sign${preview.total})",
                            modifier = Modifier.padding(bottom = 8.dp),
                            onClick = { pendingRoller = member.memberId },
                        )
                    }
                    InkButton("Cancel", onClick = { pendingChoice = null })
                } else {
                    val preview = previewEventCheck(runAfterPick, check, roller, cat)
                    BasicText("DIFFICULTY CLASS", style = TextStyle(color = INK_FAINT, fontSize = 10.sp))
                    BasicText("${check.dc}", style = TextStyle(color = INK, fontSize = 22.sp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Row { preview.terms.forEach { ModifierChip(it) } }
                    Spacer(modifier = Modifier.size(16.dp))
                    InkButton(
                        "Roll",
                        onClick = {
                            resolution = resolveEventChoice(runAfterPick, choice, roller, cat)
                            rollTrigger++
                        },
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    InkButton("Back", onClick = { pendingRoller = null })
                }
            }
            else -> {
                event.choices.forEach { c ->
                    InkButton(
                        c.label,
                        modifier = Modifier.padding(bottom = 8.dp),
                        onClick = {
                            if (c.check == null) {
                                resolution = resolveEventChoice(runAfterPick, c, runAfterPick.party.first().memberId, cat)
                            } else {
                                pendingChoice = c
                            }
                        },
                    )
                }
            }
        }
    }
}
