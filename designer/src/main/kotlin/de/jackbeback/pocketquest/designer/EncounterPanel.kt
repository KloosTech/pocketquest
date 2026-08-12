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
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EnemySpawn
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.LootEntry
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.SpawnRole
import kotlin.math.roundToInt
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkStepper
import de.jackbeback.pocketquest.ui.ink.InkTextField
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/**
 * doc16's Encounter editor: "enemy composition, map reference, scaling." A list on the left,
 * the selected EncounterSpec's fields on the right — every edit replaces the working [catalog]
 * wholesale via [onCatalogChange], matching this project's immutable-state convention throughout
 * (never mutate, always produce a new value).
 */
@Composable
fun EncounterPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<EncounterId?>(catalog.encounters.keys.firstOrNull()) }

    fun updateEncounter(id: EncounterId, transform: (EncounterSpec) -> EncounterSpec) {
        val current = catalog.encounters[id] ?: return
        onCatalogChange(catalog.copy(encounters = catalog.encounters + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("ENCOUNTERS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.encounters.values.toList()) { encounter ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = encounter.id }
                            .background(if (encounter.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(encounter.name, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Encounter",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.encounters.size + 1
                    while (EncounterId("encounter$n") in catalog.encounters) n++
                    val id = EncounterId("encounter$n")
                    val defaultMap = catalog.maps.keys.firstOrNull() ?: MapId("")
                    val spec = EncounterSpec(id = id, name = "New Encounter $n", mapId = defaultMap)
                    onCatalogChange(catalog.copy(encounters = catalog.encounters + (id to spec)))
                    selectedId = id
                },
            )
        }

        val encounter = selectedId?.let { catalog.encounters[it] }
        if (encounter != null) {
            EncounterEditor(
                encounter = encounter,
                catalog = catalog,
                onChange = { updated -> updateEncounter(encounter.id) { updated } },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No encounter selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun EncounterEditor(encounter: EncounterSpec, catalog: Catalog, onChange: (EncounterSpec) -> Unit) {
    val maps = catalog.maps.values.toList()
    val archetypes = catalog.archetypes.values.toList()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        InkLabel("NAME")
        InkTextField(encounter.name, onValueChange = { onChange(encounter.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 12.dp)) {
            InkLabel("MAP")
        }
        if (maps.isEmpty()) {
            BasicText("No maps in the working catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp))
        } else {
            val selectedMap = maps.find { it.id == encounter.mapId } ?: maps.first()
            InkSelect(
                selected = selectedMap,
                options = maps,
                label = { "${it.name.ifBlank { it.id.raw }} (${it.width}x${it.height})" },
                onSelect = { onChange(encounter.copy(mapId = it.id)) },
            )
        }

        Box(modifier = Modifier.padding(top = 16.dp)) {
            InkLabel("ENEMIES")
        }
        encounter.enemies.forEachIndexed { index, spawn ->
            EnemySpawnRow(
                spawn = spawn,
                archetypes = archetypes,
                onChange = { updated -> onChange(encounter.copy(enemies = encounter.enemies.toMutableList().also { it[index] = updated })) },
                onRemove = { onChange(encounter.copy(enemies = encounter.enemies.filterIndexed { i, _ -> i != index })) },
            )
        }
        InkButton(
            "+ Add Enemy",
            modifier = Modifier.padding(top = 4.dp),
            onClick = {
                val defaultArchetype = archetypes.firstOrNull()?.id ?: ArchetypeId("")
                onChange(encounter.copy(enemies = encounter.enemies + EnemySpawn(defaultArchetype)))
            },
        )

        Box(modifier = Modifier.padding(top = 16.dp)) {
            InkLabel("SCALING")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Extra enemies per party size:", style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
            InkStepper(encounter.scaling.extraEnemiesPerPartySize, onValueChange = { onChange(encounter.copy(scaling = encounter.scaling.copy(extraEnemiesPerPartySize = it))) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            BasicText("Extra enemies per act:", style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
            InkStepper(encounter.scaling.extraEnemiesPerAct, onValueChange = { onChange(encounter.copy(scaling = encounter.scaling.copy(extraEnemiesPerAct = it))) })
        }

        Box(modifier = Modifier.padding(top = 16.dp)) {
            InkLabel("GOLD REWARD (docs/11: finishEncounter rolls a value in this range)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Min:", style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
            InkStepper(encounter.goldMin, min = 0, onValueChange = { onChange(encounter.copy(goldMin = it, goldMax = maxOf(it, encounter.goldMax))) })
            BasicText("Max:", style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.padding(start = 16.dp, end = 8.dp))
            InkStepper(encounter.goldMax, min = encounter.goldMin, onValueChange = { onChange(encounter.copy(goldMax = it)) })
        }

        Box(modifier = Modifier.padding(top = 16.dp)) {
            InkLabel("LOOT (docs/11: each entry rolled independently by its own chance)")
        }
        val items = catalog.items.values.toList()
        encounter.loot.forEachIndexed { index, entry ->
            LootEntryRow(
                entry = entry,
                items = items,
                onChange = { updated -> onChange(encounter.copy(loot = encounter.loot.toMutableList().also { it[index] = updated })) },
                onRemove = { onChange(encounter.copy(loot = encounter.loot.filterIndexed { i, _ -> i != index })) },
            )
        }
        if (items.isEmpty()) {
            BasicText("No items in the working catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
        } else {
            InkButton(
                "+ Add Loot Entry",
                modifier = Modifier.padding(top = 4.dp),
                onClick = { onChange(encounter.copy(loot = encounter.loot + LootEntry(items.first().id))) },
            )
        }
    }
}

@Composable
private fun LootEntryRow(entry: LootEntry, items: List<ItemDef>, onChange: (LootEntry) -> Unit, onRemove: () -> Unit) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (items.isEmpty()) {
            BasicText(entry.item.raw, style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
        } else {
            InkSelect(
                selected = items.find { it.id == entry.item } ?: items.first(),
                options = items,
                label = { it.name },
                onSelect = { onChange(entry.copy(item = it.id)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        BasicText("Chance:", style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
        InkStepper(
            (entry.chance * 100).roundToInt(),
            min = 0,
            onValueChange = { onChange(entry.copy(chance = it.coerceIn(0, 100) / 100.0)) },
        )
        BasicText("%", style = TextStyle(color = INK_FAINT, fontSize = 12.sp), modifier = Modifier.padding(start = 4.dp, end = 8.dp))
        InkButton("Remove", onClick = onRemove)
    }
}

@Composable
private fun EnemySpawnRow(spawn: EnemySpawn, archetypes: List<Archetype>, onChange: (EnemySpawn) -> Unit, onRemove: () -> Unit) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (archetypes.isEmpty()) {
            BasicText(spawn.archetype.raw.ifEmpty { "(no archetypes loaded)" }, style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
        } else {
            InkSelect(
                selected = archetypes.find { it.id == spawn.archetype } ?: archetypes.first(),
                options = archetypes,
                label = { it.name },
                onSelect = { onChange(spawn.copy(archetype = it.id)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        InkSelect(
            selected = spawn.role,
            options = SpawnRole.entries,
            label = { it.name },
            onSelect = { onChange(spawn.copy(role = it)) },
            modifier = Modifier.padding(end = 8.dp),
        )
        InkStepper(spawn.count, min = 1, onValueChange = { onChange(spawn.copy(count = it)) })
        InkButton("Remove", modifier = Modifier.padding(start = 8.dp), onClick = onRemove)
    }
}
