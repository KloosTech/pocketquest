package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.rules.content.startEncounter
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect

/**
 * doc11's `startEncounter` needs a party roster that doesn't exist yet (no RunState/persistent
 * party system) — the up-to-3 archetypes already in the catalog stand in for it, per the earlier
 * "simple placeholder party" call. This panel is purely a launcher: pick an encounter, spawn it,
 * hand the resulting GameState to [onPlaytest] to open in a real battle window.
 */
@Composable
fun PlaytestPanel(catalog: Catalog, onPlaytest: (GameState, Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf(catalog.encounters.keys.firstOrNull()) }
    val partyArchetypes = catalog.archetypes.values.take(3).toList()
    val party = partyArchetypes.map { it.id }

    Column(modifier = modifier.padding(16.dp)) {
        InkLabel("ENCOUNTER")
        InkSelect(
            selected = selectedId,
            options = catalog.encounters.keys.toList(),
            label = { it?.let { id -> catalog.encounters[id]?.name ?: id.raw } ?: "No encounters in this catalog" },
            onSelect = { selectedId = it },
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        InkLabel("PLACEHOLDER PARTY")
        BasicText(
            if (partyArchetypes.isEmpty()) "No archetypes in this catalog — add one on the Encounters tab first." else partyArchetypes.joinToString { it.name },
            style = TextStyle(color = INK, fontSize = 13.sp),
        )

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
        }
    }
}
