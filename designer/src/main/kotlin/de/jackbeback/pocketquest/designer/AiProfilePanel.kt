package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import de.jackbeback.pocketquest.core.model.AiActionCategory
import de.jackbeback.pocketquest.core.model.AiCondition
import de.jackbeback.pocketquest.core.model.AiGoal
import de.jackbeback.pocketquest.core.model.AiProfileDef
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.AiTargetPreference
import de.jackbeback.pocketquest.core.model.AiTier
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkTextField
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/**
 * docs/21-ai-behavior-spec.md's AI Profile editor. A profile is an ORDERED list of tiers — order is
 * the entire semantic backbone (first matching tier wins), so unlike every other list editor in
 * this module, tiers get explicit reorder controls, not just add/remove.
 */
@Composable
fun AiProfilePanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<AiProfileId?>(catalog.aiProfiles.keys.firstOrNull()) }

    fun updateProfile(id: AiProfileId, transform: (AiProfileDef) -> AiProfileDef) {
        val current = catalog.aiProfiles[id] ?: return
        onCatalogChange(catalog.copy(aiProfiles = catalog.aiProfiles + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("AI PROFILES")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.aiProfiles.values.toList()) { profile ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = profile.id }
                            .background(if (profile.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(profile.name, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Profile",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.aiProfiles.size + 1
                    while (AiProfileId("profile$n") in catalog.aiProfiles) n++
                    val id = AiProfileId("profile$n")
                    onCatalogChange(catalog.copy(aiProfiles = catalog.aiProfiles + (id to AiProfileDef(id, "New Profile $n"))))
                    selectedId = id
                },
            )
        }

        val profile = selectedId?.let { catalog.aiProfiles[it] }
        if (profile != null) {
            AiProfileEditor(
                profile = profile,
                catalog = catalog,
                onChange = { updated -> updateProfile(profile.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(aiProfiles = catalog.aiProfiles - profile.id))
                    selectedId = catalog.aiProfiles.keys.firstOrNull { it != profile.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No AI profile selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun AiProfileEditor(profile: AiProfileDef, catalog: Catalog, onChange: (AiProfileDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove Profile", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(profile.name, onValueChange = { onChange(profile.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("TIERS — checked top to bottom, first match wins") }
        profile.tiers.forEachIndexed { index, tier ->
            TierRow(
                tier = tier,
                catalog = catalog,
                onChange = { updated -> onChange(profile.copy(tiers = profile.tiers.toMutableList().also { it[index] = updated })) },
                onRemove = { onChange(profile.copy(tiers = profile.tiers.filterIndexed { i, _ -> i != index })) },
                onMoveUp = {
                    if (index > 0) {
                        val reordered = profile.tiers.toMutableList()
                        reordered[index] = profile.tiers[index - 1]
                        reordered[index - 1] = profile.tiers[index]
                        onChange(profile.copy(tiers = reordered))
                    }
                },
                onMoveDown = {
                    if (index < profile.tiers.lastIndex) {
                        val reordered = profile.tiers.toMutableList()
                        reordered[index] = profile.tiers[index + 1]
                        reordered[index + 1] = profile.tiers[index]
                        onChange(profile.copy(tiers = reordered))
                    }
                },
            )
        }
        InkButton(
            "+ Add Tier",
            modifier = Modifier.padding(top = 4.dp),
            onClick = { onChange(profile.copy(tiers = profile.tiers + AiTier(AiCondition.Always, AiGoal.UseAction(targetPreference = AiTargetPreference.LowestHpPercent)))) },
        )
    }
}

@Composable
private fun TierRow(tier: AiTier, catalog: Catalog, onChange: (AiTier) -> Unit, onRemove: () -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp).border(1.dp, INK_FAINT).padding(8.dp),
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            InkButton("▲", onClick = onMoveUp)
            InkButton("▼", modifier = Modifier.padding(top = 2.dp), onClick = onMoveDown)
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkLabel("IF", modifier = Modifier.padding(end = 8.dp))
                ConditionEditor(tier.condition, catalog) { onChange(tier.copy(condition = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                InkLabel("THEN", modifier = Modifier.padding(end = 8.dp))
                GoalEditor(tier.goal) { onChange(tier.copy(goal = it)) }
            }
        }
        InkButton("Remove", modifier = Modifier.padding(start = 8.dp), onClick = onRemove)
    }
}

private enum class ConditionKind { SelfHpBelow, AnyAllyHpBelow, AnyEnemyHpBelow, IsTaunted, HasStatus, EnemyCountInRange, Always }

private fun AiCondition.kind(): ConditionKind = when (this) {
    is AiCondition.SelfHpBelow -> ConditionKind.SelfHpBelow
    is AiCondition.AnyAllyHpBelow -> ConditionKind.AnyAllyHpBelow
    is AiCondition.AnyEnemyHpBelow -> ConditionKind.AnyEnemyHpBelow
    AiCondition.IsTaunted -> ConditionKind.IsTaunted
    is AiCondition.HasStatus -> ConditionKind.HasStatus
    is AiCondition.EnemyCountInRange -> ConditionKind.EnemyCountInRange
    AiCondition.Always -> ConditionKind.Always
}

private fun defaultCondition(kind: ConditionKind, catalog: Catalog): AiCondition = when (kind) {
    ConditionKind.SelfHpBelow -> AiCondition.SelfHpBelow(50)
    ConditionKind.AnyAllyHpBelow -> AiCondition.AnyAllyHpBelow(50)
    ConditionKind.AnyEnemyHpBelow -> AiCondition.AnyEnemyHpBelow(50)
    ConditionKind.IsTaunted -> AiCondition.IsTaunted
    ConditionKind.HasStatus -> AiCondition.HasStatus(catalog.statuses.keys.firstOrNull() ?: StatusId(""))
    ConditionKind.EnemyCountInRange -> AiCondition.EnemyCountInRange(range = 3, atLeast = 2)
    ConditionKind.Always -> AiCondition.Always
}

@Composable
private fun ConditionEditor(condition: AiCondition, catalog: Catalog, onChange: (AiCondition) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(condition.kind(), ConditionKind.entries, { it.name }, { onChange(defaultCondition(it, catalog)) }, modifier = Modifier.padding(end = 8.dp))
        when (condition) {
            is AiCondition.SelfHpBelow -> PercentField(condition.percent) { onChange(condition.copy(percent = it)) }
            is AiCondition.AnyAllyHpBelow -> PercentField(condition.percent) { onChange(condition.copy(percent = it)) }
            is AiCondition.AnyEnemyHpBelow -> PercentField(condition.percent) { onChange(condition.copy(percent = it)) }
            AiCondition.IsTaunted -> Unit
            is AiCondition.HasStatus -> {
                val statuses = catalog.statuses.values.toList()
                if (statuses.isEmpty()) {
                    InkLabel("no statuses yet")
                } else {
                    InkSelect(
                        statuses.find { it.id == condition.status } ?: statuses.first(),
                        statuses,
                        { it.name },
                        { onChange(condition.copy(status = it.id)) },
                    )
                }
            }
            is AiCondition.EnemyCountInRange -> {
                InkLabel("range", modifier = Modifier.padding(end = 4.dp))
                IntField(condition.range) { onChange(condition.copy(range = it)) }
                InkLabel("at least", modifier = Modifier.padding(start = 8.dp, end = 4.dp))
                IntField(condition.atLeast) { onChange(condition.copy(atLeast = it)) }
            }
            AiCondition.Always -> Unit
        }
    }
}

private enum class GoalKind { UseAction, Retreat, Approach }

private fun AiGoal.kind(): GoalKind = when (this) {
    is AiGoal.UseAction -> GoalKind.UseAction
    AiGoal.Retreat -> GoalKind.Retreat
    AiGoal.Approach -> GoalKind.Approach
}

private fun defaultGoal(kind: GoalKind): AiGoal = when (kind) {
    GoalKind.UseAction -> AiGoal.UseAction(targetPreference = AiTargetPreference.LowestHpPercent)
    GoalKind.Retreat -> AiGoal.Retreat
    GoalKind.Approach -> AiGoal.Approach
}

@Composable
private fun GoalEditor(goal: AiGoal, onChange: (AiGoal) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(goal.kind(), GoalKind.entries, { it.name }, { onChange(defaultGoal(it)) }, modifier = Modifier.padding(end = 8.dp))
        if (goal is AiGoal.UseAction) {
            InkSelect(goal.category, listOf<AiActionCategory?>(null) + AiActionCategory.entries, { it?.name ?: "Any" }, { onChange(goal.copy(category = it)) }, modifier = Modifier.padding(end = 8.dp))
            InkSelect(goal.targetPreference, AiTargetPreference.entries, { it.name }, { onChange(goal.copy(targetPreference = it)) })
        }
    }
}

@Composable
private fun PercentField(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IntField(value, onChange)
        InkLabel("%", modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun IntField(value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    InkTextField(text, onValueChange = { text = it; it.toIntOrNull()?.let(onChange) }, modifier = Modifier.width(50.dp))
}
