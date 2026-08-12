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
import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.Catalog
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
 * doc16's 7-editor scope table names Archetype as one of the desktop designer's editors. Core
 * combat stats, which catalog-defined actions it has (including "move" — an archetype with no
 * actions at all can't even move, found by actually trying to playtest one), and now
 * `innateModifiers` via [ModifierListEditor] once that existed for Item/Status/Feature.
 * `innateDamageSteps`/`innateHealSteps` stay deferred — doc18's damage-pipeline mechanic is niche/
 * rarely-authored, same scoping call made in every other editor this pass.
 */
@Composable
fun ArchetypePanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<ArchetypeId?>(catalog.archetypes.keys.firstOrNull()) }

    fun updateArchetype(id: ArchetypeId, transform: (Archetype) -> Archetype) {
        val current = catalog.archetypes[id] ?: return
        onCatalogChange(catalog.copy(archetypes = catalog.archetypes + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("ARCHETYPES")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.archetypes.values.toList()) { archetype ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = archetype.id }
                            .background(if (archetype.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(archetype.name, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Archetype",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.archetypes.size + 1
                    while (ArchetypeId("archetype$n") in catalog.archetypes) n++
                    val id = ArchetypeId("archetype$n")
                    val archetype = Archetype(
                        id = id, name = "New Archetype $n",
                        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
                        baseMaxHp = 10, baseAc = 10, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 0,
                        actions = listOfNotNull(MOVE_ACTION_ID.takeIf { it in catalog.actions }),
                    )
                    onCatalogChange(catalog.copy(archetypes = catalog.archetypes + (id to archetype)))
                    selectedId = id
                },
            )
        }

        val archetype = selectedId?.let { catalog.archetypes[it] }
        if (archetype != null) {
            ArchetypeEditor(
                archetype = archetype,
                catalog = catalog,
                onChange = { updated -> updateArchetype(archetype.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(archetypes = catalog.archetypes - archetype.id))
                    selectedId = catalog.archetypes.keys.firstOrNull { it != archetype.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No archetype selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun ArchetypeEditor(archetype: Archetype, catalog: Catalog, onChange: (Archetype) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove Archetype", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(archetype.name, onValueChange = { onChange(archetype.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("DESCRIPTION") }
        // docs/26-character-detail-card.md: flavor/lore text shown as the Inspect card's top banner.
        InkTextField(
            archetype.description,
            onValueChange = { onChange(archetype.copy(description = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
        )

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("ACTIONS") }
        if (catalog.actions.isEmpty()) {
            BasicText("No actions defined in this catalog.", style = TextStyle(color = DANGER, fontSize = 12.sp))
        } else {
            // Same "many items, no wrap" shape that overflowed the top tab bar — scrolls instead
            // of pushing later toggles off-screen once enough actions exist.
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                catalog.actions.values.forEach { action ->
                    ActionToggle(
                        label = action.name,
                        has = action.id in archetype.actions,
                        onToggle = {
                            val updated = if (action.id in archetype.actions) archetype.actions - action.id else archetype.actions + action.id
                            onChange(archetype.copy(actions = updated))
                        },
                    )
                }
            }
        }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("SPRITE") }
        var justImportedSprite by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val spriteOptions = AssetManifest.characterSprites
            val currentSprite = spriteOptions.find { it.id == archetype.spriteId }
            InkSelect(
                selected = currentSprite,
                options = listOf<ManifestAsset?>(null) + spriteOptions,
                label = { it?.id ?: "None (colored circle)" },
                onSelect = { asset -> onChange(archetype.copy(spriteId = asset?.id)) },
                modifier = Modifier.width(220.dp),
                itemContent = { asset ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val bmp = asset?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
                        if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(end = 6.dp))
                        BasicText(asset?.id ?: "None (colored circle)", style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                },
            )
            if (currentSprite != null) {
                val bmp = remember(currentSprite.file) { SpriteLoader.load(PROPS_DIR + currentSprite.file) }
                if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(start = 8.dp))
            }
            InkButton(
                "Import…",
                modifier = Modifier.padding(start = 8.dp),
                onClick = {
                    val source = chooseImageFile() ?: return@InkButton
                    val imported = AssetManifest.importSprite(source, kind = "character") ?: return@InkButton
                    onChange(archetype.copy(spriteId = imported.id))
                    justImportedSprite = true
                },
            )
        }
        // docs/28: this list updates live, but Playtest reads through :ui's packaged Compose
        // Resources, baked in at build time — it won't see a just-imported file until :designer
        // itself is fully restarted. Only shown right after an import, not permanently.
        if (justImportedSprite) {
            InkLabel("Imported — restart :designer:run to see it in Playtest.", modifier = Modifier.padding(top = 4.dp))
        }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("ABILITY SCORES") }
        val abilities = archetype.abilities
        AbilityRow("STR", abilities.str) { onChange(archetype.copy(abilities = abilities.copy(str = it))) }
        AbilityRow("DEX", abilities.dex) { onChange(archetype.copy(abilities = abilities.copy(dex = it))) }
        AbilityRow("CON", abilities.con) { onChange(archetype.copy(abilities = abilities.copy(con = it))) }
        AbilityRow("INT", abilities.int) { onChange(archetype.copy(abilities = abilities.copy(int = it))) }
        AbilityRow("WIS", abilities.wis) { onChange(archetype.copy(abilities = abilities.copy(wis = it))) }
        AbilityRow("CHA", abilities.cha) { onChange(archetype.copy(abilities = abilities.copy(cha = it))) }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("COMBAT STATS") }
        StatRow("Max HP", archetype.baseMaxHp) { onChange(archetype.copy(baseMaxHp = it)) }
        StatRow("Armor class", archetype.baseAc) { onChange(archetype.copy(baseAc = it)) }
        StatRow("Speed (tiles)", archetype.speedTiles) { onChange(archetype.copy(speedTiles = it)) }
        StatRow("Max AP", archetype.baseMaxAp) { onChange(archetype.copy(baseMaxAp = it)) }
        StatRow("Max mana", archetype.baseMaxMana) { onChange(archetype.copy(baseMaxMana = it)) }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("INNATE MODIFIERS") }
        ModifierListEditor(archetype.innateModifiers, onChange = { onChange(archetype.copy(innateModifiers = it)) })

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("AI PROFILE") }
        // "standard" (the zero-config default — see AiProfile.kt/Catalog.aiProfileOrDefault) is
        // always offered even if nothing authored it explicitly in this catalog yet.
        val profileOptions = (listOf(AiProfileId("standard")) + catalog.aiProfiles.keys).distinct()
        InkSelect(
            archetype.aiProfile,
            profileOptions,
            { catalog.aiProfiles[it]?.name ?: it.raw },
            { onChange(archetype.copy(aiProfile = it)) },
        )
    }
}

/**
 * Shared with [FeaturePanel.kt] — a feature's `grantsActions` needs the identical picker. Takes the
 * action's editable [label] (its `.name`), not [actionId] itself — an action's id is auto-generated
 * once ("action2") and never changes, so displaying `actionId.raw` here made every renamed action
 * look like the rename never took effect anywhere except the Actions tab's own list.
 */
@Composable
fun ActionToggle(label: String, has: Boolean, onToggle: () -> Unit) {
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

@Composable
private fun AbilityRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        BasicText(label, style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.width(40.dp))
        InkStepper(value, min = 1, onValueChange = onValueChange)
    }
}

@Composable
private fun StatRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        BasicText(label, style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.width(120.dp))
        InkStepper(value, min = 0, onValueChange = onValueChange)
    }
}
