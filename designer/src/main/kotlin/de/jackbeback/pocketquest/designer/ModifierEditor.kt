package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.AdvSide
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.Flag
import de.jackbeback.pocketquest.core.model.Modifier as StatModifier
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.RollContext
import de.jackbeback.pocketquest.core.model.Skill
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkTextField

private enum class ModifierKind { Add, Mul, Override, Grant, Resist, Roll }

private fun StatModifier.kind(): ModifierKind = when (this) {
    is StatModifier.Add -> ModifierKind.Add
    is StatModifier.Mul -> ModifierKind.Mul
    is StatModifier.Override -> ModifierKind.Override
    is StatModifier.Grant -> ModifierKind.Grant
    is StatModifier.Resist -> ModifierKind.Resist
    is StatModifier.Roll -> ModifierKind.Roll
}

private fun defaultFor(kind: ModifierKind): StatModifier = when (kind) {
    ModifierKind.Add -> StatModifier.Add(Stat.MaxHp, 0)
    ModifierKind.Mul -> StatModifier.Mul(Stat.MaxHp, 1f)
    ModifierKind.Override -> StatModifier.Override(Stat.MaxHp, 0)
    ModifierKind.Grant -> StatModifier.Grant(Flag.Prone)
    ModifierKind.Resist -> StatModifier.Resist(DamageType.Fire, Resistance.Resistant)
    ModifierKind.Roll -> StatModifier.Roll(RollContext.AttackRoll(), AdvSide.Advantage)
}

/**
 * doc20's "type dropdown + inline fields per row" pattern applied to [StatModifier]'s 6 variants —
 * used by Item/Status/Feature's `modifiers` lists and (once this exists) Archetype's
 * `innateModifiers`, rather than authoring the same row-editor once per catalog entry kind.
 */
@Composable
fun ModifierListEditor(modifiers: List<StatModifier>, onChange: (List<StatModifier>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        modifiers.forEachIndexed { index, m ->
            ModifierRow(
                value = m,
                onChange = { updated -> onChange(modifiers.toMutableList().also { it[index] = updated }) },
                onRemove = { onChange(modifiers.filterIndexed { i, _ -> i != index }) },
            )
        }
        InkButton("+ Add Modifier", modifier = Modifier.padding(top = 4.dp), onClick = { onChange(modifiers + defaultFor(ModifierKind.Add)) })
    }
}

@Composable
private fun ModifierRow(value: StatModifier, onChange: (StatModifier) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        InkSelect(
            selected = value.kind(),
            options = ModifierKind.entries,
            label = { it.name },
            onSelect = { onChange(defaultFor(it)) },
            modifier = Modifier.padding(end = 8.dp),
        )
        when (value) {
            is StatModifier.Add -> {
                StatSelect(value.stat) { onChange(value.copy(stat = it)) }
                IntField(value.value) { onChange(value.copy(value = it)) }
            }
            is StatModifier.Mul -> {
                StatSelect(value.stat) { onChange(value.copy(stat = it)) }
                FloatField(value.factor) { onChange(value.copy(factor = it)) }
            }
            is StatModifier.Override -> {
                StatSelect(value.stat) { onChange(value.copy(stat = it)) }
                IntField(value.value) { onChange(value.copy(value = it)) }
            }
            is StatModifier.Grant -> {
                InkSelect(value.flag, Flag.entries, { it.name }, { onChange(value.copy(flag = it)) })
            }
            is StatModifier.Resist -> {
                InkSelect(value.damageType, DamageType.entries, { it.name }, { onChange(value.copy(damageType = it)) }, modifier = Modifier.padding(end = 8.dp))
                InkSelect(value.level, Resistance.entries, { it.name }, { onChange(value.copy(level = it)) })
            }
            is StatModifier.Roll -> {
                RollContextSelect(value.ctx) { onChange(value.copy(ctx = it)) }
                InkSelect(value.side, AdvSide.entries, { it.name }, { onChange(value.copy(side = it)) }, modifier = Modifier.padding(start = 8.dp))
            }
        }
        InkButton("Remove", modifier = Modifier.padding(start = 8.dp), onClick = onRemove)
    }
}

@Composable
private fun StatSelect(selected: Stat, onSelect: (Stat) -> Unit) {
    InkSelect(selected, Stat.entries, { it.name }, onSelect, modifier = Modifier.padding(end = 8.dp))
}

@Composable
private fun IntField(value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    InkTextField(text, onValueChange = { text = it; it.toIntOrNull()?.let(onChange) }, modifier = Modifier.width(60.dp).padding(end = 8.dp))
}

@Composable
private fun FloatField(value: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    InkTextField(text, onValueChange = { text = it; it.toFloatOrNull()?.let(onChange) }, modifier = Modifier.width(60.dp).padding(end = 8.dp))
}

private enum class RollContextKind { AttackRoll, SavingThrow, AbilityCheck }

private fun RollContext.kind(): RollContextKind = when (this) {
    is RollContext.AttackRoll -> RollContextKind.AttackRoll
    is RollContext.SavingThrow -> RollContextKind.SavingThrow
    is RollContext.AbilityCheck -> RollContextKind.AbilityCheck
}

@Composable
private fun RollContextSelect(ctx: RollContext, onChange: (RollContext) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(
            selected = ctx.kind(),
            options = RollContextKind.entries,
            label = { it.name },
            onSelect = { kind ->
                onChange(
                    when (kind) {
                        RollContextKind.AttackRoll -> RollContext.AttackRoll()
                        RollContextKind.SavingThrow -> RollContext.SavingThrow(Ability.Str)
                        RollContextKind.AbilityCheck -> RollContext.AbilityCheck(Skill.Athletics)
                    },
                )
            },
            modifier = Modifier.padding(end = 8.dp),
        )
        when (ctx) {
            is RollContext.AttackRoll -> InkSelect(ctx.vs, listOf<Faction?>(null) + Faction.entries, { it?.name ?: "Any" }, { onChange(ctx.copy(vs = it)) })
            is RollContext.SavingThrow -> InkSelect(ctx.ability, Ability.entries, { it.name }, { onChange(ctx.copy(ability = it)) })
            is RollContext.AbilityCheck -> InkSelect(ctx.skill, Skill.entries, { it.name }, { onChange(ctx.copy(skill = it)) })
        }
    }
}
