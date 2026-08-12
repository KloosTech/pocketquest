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
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.DamageTag
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkTextField

private enum class EffectKind { DealDamage, ApplyStatus, RollAttack, RollSave, Push, Teleport, SpawnEntity, DestroyEntity, Heal }

private fun EffectTemplate.kind(): EffectKind = when (this) {
    is EffectTemplate.DealDamage -> EffectKind.DealDamage
    is EffectTemplate.ApplyStatus -> EffectKind.ApplyStatus
    is EffectTemplate.RollAttack -> EffectKind.RollAttack
    is EffectTemplate.RollSave -> EffectKind.RollSave
    is EffectTemplate.Push -> EffectKind.Push
    is EffectTemplate.Teleport -> EffectKind.Teleport
    is EffectTemplate.SpawnEntity -> EffectKind.SpawnEntity
    is EffectTemplate.DestroyEntity -> EffectKind.DestroyEntity
    is EffectTemplate.Heal -> EffectKind.Heal
}

private fun defaultFor(kind: EffectKind, catalog: Catalog): EffectTemplate = when (kind) {
    EffectKind.DealDamage -> EffectTemplate.DealDamage(Ref.EachTarget, 0, DamageType.Bludgeoning)
    EffectKind.ApplyStatus -> EffectTemplate.ApplyStatus(Ref.EachTarget, catalog.statuses.keys.firstOrNull() ?: StatusId(""), 1, Expiry.Permanent)
    EffectKind.RollAttack -> EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, 0, damage = DiceSpec(1, 6), damageType = DamageType.Bludgeoning)
    EffectKind.RollSave -> EffectTemplate.RollSave(Ref.EachTarget, Ability.Str, 10)
    EffectKind.Push -> EffectTemplate.Push(Ref.EachTarget, Ref.Caster, 1)
    EffectKind.Teleport -> EffectTemplate.Teleport(Ref.Caster)
    EffectKind.SpawnEntity -> EffectTemplate.SpawnEntity(catalog.archetypes.keys.firstOrNull() ?: ArchetypeId(""), Faction.Enemy, Controller.Ai(AiProfileId("standard")))
    EffectKind.DestroyEntity -> EffectTemplate.DestroyEntity(Ref.EachTarget)
    EffectKind.Heal -> EffectTemplate.Heal(Ref.EachTarget, 0)
}

/**
 * doc20's "type dropdown + inline fields per row" pattern applied to [EffectTemplate]'s 9 variants
 * — Action's `effects` and Status's `onTurnStart` both use this. [RollSave] recurses into two
 * nested lists (`onSuccess`/`onFail`); nothing else does, so recursion stays bounded in practice.
 *
 * [Expiry] is deliberately only offered as Permanent/OnConcentrationLost here — `EndOfTurnOf`/
 * `StartOfTurnOf`/`EndOfRound` need a real `EntityId`/round number that doesn't exist at authoring
 * time (`EffectTemplateInstantiate.kt` passes `ApplyStatus.expiry` straight through unresolved), so
 * exposing them here would only let someone author a status that expires against a meaningless
 * hardcoded id. Those three stay handler-constructed-only, same as before this editor existed.
 */
@Composable
fun EffectTemplateListEditor(effects: List<EffectTemplate>, catalog: Catalog, onChange: (List<EffectTemplate>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        effects.forEachIndexed { index, effect ->
            EffectRow(
                value = effect,
                catalog = catalog,
                onChange = { updated -> onChange(effects.toMutableList().also { it[index] = updated }) },
                onRemove = { onChange(effects.filterIndexed { i, _ -> i != index }) },
            )
        }
        InkButton("+ Add Effect", modifier = Modifier.padding(top = 4.dp), onClick = { onChange(effects + defaultFor(EffectKind.DealDamage, catalog)) })
    }
}

@Composable
private fun EffectRow(value: EffectTemplate, catalog: Catalog, onChange: (EffectTemplate) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkSelect(
                selected = value.kind(),
                options = EffectKind.entries,
                label = { it.name },
                onSelect = { onChange(defaultFor(it, catalog)) },
                modifier = Modifier.padding(end = 8.dp),
            )
            InkButton("Remove", onClick = onRemove)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            when (value) {
                is EffectTemplate.DealDamage -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    IntField(value.amount) { onChange(value.copy(amount = it)) }
                    InkSelect(value.damageType, DamageType.entries, { it.name }, { onChange(value.copy(damageType = it)) }, modifier = Modifier.padding(start = 8.dp))
                }
                is EffectTemplate.ApplyStatus -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    StatusSelect(value.status, catalog) { onChange(value.copy(status = it)) }
                    IntField(value.stacks, label = "stacks") { onChange(value.copy(stacks = it)) }
                    ExpirySelect(value.expiry) { onChange(value.copy(expiry = it)) }
                }
                is EffectTemplate.RollAttack -> {
                    RefPicker(value.attacker, { onChange(value.copy(attacker = it)) }, modifier = Modifier.padding(end = 8.dp))
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    // docs/22-dice-roll-ui-and-ability-checks.md: the attacker's own ability
                    // modifier now drives the roll — "bonus" here is only the extra/magic-weapon
                    // bonus on top (0 for an ordinary weapon), no longer the whole attack bonus.
                    InkSelect(value.ability, Ability.entries, { it.name }, { onChange(value.copy(ability = it)) }, modifier = Modifier.padding(end = 8.dp))
                    IntField(value.attackBonus, label = "extra bonus") { onChange(value.copy(attackBonus = it)) }
                    DiceField(value.damage) { onChange(value.copy(damage = it)) }
                    InkSelect(value.damageType, DamageType.entries, { it.name }, { onChange(value.copy(damageType = it)) }, modifier = Modifier.padding(start = 8.dp))
                }
                is EffectTemplate.RollSave -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    InkSelect(value.ability, Ability.entries, { it.name }, { onChange(value.copy(ability = it)) }, modifier = Modifier.padding(end = 8.dp))
                    IntField(value.dc, label = "DC") { onChange(value.copy(dc = it)) }
                }
                is EffectTemplate.Push -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    RefPicker(value.awayFrom, { onChange(value.copy(awayFrom = it)) }, modifier = Modifier.padding(end = 8.dp))
                    IntField(value.distance, label = "tiles") { onChange(value.copy(distance = it)) }
                }
                is EffectTemplate.Teleport -> RefPicker(value.who, onChange = { onChange(value.copy(who = it)) })
                is EffectTemplate.SpawnEntity -> {
                    ArchetypeSelect(value.archetype, catalog) { onChange(value.copy(archetype = it)) }
                    InkSelect(value.faction, Faction.entries, { it.name }, { onChange(value.copy(faction = it)) }, modifier = Modifier.padding(start = 8.dp))
                }
                is EffectTemplate.DestroyEntity -> RefPicker(value.target, onChange = { onChange(value.copy(target = it)) })
                is EffectTemplate.Heal -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    IntField(value.amount) { onChange(value.copy(amount = it)) }
                }
            }
        }
        if (value is EffectTemplate.RollSave) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                InkLabel("ON SUCCESS")
                EffectTemplateListEditor(value.onSuccess, catalog, onChange = { onChange(value.copy(onSuccess = it)) })
                InkLabel("ON FAIL", modifier = Modifier.padding(top = 4.dp))
                EffectTemplateListEditor(value.onFail, catalog, onChange = { onChange(value.copy(onFail = it)) })
            }
        }
        if (value is EffectTemplate.Push) {
            // docs/29-push-on-wall-hit.md: same nested-editor shape as RollSave's onSuccess/onFail
            // above — Ref.EachTarget in here means "the entity that hit the wall" (see
            // EffectTemplateInstantiate.kt's per-target ctx scoping for Push.onWallHit).
            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                InkLabel("ON WALL HIT")
                EffectTemplateListEditor(value.onWallHit, catalog, onChange = { onChange(value.copy(onWallHit = it)) })
            }
        }
    }
}

@Composable
private fun StatusSelect(selected: StatusId, catalog: Catalog, onSelect: (StatusId) -> Unit) {
    val options = catalog.statuses.values.toList()
    if (options.isEmpty()) {
        InkLabel("no statuses yet")
    } else {
        InkSelect(
            options.find { it.id == selected } ?: options.first(),
            options,
            { it.name },
            { onSelect(it.id) },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun ArchetypeSelect(selected: ArchetypeId, catalog: Catalog, onSelect: (ArchetypeId) -> Unit) {
    val options = catalog.archetypes.values.toList()
    if (options.isEmpty()) {
        InkLabel("no archetypes yet")
    } else {
        InkSelect(
            options.find { it.id == selected } ?: options.first(),
            options,
            { it.name },
            { onSelect(it.id) },
        )
    }
}

/** Only the two authorable-ahead-of-time [Expiry] variants — see this file's own doc comment. */
@Composable
private fun ExpirySelect(selected: Expiry, onSelect: (Expiry) -> Unit) {
    val options = listOf<Expiry>(Expiry.Permanent, Expiry.OnConcentrationLost)
    InkSelect(selected, options, { if (it is Expiry.Permanent) "Permanent" else "Until concentration lost" }, onSelect, modifier = Modifier.padding(start = 8.dp))
}

@Composable
private fun IntField(value: Int, label: String? = null, onChange: (Int) -> Unit) {
    label?.let { InkLabel(it, modifier = Modifier.padding(end = 4.dp)) }
    var text by remember(value) { mutableStateOf(value.toString()) }
    InkTextField(text, onValueChange = { text = it; it.toIntOrNull()?.let(onChange) }, modifier = Modifier.width(50.dp).padding(end = 4.dp))
}

@Composable
private fun DiceField(value: DiceSpec, onChange: (DiceSpec) -> Unit) {
    var count by remember(value.count) { mutableStateOf(value.count.toString()) }
    var sides by remember(value.sides) { mutableStateOf(value.sides.toString()) }
    var mod by remember(value.modifier) { mutableStateOf(value.modifier.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkTextField(count, onValueChange = { count = it; it.toIntOrNull()?.let { n -> onChange(value.copy(count = n)) } }, modifier = Modifier.width(36.dp))
        InkLabel("d", modifier = Modifier.padding(horizontal = 2.dp))
        InkTextField(sides, onValueChange = { sides = it; it.toIntOrNull()?.let { n -> onChange(value.copy(sides = n)) } }, modifier = Modifier.width(36.dp))
        InkLabel("+", modifier = Modifier.padding(horizontal = 2.dp))
        InkTextField(mod, onValueChange = { mod = it; it.toIntOrNull()?.let { n -> onChange(value.copy(modifier = n)) } }, modifier = Modifier.width(36.dp))
    }
}
