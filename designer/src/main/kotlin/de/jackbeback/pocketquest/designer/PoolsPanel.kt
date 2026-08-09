package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterPool
import de.jackbeback.pocketquest.core.model.EventId
import de.jackbeback.pocketquest.core.model.EventPool
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.ShopId
import de.jackbeback.pocketquest.core.model.ShopPool
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkStepper
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/**
 * docs/13-encounters-and-events.md's Content pools section — which authored content (by id) a
 * generated node of a given act/kind may resolve to. Unlike every other tab, pools aren't keyed by
 * a stable id of their own (`Catalog.encounterPools` etc. are plain lists), so this is index-based
 * CRUD rather than the usual "list on the left, editor on the right" split.
 */
@Composable
fun PoolsPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        InkLabel("ENCOUNTER POOLS (which Combat/Elite/Boss nodes draw from)")
        catalog.encounterPools.forEachIndexed { index, pool ->
            EncounterPoolRow(
                pool = pool,
                catalog = catalog,
                onChange = { updated -> onCatalogChange(catalog.copy(encounterPools = catalog.encounterPools.toMutableList().also { it[index] = updated })) },
                onRemove = { onCatalogChange(catalog.copy(encounterPools = catalog.encounterPools.filterIndexed { i, _ -> i != index })) },
            )
        }
        InkButton(
            "+ Add Encounter Pool",
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            onClick = { onCatalogChange(catalog.copy(encounterPools = catalog.encounterPools + EncounterPool(act = 1, kind = NodeType.Combat, entries = emptyList()))) },
        )

        InkLabel("EVENT POOLS")
        catalog.eventPools.forEachIndexed { index, pool ->
            EventPoolRow(
                pool = pool,
                catalog = catalog,
                onChange = { updated -> onCatalogChange(catalog.copy(eventPools = catalog.eventPools.toMutableList().also { it[index] = updated })) },
                onRemove = { onCatalogChange(catalog.copy(eventPools = catalog.eventPools.filterIndexed { i, _ -> i != index })) },
            )
        }
        InkButton(
            "+ Add Event Pool",
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            onClick = { onCatalogChange(catalog.copy(eventPools = catalog.eventPools + EventPool(act = 1, entries = emptyList()))) },
        )

        InkLabel("SHOP POOLS")
        catalog.shopPools.forEachIndexed { index, pool ->
            ShopPoolRow(
                pool = pool,
                catalog = catalog,
                onChange = { updated -> onCatalogChange(catalog.copy(shopPools = catalog.shopPools.toMutableList().also { it[index] = updated })) },
                onRemove = { onCatalogChange(catalog.copy(shopPools = catalog.shopPools.filterIndexed { i, _ -> i != index })) },
            )
        }
        InkButton(
            "+ Add Shop Pool",
            modifier = Modifier.padding(top = 4.dp),
            onClick = { onCatalogChange(catalog.copy(shopPools = catalog.shopPools + ShopPool(act = 1, entries = emptyList()))) },
        )
    }
}

@Composable
private fun EncounterPoolRow(pool: EncounterPool, catalog: Catalog, onChange: (EncounterPool) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp).border(1.dp, INK_FAINT).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("ACT", modifier = Modifier.padding(end = 4.dp))
            InkStepper(pool.act, min = 1, onValueChange = { onChange(pool.copy(act = it)) })
            InkLabel("KIND", modifier = Modifier.padding(start = 16.dp, end = 4.dp))
            InkSelect(pool.kind, NodeType.entries, { it.name }, { onChange(pool.copy(kind = it)) }, modifier = Modifier.padding(end = 16.dp))
            InkButton("Remove Pool", onClick = onRemove)
        }
        val encounters = catalog.encounters.values.toList()
        if (encounters.isEmpty()) {
            BasicText("No encounters in the working catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(top = 8.dp))
        } else {
            Row(modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                encounters.forEach { encounter ->
                    IdToggle(encounter.name, has = encounter.id in pool.entries) {
                        val updated = if (encounter.id in pool.entries) pool.entries - encounter.id else pool.entries + encounter.id
                        onChange(pool.copy(entries = updated))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventPoolRow(pool: EventPool, catalog: Catalog, onChange: (EventPool) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp).border(1.dp, INK_FAINT).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("ACT", modifier = Modifier.padding(end = 4.dp))
            InkStepper(pool.act, min = 1, onValueChange = { onChange(pool.copy(act = it)) })
            InkButton("Remove Pool", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        val events = catalog.events.values.toList()
        if (events.isEmpty()) {
            BasicText("No events in the working catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(top = 8.dp))
        } else {
            Row(modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                events.forEach { event ->
                    IdToggle(event.title, has = event.id in pool.entries) {
                        val updated = if (event.id in pool.entries) pool.entries - event.id else pool.entries + event.id
                        onChange(pool.copy(entries = updated))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopPoolRow(pool: ShopPool, catalog: Catalog, onChange: (ShopPool) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 6.dp).border(1.dp, INK_FAINT).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("ACT", modifier = Modifier.padding(end = 4.dp))
            InkStepper(pool.act, min = 1, onValueChange = { onChange(pool.copy(act = it)) })
            InkButton("Remove Pool", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        val shops = catalog.shops.values.toList()
        if (shops.isEmpty()) {
            BasicText("No shops in the working catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(top = 8.dp))
        } else {
            Row(modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                shops.forEach { shop ->
                    IdToggle(shop.id.raw, has = shop.id in pool.entries) {
                        val updated = if (shop.id in pool.entries) pool.entries - shop.id else pool.entries + shop.id
                        onChange(pool.copy(entries = updated))
                    }
                }
            }
        }
    }
}

@Composable
private fun IdToggle(label: String, has: Boolean, onToggle: () -> Unit) {
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
