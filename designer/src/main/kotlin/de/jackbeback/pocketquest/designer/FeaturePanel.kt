package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
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
import de.jackbeback.pocketquest.core.model.FeatureDef
import de.jackbeback.pocketquest.core.model.FeatureId
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkTextField
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/**
 * doc20's Feature editor — simplest of the four, mostly reuses [ModifierListEditor] and the
 * action-multi-select pattern [ArchetypePanel.kt]'s `ActionToggle` proved first. damageSteps/
 * healSteps deferred, same reasoning as every other editor this pass.
 */
@Composable
fun FeaturePanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<FeatureId?>(catalog.features.keys.firstOrNull()) }

    fun updateFeature(id: FeatureId, transform: (FeatureDef) -> FeatureDef) {
        val current = catalog.features[id] ?: return
        onCatalogChange(catalog.copy(features = catalog.features + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("FEATURES")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.features.values.toList()) { feature ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = feature.id }
                            .background(if (feature.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(feature.name, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Feature",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.features.size + 1
                    while (FeatureId("feature$n") in catalog.features) n++
                    val id = FeatureId("feature$n")
                    onCatalogChange(catalog.copy(features = catalog.features + (id to FeatureDef(id, "New Feature $n"))))
                    selectedId = id
                },
            )
        }

        val feature = selectedId?.let { catalog.features[it] }
        if (feature != null) {
            FeatureEditor(
                feature = feature,
                catalog = catalog,
                onChange = { updated -> updateFeature(feature.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(features = catalog.features - feature.id))
                    selectedId = catalog.features.keys.firstOrNull { it != feature.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No feature selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun FeatureEditor(feature: FeatureDef, catalog: Catalog, onChange: (FeatureDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove Feature", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(feature.name, onValueChange = { onChange(feature.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("GRANTS ACTIONS") }
        if (catalog.actions.isEmpty()) {
            BasicText("No actions defined in this catalog.", style = TextStyle(color = INK_FAINT, fontSize = 12.sp))
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                catalog.actions.values.forEach { action ->
                    ActionToggle(
                        label = action.name,
                        has = action.id in feature.grantsActions,
                        onToggle = {
                            val updated = if (action.id in feature.grantsActions) feature.grantsActions - action.id else feature.grantsActions + action.id
                            onChange(feature.copy(grantsActions = updated))
                        },
                    )
                }
            }
        }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("MODIFIERS") }
        ModifierListEditor(feature.modifiers, onChange = { onChange(feature.copy(modifiers = it)) })
    }
}
