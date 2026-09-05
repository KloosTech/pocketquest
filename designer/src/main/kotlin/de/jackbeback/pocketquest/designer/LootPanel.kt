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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.LootDef
import de.jackbeback.pocketquest.core.model.LootEntry
import de.jackbeback.pocketquest.core.model.LootId
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.rules.pickWeighted
import de.jackbeback.pocketquest.ui.LootReel
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
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * docs/37-lootable-containers.md's Loot editor: reusable lootable-container definitions — a name,
 * closed/open sprite (the same `AssetManifest.placeableProps` picker the Map editor's Prop tool
 * already uses), and a loot table (`LootEntryRow`, moved here unchanged from `EncounterPanel.kt` —
 * the exact item+chance row the old per-encounter loot list used, now attached to a reusable
 * container instead of a single encounter).
 */
@Composable
fun LootPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<LootId?>(catalog.loot.keys.firstOrNull()) }

    fun updateLoot(id: LootId, transform: (LootDef) -> LootDef) {
        val current = catalog.loot[id] ?: return
        onCatalogChange(catalog.copy(loot = catalog.loot + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("LOOT CONTAINERS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.loot.values.toList()) { loot ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = loot.id }
                            .background(if (loot.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(loot.name.ifBlank { loot.id.raw }, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Loot",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.loot.size + 1
                    while (LootId("loot$n") in catalog.loot) n++
                    val id = LootId("loot$n")
                    onCatalogChange(catalog.copy(loot = catalog.loot + (id to LootDef(id, "New Loot $n"))))
                    selectedId = id
                },
            )
        }

        val loot = selectedId?.let { catalog.loot[it] }
        if (loot != null) {
            LootEditor(
                loot = loot,
                catalog = catalog,
                onChange = { updated -> updateLoot(loot.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(loot = catalog.loot - loot.id))
                    selectedId = catalog.loot.keys.firstOrNull { it != loot.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No loot container selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun LootEditor(loot: LootDef, catalog: Catalog, onChange: (LootDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(loot.name, onValueChange = { onChange(loot.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("CLOSED SPRITE") }
        SpritePicker(loot.closedSprite, onSelect = { onChange(loot.copy(closedSprite = it)) })

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("OPEN SPRITE") }
        SpritePicker(loot.openSprite, onSelect = { onChange(loot.copy(openSprite = it)) })

        Box(modifier = Modifier.padding(top = 16.dp)) {
            InkLabel("LOOT TABLE (one weighted pick across the whole table — weights under 100% leave a chance of nothing)")
        }
        val items = catalog.items.values.toList()
        loot.table.forEachIndexed { index, entry ->
            LootEntryRow(
                entry = entry,
                items = items,
                onChange = { updated -> onChange(loot.copy(table = loot.table.toMutableList().also { it[index] = updated })) },
                onRemove = { onChange(loot.copy(table = loot.table.filterIndexed { i, _ -> i != index })) },
            )
        }
        if (items.isEmpty()) {
            BasicText("No items in the working catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp), modifier = Modifier.padding(top = 4.dp))
        } else {
            InkButton(
                "+ Add Loot Entry",
                modifier = Modifier.padding(top = 4.dp),
                onClick = { onChange(loot.copy(table = loot.table + LootEntry(items.first().id))) },
            )
        }

        // docs/38-loot-reveal-screen.md: :designer's own Playtest tab starts an encounter straight
        // through `core.rules.content.startEncounter`, bypassing `RunState`/`finishEncounter`
        // entirely — the real loot-reveal screen is never reachable from it. This is the only place
        // an author can actually see a container's roll+reel without running the full `:app`.
        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("TEST (simulates opening this chest — no items are actually granted)") }
        var testRoll by remember(loot.id) { mutableStateOf<Pair<Long, ItemId?>?>(null) }
        InkButton(
            "Test Open",
            modifier = Modifier.padding(top = 4.dp),
            onClick = {
                val seed = Random.nextLong()
                val (_, item) = RngState(seed = seed).pickWeighted(loot.table)
                testRoll = seed to item
            },
        )
        testRoll?.let { (trigger, item) ->
            val itemIcons = remember(loot.table, catalog) {
                loot.table.mapNotNull { entry ->
                    val iconId = catalog.items[entry.item]?.icon ?: return@mapNotNull null
                    val meta = AssetManifest.prop(iconId) ?: return@mapNotNull null
                    val bmp = SpriteLoader.load(PROPS_DIR + meta.file) ?: return@mapNotNull null
                    entry.item to bmp
                }.toMap()
            }
            LootReel(
                table = loot.table,
                result = item,
                trigger = trigger,
                catalog = catalog,
                itemIcons = itemIcons,
                onSettled = {},
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SpritePicker(selectedId: String?, onSelect: (String?) -> Unit) {
    // Chest sprites pick from the same pool ArchetypePanel's own SPRITE field does (character-kind
    // manifest entries), not the prop-placement list — a chest reads as a "thing on the board" the
    // same way a character does, not furniture/scenery.
    val options = listOf<ManifestAsset?>(null) + AssetManifest.characterSprites
    val current = options.find { it?.id == selectedId }
    var justImported by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(
            selected = current,
            options = options,
            label = { it?.let { a -> "${a.id} (${a.tilesW}x${a.tilesH})" } ?: "(none)" },
            onSelect = { onSelect(it?.id) },
            modifier = Modifier.width(200.dp),
        )
        if (current != null) {
            val bmp = remember(current.file) { SpriteLoader.load(PROPS_DIR + current.file) }
            if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(start = 8.dp))
        }
        InkButton(
            "Import…",
            modifier = Modifier.padding(start = 8.dp),
            onClick = {
                val source = chooseImageFile() ?: return@InkButton
                // Same kind = "character" ArchetypePanel's own SPRITE field imports as — this list
                // reads from AssetManifest.characterSprites above, so it has to land in that pool.
                val imported = AssetManifest.importSprite(source, kind = "character") ?: return@InkButton
                onSelect(imported.id)
                justImported = true
            },
        )
    }
    // docs/28: :designer's own list updates live, but Playtest reads through :ui's packaged Compose
    // Resources, baked in at build time — same "restart to see it" caveat ArchetypePanel's own
    // Import button already carries.
    if (justImported) {
        InkLabel("Imported — restart :designer:run to see it in Playtest.", modifier = Modifier.padding(top = 4.dp))
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
        // docs/38-loot-reveal-screen.md: a single weighted pick across the whole table now, not an
        // independent chance per entry — the % here is this entry's share of the pull, not its own
        // odds of firing regardless of the others.
        BasicText("Weight:", style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.padding(end = 8.dp))
        InkStepper(
            (entry.weight * 100).roundToInt(),
            min = 0,
            onValueChange = { onChange(entry.copy(weight = it.coerceIn(0, 100) / 100.0)) },
        )
        BasicText("%", style = TextStyle(color = INK_FAINT, fontSize = 12.sp), modifier = Modifier.padding(start = 4.dp, end = 8.dp))
        InkButton("Remove", onClick = onRemove)
    }
}
