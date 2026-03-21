package de.jackbeback.pocketquest.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.game.hint.allHints
import de.jackbeback.pocketquest.ui.battle.SkillUiState
import de.jackbeback.pocketquest.ui.component.ConditionBadge
import de.jackbeback.pocketquest.ui.component.ConditionBadges
import de.jackbeback.pocketquest.ui.component.HintOverlay
import de.jackbeback.pocketquest.ui.component.SkillPanel
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.panels.*

// ── Catalog entry model ───────────────────────────────────────────────────────

private class CatalogEntry(
    val name: String,
    val category: String,
    val content: @Composable () -> Unit,
)

// ── Fake data for previews ────────────────────────────────────────────────────

private val fakeSkills = listOf(
    SkillUiState(id = "MagicMissile",  name = "Magic Missile",  manaCost = 2, spriteKey = "MagicMissile",  range = 4),
    SkillUiState(id = "BasicHeal",     name = "Basic Heal",     manaCost = 3, spriteKey = "BasicHeal",     range = 1),
    SkillUiState(id = "ThornWhip",     name = "Thorn Whip",     manaCost = 0, spriteKey = "ThornWhip",     range = 2),
    SkillUiState(id = "CrimsonArcana", name = "Crimson Arcana", manaCost = 4, spriteKey = "CrimsonArcana", range = 3),
)

// ── Entry definitions ─────────────────────────────────────────────────────────

@Composable
private fun buildEntries(): List<CatalogEntry> = listOf(

    // ── Game Components ───────────────────────────────────────────────────

    CatalogEntry("ConditionBadge", "Game Components") {
        val conditionNames = ConditionType.entries.map { it.name }
        var selectedCondition by remember { mutableStateOf(ConditionType.Burn) }
        var stacks by remember { mutableStateOf(3) }
        CatalogPanel(
            preview = {
                ConditionBadge(type = selectedCondition, stacks = stacks)
            },
            controls = {
                ControlRow("Condition") {
                    DropdownField(
                        selected = selectedCondition.name,
                        options = conditionNames,
                        onSelect = { selectedCondition = ConditionType.valueOf(it) },
                    )
                }
                ControlRow("Stacks (1–5)") {
                    IntSliderRow(value = stacks, range = 1..5, onValueChange = { stacks = it })
                }
            },
        )
    },

    CatalogEntry("ConditionBadges", "Game Components") {
        val allTypes = ConditionType.entries
        val activeStacks = remember { mutableStateMapOf<ConditionType, Int>().also { map ->
            allTypes.forEach { map[it] = 2 }
        }}
        val activeSet = remember { mutableStateSetOf<ConditionType>().also { it.addAll(allTypes) } }
        CatalogPanel(
            preview = {
                ConditionBadges(conditions = allTypes.filter { it in activeSet }.associateWith { activeStacks[it]!! })
            },
            controls = {
                allTypes.forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckboxField(
                            label = type.name,
                            checked = type in activeSet,
                            onCheckedChange = { if (it) activeSet.add(type) else activeSet.remove(type) },
                        )
                        if (type in activeSet) {
                            Spacer(Modifier.weight(1f))
                            Text("×", color = DC.Overlay0, fontSize = 12.sp)
                            IntSliderRow(
                                value = activeStacks[type]!!,
                                range = 1..5,
                                onValueChange = { activeStacks[type] = it },
                                modifier = Modifier.width(180.dp),
                            )
                        }
                    }
                }
            },
        )
    },

    CatalogEntry("SkillPanel", "Game Components") {
        var selectedSkillId by remember { mutableStateOf<String?>(null) }
        var enabled by remember { mutableStateOf(true) }
        var playerMana by remember { mutableStateOf(5) }
        CatalogPanel(
            preview = {
                Box(Modifier.width(340.dp)) {
                    SkillPanel(
                        skills = fakeSkills,
                        selectedSkillId = selectedSkillId,
                        onSkillSelected = { selectedSkillId = it },
                        enabled = enabled,
                        playerMana = playerMana,
                    )
                }
            },
            controls = {
                ControlRow("Enabled") {
                    CheckboxField(label = "enabled", checked = enabled, onCheckedChange = { enabled = it })
                }
                ControlRow("Player Mana (0–10)") {
                    IntSliderRow(value = playerMana, range = 0..10, onValueChange = { playerMana = it })
                }
                ControlRow("Selected Skill") {
                    Text(
                        text = selectedSkillId ?: "none",
                        color = if (selectedSkillId != null) DC.Primary else DC.Overlay0,
                        fontSize = 12.sp,
                    )
                }
            },
        )
    },

    CatalogEntry("HintOverlay", "Game Components") {
        val hintNames = allHints.map { it.id }
        var selectedHintId by remember { mutableStateOf(allHints.first().id) }
        var visible by remember { mutableStateOf(true) }
        val selectedHint = allHints.first { it.id == selectedHintId }
        CatalogPanel(
            preview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DC.Background),
                ) {
                    if (visible) {
                        HintOverlay(
                            hint = selectedHint,
                            onDismiss = { visible = false },
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Hint dismissed", color = DC.Overlay0, fontSize = 13.sp)
                                PrimaryButton(label = "Show again", onClick = { visible = true })
                            }
                        }
                    }
                }
            },
            controls = {
                ControlRow("Hint") {
                    DropdownField(
                        selected = selectedHintId,
                        options = hintNames,
                        onSelect = { selectedHintId = it; visible = true },
                    )
                }
                ControlRow("Visible") {
                    CheckboxField(label = "visible", checked = visible, onCheckedChange = { visible = it })
                }
            },
        )
    },

    // ── Form Fields ───────────────────────────────────────────────────────

    CatalogEntry("DField", "Form Fields") {
        var text by remember { mutableStateOf("Edit me…") }
        var enabled by remember { mutableStateOf(true) }
        var singleLine by remember { mutableStateOf(true) }
        CatalogPanel(
            preview = {
                Box(Modifier.width(280.dp)) {
                    DField(value = text, enabled = enabled, singleLine = singleLine, onValueChange = { text = it })
                }
            },
            controls = {
                ControlRow("Enabled") { CheckboxField(label = "enabled", checked = enabled, onCheckedChange = { enabled = it }) }
                ControlRow("Single Line") { CheckboxField(label = "singleLine", checked = singleLine, onCheckedChange = { singleLine = it }) }
                ControlRow("Current value") { Text(text.ifEmpty { "(empty)" }, color = DC.Subtext0, fontSize = 12.sp) }
            },
        )
    },

    CatalogEntry("IntField", "Form Fields") {
        var value by remember { mutableStateOf(42) }
        CatalogPanel(
            preview = {
                Box(Modifier.width(180.dp)) {
                    IntField(value = value, onValueChange = { value = it })
                }
            },
            controls = {
                ControlRow("Current value") { Text("$value", color = DC.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
        )
    },

    CatalogEntry("FloatField", "Form Fields") {
        var value by remember { mutableStateOf(3.14f) }
        CatalogPanel(
            preview = {
                Box(Modifier.width(180.dp)) {
                    FloatField(value = value, onValueChange = { value = it })
                }
            },
            controls = {
                ControlRow("Current value") { Text("$value", color = DC.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
        )
    },

    CatalogEntry("DropdownField", "Form Fields") {
        val options = listOf("Alpha", "Beta", "Gamma", "Delta", "Epsilon")
        var selected by remember { mutableStateOf(options.first()) }
        CatalogPanel(
            preview = {
                Box(Modifier.width(200.dp)) {
                    DropdownField(selected = selected, options = options, onSelect = { selected = it })
                }
            },
            controls = {
                ControlRow("Selected") { Text(selected, color = DC.Primary, fontSize = 12.sp) }
            },
        )
    },

    CatalogEntry("CheckboxField", "Form Fields") {
        var checked by remember { mutableStateOf(true) }
        var label by remember { mutableStateOf("Enable feature") }
        CatalogPanel(
            preview = {
                CheckboxField(label = label, checked = checked, onCheckedChange = { checked = it })
            },
            controls = {
                ControlRow("Label") { DField(value = label, onValueChange = { label = it }) }
                ControlRow("Checked") { CheckboxField(label = "checked", checked = checked, onCheckedChange = { checked = it }) }
            },
        )
    },

    // ── Buttons ───────────────────────────────────────────────────────────

    CatalogEntry("PrimaryButton", "Buttons") {
        var label by remember { mutableStateOf("Confirm") }
        var clicks by remember { mutableStateOf(0) }
        CatalogPanel(
            preview = { PrimaryButton(label = label, onClick = { clicks++ }) },
            controls = {
                ControlRow("Label") { DField(value = label, onValueChange = { label = it }) }
                ControlRow("Click count") { Text("$clicks", color = DC.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
        )
    },

    CatalogEntry("SecondaryButton", "Buttons") {
        var label by remember { mutableStateOf("Cancel") }
        var clicks by remember { mutableStateOf(0) }
        CatalogPanel(
            preview = { SecondaryButton(label = label, onClick = { clicks++ }) },
            controls = {
                ControlRow("Label") { DField(value = label, onValueChange = { label = it }) }
                ControlRow("Click count") { Text("$clicks", color = DC.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
        )
    },

    CatalogEntry("DangerButton", "Buttons") {
        var label by remember { mutableStateOf("Delete") }
        var clicks by remember { mutableStateOf(0) }
        CatalogPanel(
            preview = { DangerButton(label = label, onClick = { clicks++ }) },
            controls = {
                ControlRow("Label") { DField(value = label, onValueChange = { label = it }) }
                ControlRow("Click count") { Text("$clicks", color = DC.Error, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
        )
    },

    CatalogEntry("ActionChip", "Buttons") {
        var label by remember { mutableStateOf("Export") }
        var clicks by remember { mutableStateOf(0) }
        CatalogPanel(
            preview = { ActionChip(label = label, onClick = { clicks++ }) },
            controls = {
                ControlRow("Label") { DField(value = label, onValueChange = { label = it }) }
                ControlRow("Click count") { Text("$clicks", color = DC.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
        )
    },

    CatalogEntry("SuccessChip", "Buttons") {
        var label by remember { mutableStateOf("Saved!") }
        var clicks by remember { mutableStateOf(0) }
        CatalogPanel(
            preview = { SuccessChip(label = label, onClick = { clicks++ }) },
            controls = {
                ControlRow("Label") { DField(value = label, onValueChange = { label = it }) }
                ControlRow("Click count") { Text("$clicks", color = DC.Green, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            },
        )
    },

    // ── Layout ────────────────────────────────────────────────────────────

    CatalogEntry("EditorSectionTitle", "Layout") {
        val colorOptions = listOf("Blue", "Green", "Yellow", "Red", "Mauve")
        val colorMap = mapOf("Blue" to DC.Blue, "Green" to DC.Green, "Yellow" to DC.Yellow, "Red" to DC.Red, "Mauve" to DC.Mauve)
        var text by remember { mutableStateOf("Section Title") }
        var colorName by remember { mutableStateOf("Blue") }
        CatalogPanel(
            preview = {
                Box(Modifier.width(300.dp)) {
                    EditorSectionTitle(text = text, color = colorMap[colorName]!!)
                }
            },
            controls = {
                ControlRow("Text") { DField(value = text, onValueChange = { text = it }) }
                ControlRow("Color") { DropdownField(selected = colorName, options = colorOptions, onSelect = { colorName = it }) }
            },
        )
    },

    CatalogEntry("EditorCard", "Layout") {
        CatalogPanel(
            preview = {
                Box(Modifier.width(300.dp)) {
                    EditorCard {
                        Text("Card content goes here", color = DC.Text, fontSize = 13.sp)
                        Text("Another row of content", color = DC.Subtext0, fontSize = 11.sp)
                    }
                }
            },
            controls = {
                Text("EditorCard is a static container — no interactive props.", color = DC.Overlay0, fontSize = 11.sp)
            },
        )
    },

    CatalogEntry("CardLabel", "Layout") {
        var text by remember { mutableStateOf("SECTION") }
        CatalogPanel(
            preview = {
                Box(Modifier.width(280.dp)) {
                    CardLabel(text = text)
                }
            },
            controls = {
                ControlRow("Text") { DField(value = text, onValueChange = { text = it }) }
            },
        )
    },

    CatalogEntry("FieldRow", "Layout") {
        var label by remember { mutableStateOf("Name") }
        CatalogPanel(
            preview = {
                Box(Modifier.width(280.dp)) {
                    FieldRow(label = label) {
                        DField(value = "Sample value", onValueChange = {})
                    }
                }
            },
            controls = {
                ControlRow("Label") { DField(value = label, onValueChange = { label = it }) }
            },
        )
    },

    // ── Feedback ──────────────────────────────────────────────────────────

    CatalogEntry("StatusBar", "Feedback") {
        var message by remember { mutableStateOf("Ready") }
        var isError by remember { mutableStateOf(false) }
        var isDirty by remember { mutableStateOf(false) }
        var filePath by remember { mutableStateOf("/home/user/encounters/dungeon.json") }
        var campaignName by remember { mutableStateOf("The Lost Caverns") }
        var campaignDirty by remember { mutableStateOf(false) }
        CatalogPanel(
            preview = {
                Box(Modifier.fillMaxWidth()) {
                    StatusBar(
                        message = message,
                        isError = isError,
                        isDirty = isDirty,
                        filePath = filePath.ifEmpty { null },
                        campaignName = campaignName.ifEmpty { null },
                        campaignLastSavedMs = System.currentTimeMillis(),
                        campaignDirty = campaignDirty,
                    )
                }
            },
            controls = {
                ControlRow("Message") { DField(value = message, onValueChange = { message = it }) }
                ControlRow("File Path") { DField(value = filePath, onValueChange = { filePath = it }) }
                ControlRow("Campaign Name") { DField(value = campaignName, onValueChange = { campaignName = it }) }
                ControlRow("Flags") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckboxField(label = "isError", checked = isError, onCheckedChange = { isError = it })
                        CheckboxField(label = "isDirty", checked = isDirty, onCheckedChange = { isDirty = it })
                        CheckboxField(label = "campaignDirty", checked = campaignDirty, onCheckedChange = { campaignDirty = it })
                    }
                }
            },
        )
    },

    CatalogEntry("EmptyEditorPlaceholder", "Feedback") {
        var message by remember { mutableStateOf("No encounter selected") }
        var hint by remember { mutableStateOf("Open or create an encounter from the sidebar") }
        CatalogPanel(
            preview = {
                Box(Modifier.fillMaxSize()) {
                    EmptyEditorPlaceholder(message = message, hint = hint)
                }
            },
            controls = {
                ControlRow("Message") { DField(value = message, onValueChange = { message = it }) }
                ControlRow("Hint") { DField(value = hint, onValueChange = { hint = it }) }
            },
        )
    },
)

// ── App root ──────────────────────────────────────────────────────────────────

@Composable
fun UiCatalogApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DC.Background,
            surface = DC.Panel,
            primary = DC.Primary,
            onBackground = DC.Text,
            onSurface = DC.Text,
        )
    ) {
        val entries = buildEntries()
        var selectedEntry by remember { mutableStateOf(entries.first()) }

        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left sidebar ──────────────────────────────────────────────
            CatalogSidebar(
                entries = entries,
                selectedEntry = selectedEntry,
                onSelect = { selectedEntry = it },
            )

            // ── Right content panel ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DC.Background),
            ) {
                selectedEntry.content()
            }
        }
    }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────

@Composable
private fun CatalogSidebar(
    entries: List<CatalogEntry>,
    selectedEntry: CatalogEntry,
    onSelect: (CatalogEntry) -> Unit,
) {
    val grouped = entries.groupBy { it.category }

    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(DC.Panel)
            .border(width = 1.dp, color = DC.PanelBorder, shape = RoundedCornerShape(0.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DC.Mantle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("UI Catalog", color = DC.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(color = DC.PanelBorder)

        grouped.forEach { (category, categoryEntries) ->
            // Category header
            Text(
                text = category.uppercase(),
                color = DC.Overlay0,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            )

            categoryEntries.forEach { entry ->
                val isSelected = entry === selectedEntry
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) DC.Selected else Color.Transparent)
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = entry.name,
                        color = if (isSelected) DC.Primary else DC.Subtext1,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Right panel scaffolding ───────────────────────────────────────────────────

/**
 * Standard layout for a catalog entry's right panel.
 * - Top: component name + category chip
 * - Middle: preview area (fixed height 240dp, centered)
 * - Bottom: scrollable controls section
 */
@Composable
private fun CatalogPanel(
    preview: @Composable BoxScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(DC.Background),
            contentAlignment = Alignment.Center,
            content = preview,
        )

        HorizontalDivider(color = DC.PanelBorder)

        // Controls area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 280.dp)
                .background(DC.Mantle)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "CONTROLS",
                color = DC.Overlay0,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            controls()
        }
    }
}

// ── Control helper composables ────────────────────────────────────────────────

@Composable
private fun ControlRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = DC.Subtext0,
            fontSize = 11.sp,
            modifier = Modifier.width(130.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun IntSliderRow(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = DC.Primary,
                activeTrackColor = DC.Primary,
                inactiveTrackColor = DC.Surface1,
            ),
        )
        Text(
            text = "$value",
            color = DC.Text,
            fontSize = 12.sp,
            modifier = Modifier.width(28.dp),
        )
    }
}
