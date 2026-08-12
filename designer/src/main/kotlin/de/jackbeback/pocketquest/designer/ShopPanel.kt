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
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.ShopDef
import de.jackbeback.pocketquest.core.model.ShopEntry
import de.jackbeback.pocketquest.core.model.ShopId
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
 * docs/13-encounters-and-events.md's Shops section — "a Shop node picks N entries at random from
 * the act-matching ShopDef.stock." Same CRUD-list-on-the-left shape as [ItemPanel]/[EncounterPanel]/
 * [EventPanel]. `ShopDef` has no name field (doc13's own shape), so the list shows `id.raw`.
 */
@Composable
fun ShopPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<ShopId?>(catalog.shops.keys.firstOrNull()) }

    fun updateShop(id: ShopId, transform: (ShopDef) -> ShopDef) {
        val current = catalog.shops[id] ?: return
        onCatalogChange(catalog.copy(shops = catalog.shops + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("SHOPS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.shops.values.toList()) { shop ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = shop.id }
                            .background(if (shop.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText("${shop.id.raw} (act ${shop.act})", style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Shop",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.shops.size + 1
                    while (ShopId("shop$n") in catalog.shops) n++
                    val id = ShopId("shop$n")
                    onCatalogChange(catalog.copy(shops = catalog.shops + (id to ShopDef(id = id, act = 1, stock = emptyList()))))
                    selectedId = id
                },
            )
        }

        val shop = selectedId?.let { catalog.shops[it] }
        if (shop != null) {
            ShopEditor(
                shop = shop,
                catalog = catalog,
                onChange = { updated -> updateShop(shop.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(shops = catalog.shops - shop.id))
                    selectedId = catalog.shops.keys.firstOrNull { it != shop.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No shop selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun ShopEditor(shop: ShopDef, catalog: Catalog, onChange: (ShopDef) -> Unit, onRemove: () -> Unit) {
    val items = catalog.items.values.toList()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("ID: ${shop.id.raw}", modifier = Modifier.padding(end = 16.dp))
            InkButton("Remove Shop", onClick = onRemove)
        }

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("ACT (which act's Shop pool this shop is drawn from)") }
        InkStepper(shop.act, min = 1, onValueChange = { onChange(shop.copy(act = it)) })

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("STOCK") }
        shop.stock.forEachIndexed { index, entry ->
            ShopEntryRow(
                entry = entry,
                items = items,
                onChange = { updated -> onChange(shop.copy(stock = shop.stock.toMutableList().also { it[index] = updated })) },
                onRemove = { onChange(shop.copy(stock = shop.stock.filterIndexed { i, _ -> i != index })) },
            )
        }
        if (items.isEmpty()) {
            BasicText("No items in the working catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
        } else {
            InkButton(
                "+ Add Stock Entry",
                modifier = Modifier.padding(top = 4.dp),
                onClick = { onChange(shop.copy(stock = shop.stock + ShopEntry(items.first().id, price = 0))) },
            )
        }
    }
}

@Composable
private fun ShopEntryRow(entry: ShopEntry, items: List<ItemDef>, onChange: (ShopEntry) -> Unit, onRemove: () -> Unit) {
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
        BasicText("Price:", style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
        InkStepper(entry.price, min = 0, onValueChange = { onChange(entry.copy(price = it)) })
        InkButton("Remove", modifier = Modifier.padding(start = 8.dp), onClick = onRemove)
    }
}
