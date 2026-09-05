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
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusDef
import de.jackbeback.pocketquest.core.model.StatusId
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
 * doc20's Status editor. damageSteps/healSteps deferred, same reasoning as every other editor this
 * pass (doc18's pipeline mechanic is niche/rarely-authored).
 */
@Composable
fun StatusPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<StatusId?>(catalog.statuses.keys.firstOrNull()) }

    fun updateStatus(id: StatusId, transform: (StatusDef) -> StatusDef) {
        val current = catalog.statuses[id] ?: return
        onCatalogChange(catalog.copy(statuses = catalog.statuses + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("STATUSES")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.statuses.values.toList()) { status ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = status.id }
                            .background(if (status.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(status.name, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Status",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.statuses.size + 1
                    while (StatusId("status$n") in catalog.statuses) n++
                    val id = StatusId("status$n")
                    onCatalogChange(catalog.copy(statuses = catalog.statuses + (id to StatusDef(id, "New Status $n", StackPolicy.Refresh))))
                    selectedId = id
                },
            )
        }

        val status = selectedId?.let { catalog.statuses[it] }
        if (status != null) {
            StatusEditor(
                status = status,
                catalog = catalog,
                onChange = { updated -> updateStatus(status.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(statuses = catalog.statuses - status.id))
                    selectedId = catalog.statuses.keys.firstOrNull { it != status.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No status selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun StatusEditor(status: StatusDef, catalog: Catalog, onChange: (StatusDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove Status", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(status.name, onValueChange = { onChange(status.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        // docs/40-status-icons.md: drawn in a row above any entity carrying this status.
        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("ICON") }
        StatusIconPicker(status.icon, onSelect = { onChange(status.copy(icon = it)) })

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("STACK POLICY (how a fresh application merges with an existing stack)") }
        InkSelect(status.stackPolicy, StackPolicy.entries, { it.name }, { onChange(status.copy(stackPolicy = it)) })

        // docs/42-status-stack-scaling.md: a different axis from STACK POLICY above — this fires
        // every turn regardless of reapplication, not just when the status is applied again.
        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("STACK DECAY (lost at the end of the bearer's own turn — 0 = never decays)") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkStepper(status.decayStacksPerTurn, min = 0, onValueChange = { onChange(status.copy(decayStacksPerTurn = it)) })
            InkLabel(" per turn", modifier = Modifier.padding(start = 4.dp))
        }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("MODIFIERS") }
        ModifierListEditor(status.modifiers, onChange = { onChange(status.copy(modifiers = it)) })

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("ON TURN START") }
        EffectTemplateListEditor(status.onTurnStart, catalog, onChange = { onChange(status.copy(onTurnStart = it)) })
    }
}

/** docs/40-status-icons.md: same shape as `LootPanel.kt`'s closed/open `SpritePicker`/`ItemPanel.kt`'s `ItemIconPicker`, pointed at `AssetManifest.statusSprites` (`kind = "status"`). */
@Composable
private fun StatusIconPicker(selectedId: String?, onSelect: (String?) -> Unit) {
    val options = listOf<ManifestAsset?>(null) + AssetManifest.statusSprites
    val current = options.find { it?.id == selectedId }
    var justImported by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(
            selected = current,
            options = options,
            label = { it?.id ?: "(none)" },
            onSelect = { onSelect(it?.id) },
            modifier = Modifier.width(200.dp),
            itemContent = { asset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val bmp = asset?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
                    if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(end = 6.dp))
                    BasicText(asset?.id ?: "(none)", style = TextStyle(color = INK, fontSize = 13.sp))
                }
            },
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
                val imported = AssetManifest.importSprite(source, kind = "status") ?: return@InkButton
                onSelect(imported.id)
                justImported = true
            },
        )
    }
    if (justImported) {
        InkLabel("Imported — restart :designer:run to see it in Playtest.", modifier = Modifier.padding(top = 4.dp))
    }
}
