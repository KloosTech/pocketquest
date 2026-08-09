package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.RunEffect
import de.jackbeback.pocketquest.core.model.RunEffectTarget
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkTextField

private enum class RunEffectKind { GrantCurrency, GrantItem, LoseItem, DamageParty, HealParty, ForceCombat }

private fun RunEffect.kind(): RunEffectKind = when (this) {
    is RunEffect.GrantCurrency -> RunEffectKind.GrantCurrency
    is RunEffect.GrantItem -> RunEffectKind.GrantItem
    is RunEffect.LoseItem -> RunEffectKind.LoseItem
    is RunEffect.DamageParty -> RunEffectKind.DamageParty
    is RunEffect.HealParty -> RunEffectKind.HealParty
    is RunEffect.ForceCombat -> RunEffectKind.ForceCombat
}

private fun defaultFor(kind: RunEffectKind, catalog: Catalog): RunEffect = when (kind) {
    RunEffectKind.GrantCurrency -> RunEffect.GrantCurrency(0)
    RunEffectKind.GrantItem -> RunEffect.GrantItem(catalog.items.keys.firstOrNull() ?: ItemId(""))
    RunEffectKind.LoseItem -> RunEffect.LoseItem(catalog.items.keys.firstOrNull() ?: ItemId(""))
    RunEffectKind.DamageParty -> RunEffect.DamageParty(0, RunEffectTarget.WholeParty)
    RunEffectKind.HealParty -> RunEffect.HealParty(0, RunEffectTarget.WholeParty)
    RunEffectKind.ForceCombat -> RunEffect.ForceCombat(catalog.encounters.keys.firstOrNull() ?: EncounterId(""))
}

/** docs/13-encounters-and-events.md's `RunEffect` — same "type dropdown + inline fields" pattern as [EffectTemplateListEditor]. */
@Composable
fun RunEffectListEditor(effects: List<RunEffect>, catalog: Catalog, onChange: (List<RunEffect>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        effects.forEachIndexed { index, effect ->
            RunEffectRow(
                value = effect,
                catalog = catalog,
                onChange = { updated -> onChange(effects.toMutableList().also { it[index] = updated }) },
                onRemove = { onChange(effects.filterIndexed { i, _ -> i != index }) },
            )
        }
        InkButton("+ Add Effect", modifier = Modifier.padding(top = 4.dp), onClick = { onChange(effects + defaultFor(RunEffectKind.GrantCurrency, catalog)) })
    }
}

@Composable
private fun RunEffectRow(value: RunEffect, catalog: Catalog, onChange: (RunEffect) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkSelect(
                selected = value.kind(),
                options = RunEffectKind.entries,
                label = { it.name },
                onSelect = { onChange(defaultFor(it, catalog)) },
                modifier = Modifier.padding(end = 8.dp),
            )
            InkButton("Remove", onClick = onRemove)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            when (value) {
                is RunEffect.GrantCurrency -> IntField(value.amount, label = "amount (negative = a cost)") { onChange(value.copy(amount = it)) }
                is RunEffect.GrantItem -> ItemSelect(value.item, catalog) { onChange(value.copy(item = it)) }
                is RunEffect.LoseItem -> ItemSelect(value.item, catalog) { onChange(value.copy(item = it)) }
                is RunEffect.DamageParty -> {
                    IntField(value.amount, label = "amount") { onChange(value.copy(amount = it)) }
                    TargetSelect(value.target) { onChange(value.copy(target = it)) }
                }
                is RunEffect.HealParty -> {
                    IntField(value.amount, label = "amount") { onChange(value.copy(amount = it)) }
                    TargetSelect(value.target) { onChange(value.copy(target = it)) }
                }
                is RunEffect.ForceCombat -> EncounterSelect(value.encounter, catalog) { onChange(value.copy(encounter = it)) }
            }
        }
    }
}

@Composable
private fun ItemSelect(selected: ItemId, catalog: Catalog, onSelect: (ItemId) -> Unit) {
    val options = catalog.items.values.toList()
    if (options.isEmpty()) {
        InkLabel("no items yet")
    } else {
        InkSelect(options.find { it.id == selected } ?: options.first(), options, { it.name }, { onSelect(it.id) }, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun EncounterSelect(selected: EncounterId, catalog: Catalog, onSelect: (EncounterId) -> Unit) {
    val options = catalog.encounters.values.toList()
    if (options.isEmpty()) {
        InkLabel("no encounters yet")
    } else {
        InkSelect(options.find { it.id == selected } ?: options.first(), options, { it.name }, { onSelect(it.id) }, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun TargetSelect(selected: RunEffectTarget, onSelect: (RunEffectTarget) -> Unit) {
    InkSelect(selected, RunEffectTarget.entries, { it.name }, onSelect, modifier = Modifier.padding(start = 8.dp))
}

@Composable
private fun IntField(value: Int, label: String? = null, onChange: (Int) -> Unit) {
    label?.let { InkLabel(it, modifier = Modifier.padding(end = 4.dp)) }
    var text by remember(value) { mutableStateOf(value.toString()) }
    InkTextField(text, onValueChange = { text = it; it.toIntOrNull()?.let(onChange) }, modifier = Modifier.width(50.dp).padding(end = 4.dp))
}
