package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.model.EventDef
import de.jackbeback.pocketquest.core.model.EventId
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkTextField
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/**
 * docs/13-encounters-and-events.md's Events section — "handcrafted text + choices... 1-4 choices,
 * each can help or hurt." Same CRUD-list-on-the-left shape as [ItemPanel]/[EncounterPanel].
 */
@Composable
fun EventPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<EventId?>(catalog.events.keys.firstOrNull()) }

    fun updateEvent(id: EventId, transform: (EventDef) -> EventDef) {
        val current = catalog.events[id] ?: return
        onCatalogChange(catalog.copy(events = catalog.events + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("EVENTS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.events.values.toList()) { event ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = event.id }
                            .background(if (event.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(event.title, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Event",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.events.size + 1
                    while (EventId("event$n") in catalog.events) n++
                    val id = EventId("event$n")
                    val def = EventDef(id = id, title = "New Event $n", body = "", choices = listOf(EventChoice(label = "Continue", outcomeText = "")))
                    onCatalogChange(catalog.copy(events = catalog.events + (id to def)))
                    selectedId = id
                },
            )
        }

        val event = selectedId?.let { catalog.events[it] }
        if (event != null) {
            EventEditor(
                event = event,
                catalog = catalog,
                onChange = { updated -> updateEvent(event.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(events = catalog.events - event.id))
                    selectedId = catalog.events.keys.firstOrNull { it != event.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No event selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun EventEditor(event: EventDef, catalog: Catalog, onChange: (EventDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("TITLE")
            InkButton("Remove Event", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(event.title, onValueChange = { onChange(event.copy(title = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("BODY (flavor text)") }
        InkTextField(event.body, onValueChange = { onChange(event.copy(body = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("CHOICES (1-4, each can help or hurt)") }
        if (event.choices.size !in 1..4) {
            BasicText("${event.choices.size} choices — must be 1..4", style = TextStyle(color = DANGER, fontSize = 12.sp))
        }
        event.choices.forEachIndexed { index, choice ->
            EventChoiceEditor(
                choice = choice,
                catalog = catalog,
                onChange = { updated -> onChange(event.copy(choices = event.choices.toMutableList().also { it[index] = updated })) },
                onRemove = { onChange(event.copy(choices = event.choices.filterIndexed { i, _ -> i != index })) },
            )
        }
        InkButton(
            "+ Add Choice",
            modifier = Modifier.padding(top = 4.dp),
            onClick = { onChange(event.copy(choices = event.choices + EventChoice(label = "Choice", outcomeText = ""))) },
        )
    }
}

@Composable
private fun EventChoiceEditor(choice: EventChoice, catalog: Catalog, onChange: (EventChoice) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp).border(1.dp, INK_FAINT).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("LABEL (the button text)", modifier = Modifier.padding(end = 8.dp))
            InkButton("Remove Choice", onClick = onRemove)
        }
        InkTextField(choice.label, onValueChange = { onChange(choice.copy(label = it)) }, modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            BasicText(if (choice.check != null) "☑" else "☐", style = TextStyle(color = INK, fontSize = 14.sp), modifier = Modifier.padding(end = 4.dp))
            BasicText(
                "Ability check (roll to decide success/failure, instead of an unconditional outcome)",
                style = TextStyle(color = INK, fontSize = 12.sp),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    onChange(choice.copy(check = if (choice.check != null) null else de.jackbeback.pocketquest.core.model.EventCheck(de.jackbeback.pocketquest.core.model.Ability.Str, 10)))
                },
            )
        }

        val check = choice.check
        if (check == null) {
            Box(modifier = Modifier.padding(top = 8.dp)) { InkLabel("OUTCOME TEXT (shown after picking, before effects apply)") }
            InkTextField(choice.outcomeText, onValueChange = { onChange(choice.copy(outcomeText = it)) }, modifier = Modifier.fillMaxWidth())

            Box(modifier = Modifier.padding(top = 8.dp)) { InkLabel("EFFECTS") }
            RunEffectListEditor(choice.effects, catalog, onChange = { onChange(choice.copy(effects = it)) })
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                InkLabel("ABILITY", modifier = Modifier.padding(end = 8.dp))
                de.jackbeback.pocketquest.ui.ink.InkSelect(
                    check.ability, de.jackbeback.pocketquest.core.model.Ability.entries, { it.name },
                    { onChange(choice.copy(check = check.copy(ability = it))) },
                    modifier = Modifier.padding(end = 16.dp),
                )
                InkLabel("DC", modifier = Modifier.padding(end = 8.dp))
                de.jackbeback.pocketquest.ui.ink.InkStepper(check.dc, min = 0, onValueChange = { onChange(choice.copy(check = check.copy(dc = it))) })
            }

            Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("ON SUCCESS (leave effects empty for \"only ever hurts\")") }
            InkTextField(choice.successText, onValueChange = { onChange(choice.copy(successText = it)) }, modifier = Modifier.fillMaxWidth())
            RunEffectListEditor(choice.successEffects, catalog, onChange = { onChange(choice.copy(successEffects = it)) }, modifier = Modifier.padding(top = 4.dp))

            Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("ON FAILURE (leave effects empty for \"only ever helps\")") }
            InkTextField(choice.failureText, onValueChange = { onChange(choice.copy(failureText = it)) }, modifier = Modifier.fillMaxWidth())
            RunEffectListEditor(choice.failureEffects, catalog, onChange = { onChange(choice.copy(failureEffects = it)) }, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
