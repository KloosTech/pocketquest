package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.run.EventPool
import de.jackbeback.pocketquest.core.run.GraphNode
import de.jackbeback.pocketquest.core.run.RunState
import de.jackbeback.pocketquest.core.run.applyRunEffect
import de.jackbeback.pocketquest.core.run.resolveEventNode
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
 */
@Composable
fun EventNodeScreen(run: RunState, node: GraphNode, cat: Catalog, pools: List<EventPool>, onResolved: (RunState) -> Unit) {
    val (event, rngAfterPick) = remember(run.position) { resolveEventNode(run, node, pools, cat) }
    var resolution by remember(run.position) { mutableStateOf<Pair<String, RunState>?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText(event.title, style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(8.dp))
        BasicText(event.body, style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
        Spacer(modifier = Modifier.size(16.dp))

        val current = resolution
        if (current != null) {
            val (outcomeText, resolved) = current
            BasicText(outcomeText, style = TextStyle(color = INK, fontSize = 14.sp))
            Spacer(modifier = Modifier.size(16.dp))
            InkButton("Continue", onClick = { onResolved(resolved) })
        } else {
            event.choices.forEach { choice: EventChoice ->
                InkButton(
                    choice.label,
                    modifier = Modifier.padding(bottom = 8.dp),
                    onClick = {
                        var updated = run.copy(rng = rngAfterPick)
                        for (effect in choice.effects) updated = applyRunEffect(updated, effect, cat)
                        resolution = choice.outcomeText to updated
                    },
                )
            }
        }
    }
}
