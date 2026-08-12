package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import de.jackbeback.pocketquest.core.model.FeatureId
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.Slot
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
 * doc20's Item editor. `validSlots` (empty = unconstrained) is the new field this pass added to
 * `ItemDef` — a real `:core:rules` `canEquip` check backs it, this is just the authoring surface.
 * damageSteps/healSteps deferred, same scoping call as every other editor this pass (doc18's
 * pipeline mechanic is niche/rarely-authored, not worth a sub-editor yet).
 */
@Composable
fun ItemPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<ItemId?>(catalog.items.keys.firstOrNull()) }

    fun updateItem(id: ItemId, transform: (ItemDef) -> ItemDef) {
        val current = catalog.items[id] ?: return
        onCatalogChange(catalog.copy(items = catalog.items + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("ITEMS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.items.values.toList()) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = item.id }
                            .background(if (item.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(item.name, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Item",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.items.size + 1
                    while (ItemId("item$n") in catalog.items) n++
                    val id = ItemId("item$n")
                    onCatalogChange(catalog.copy(items = catalog.items + (id to ItemDef(id, "New Item $n"))))
                    selectedId = id
                },
            )
        }

        val item = selectedId?.let { catalog.items[it] }
        if (item != null) {
            ItemEditor(
                item = item,
                catalog = catalog,
                onChange = { updated -> updateItem(item.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(items = catalog.items - item.id))
                    selectedId = catalog.items.keys.firstOrNull { it != item.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No item selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun ItemEditor(item: ItemDef, catalog: Catalog, onChange: (ItemDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove Item", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(item.name, onValueChange = { onChange(item.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("BASE PRICE (docs/13: sell value is 50% of this)") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkStepper(item.basePrice, min = 0, onValueChange = { onChange(item.copy(basePrice = it)) })
        }

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("GRANTS FEATURE (docs/11: equipping this item also grants...)") }
        val featureOptions = listOf<FeatureId?>(null) + catalog.features.keys
        InkSelect(
            selected = item.grantsFeature,
            options = featureOptions,
            label = { id -> id?.let { catalog.features[it]?.name ?: it.raw } ?: "(none)" },
            onSelect = { onChange(item.copy(grantsFeature = it)) },
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            BasicText(if (item.twoHanded) "☑" else "☐", style = TextStyle(color = INK, fontSize = 14.sp), modifier = Modifier.padding(end = 4.dp))
            BasicText(
                "Two-handed (occupies MainHand + OffHand)",
                style = TextStyle(color = INK, fontSize = 12.sp),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(item.copy(twoHanded = !item.twoHanded)) },
            )
        }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("VALID SLOTS (none = unconstrained)") }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Slot.entries.forEach { slot ->
                SlotToggle(slot, has = slot in item.validSlots) {
                    val updated = if (slot in item.validSlots) item.validSlots - slot else item.validSlots + slot
                    onChange(item.copy(validSlots = updated))
                }
            }
        }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("MODIFIERS") }
        ModifierListEditor(item.modifiers, onChange = { onChange(item.copy(modifiers = it)) })
    }
}

@Composable
private fun SlotToggle(slot: Slot, has: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .border(1.dp, INK)
            .background(if (has) PAPER_SHEET else PAPER)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        BasicText((if (has) "✓ " else "") + slot.name, style = TextStyle(color = INK, fontSize = 12.sp))
    }
}
