package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.rules.content.startEncounter
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/** docs/10-game-loop.md's real party cap — reused here even though this playtest party has nothing to do with a real `RunState`/roster; it's still the number of party slots a real encounter ever spawns into. */
private const val MAX_PARTY_SIZE = 3

/**
 * doc11's `startEncounter` needs a party roster that doesn't exist yet (no RunState/persistent
 * party system) — an author-picked selection of catalog archetypes stands in for it, per the earlier
 * "simple placeholder party" call, now toggleable instead of always "the first 3 archetypes in the
 * catalog" (which put whichever archetype an author added first into every playtest whether that
 * made sense or not). This panel is purely a launcher: pick an encounter and a party, spawn it, hand
 * the resulting GameState to [onPlaytest] to open in a real battle window.
 */
@Composable
fun PlaytestPanel(catalog: Catalog, onPlaytest: (GameState, Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf(catalog.encounters.keys.firstOrNull()) }
    // Defaults to the first up-to-3 archetypes so a fresh catalog behaves exactly like before this
    // picker existed — an author only needs to touch this if they want a different combination.
    var selectedArchetypes by remember { mutableStateOf(catalog.archetypes.keys.take(MAX_PARTY_SIZE).toSet()) }
    val party = catalog.archetypes.keys.filter { it in selectedArchetypes }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        InkLabel("ENCOUNTER")
        InkSelect(
            selected = selectedId,
            options = catalog.encounters.keys.toList(),
            label = { it?.let { id -> catalog.encounters[id]?.name ?: id.raw } ?: "No encounters in this catalog" },
            onSelect = { selectedId = it },
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        InkLabel("PARTY (up to $MAX_PARTY_SIZE)")
        val archetypes = catalog.archetypes.values.toList()
        if (archetypes.isEmpty()) {
            BasicText("No archetypes in this catalog — add one on the Archetypes tab first.", style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
        } else {
            Row(modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())) {
                archetypes.forEach { archetype ->
                    ArchetypeToggle(archetype.name, has = archetype.id in selectedArchetypes) {
                        selectedArchetypes = when {
                            archetype.id in selectedArchetypes -> selectedArchetypes - archetype.id
                            selectedArchetypes.size < MAX_PARTY_SIZE -> selectedArchetypes + archetype.id
                            else -> selectedArchetypes
                        }
                    }
                }
            }
        }

        val encounter = selectedId?.let { catalog.encounters[it] }
        InkButton(
            "Start Playtest",
            modifier = Modifier.padding(top = 16.dp),
            onClick = {
                if (encounter != null && party.isNotEmpty()) {
                    onPlaytest(startEncounter(catalog, encounter, party), catalog)
                }
            },
        )
        if (encounter == null) {
            BasicText("Pick an encounter above first.", style = TextStyle(color = INK_FAINT, fontSize = 11.sp), modifier = Modifier.padding(top = 4.dp))
        } else if (party.isEmpty()) {
            BasicText("Pick at least one party member above first.", style = TextStyle(color = INK_FAINT, fontSize = 11.sp), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ArchetypeToggle(label: String, has: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .border(1.dp, INK)
            .background(if (has) PAPER_SHEET else PAPER)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BasicText((if (has) "✓ " else "") + label, style = TextStyle(color = INK, fontSize = 12.sp))
    }
}
