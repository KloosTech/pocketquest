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
import de.jackbeback.pocketquest.core.model.GateId
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TileType
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkTextField

private enum class EffectKind { DealDamage, ApplyStatus, RemoveStatus, RollAttack, RollSave, Push, Teleport, SpawnEntity, DestroyEntity, Heal, ShowMessage, OpenGate, SetTerrain }

private fun EffectTemplate.kind(): EffectKind = when (this) {
    is EffectTemplate.DealDamage -> EffectKind.DealDamage
    is EffectTemplate.ApplyStatus -> EffectKind.ApplyStatus
    is EffectTemplate.RemoveStatus -> EffectKind.RemoveStatus
    is EffectTemplate.RollAttack -> EffectKind.RollAttack
    is EffectTemplate.RollSave -> EffectKind.RollSave
    is EffectTemplate.Push -> EffectKind.Push
    is EffectTemplate.Teleport -> EffectKind.Teleport
    is EffectTemplate.SpawnEntity -> EffectKind.SpawnEntity
    is EffectTemplate.DestroyEntity -> EffectKind.DestroyEntity
    is EffectTemplate.Heal -> EffectKind.Heal
    is EffectTemplate.ShowMessage -> EffectKind.ShowMessage
    is EffectTemplate.OpenGate -> EffectKind.OpenGate
    is EffectTemplate.SetTerrain -> EffectKind.SetTerrain
}

private fun defaultFor(kind: EffectKind, catalog: Catalog, gateIds: List<GateId> = emptyList()): EffectTemplate = when (kind) {
    EffectKind.DealDamage -> EffectTemplate.DealDamage(Ref.EachTarget, 0, DamageType.Bludgeoning)
    EffectKind.ApplyStatus -> EffectTemplate.ApplyStatus(Ref.EachTarget, catalog.statuses.keys.firstOrNull() ?: StatusId(""), 1, Expiry.Permanent)
    EffectKind.RemoveStatus -> EffectTemplate.RemoveStatus(Ref.EachTarget, catalog.statuses.keys.firstOrNull() ?: StatusId(""))
    EffectKind.RollAttack -> EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, 0, damage = DiceSpec(1, 6), damageType = DamageType.Bludgeoning)
    EffectKind.RollSave -> EffectTemplate.RollSave(Ref.EachTarget, Ability.Str, 10)
    EffectKind.Push -> EffectTemplate.Push(Ref.EachTarget, Ref.Caster, 1)
    EffectKind.Teleport -> EffectTemplate.Teleport(Ref.Caster)
    EffectKind.SpawnEntity -> EffectTemplate.SpawnEntity(catalog.archetypes.keys.firstOrNull() ?: ArchetypeId(""), Faction.Enemy, Controller.Ai(AiProfileId("standard")))
    EffectKind.DestroyEntity -> EffectTemplate.DestroyEntity(Ref.EachTarget)
    EffectKind.Heal -> EffectTemplate.Heal(Ref.EachTarget, 0)
    EffectKind.ShowMessage -> EffectTemplate.ShowMessage("")
    EffectKind.OpenGate -> EffectTemplate.OpenGate(gateIds.firstOrNull() ?: GateId(""))
    EffectKind.SetTerrain -> EffectTemplate.SetTerrain(GridPos(0, 0), TileType.Floor)
}

/**
 * doc20's "type dropdown + inline fields per row" pattern applied to [EffectTemplate]'s variants —
 * Action's `effects` and Status's `onTurnStart` both use this. [RollSave] recurses into two nested
 * lists (`onSuccess`/`onFail`); nothing else does, so recursion stays bounded in practice.
 *
 * [Expiry] offers Permanent/Until concentration lost/Turns here — `EndOfTurnOf`/`StartOfTurnOf`/
 * `EndOfRound` still stay handler-constructed-only, since they need a real `EntityId`/absolute round
 * number that doesn't exist at authoring time. `Turns` (docs/41) sidesteps that: it's a relative
 * "expires after N rounds" the `applyStatus` handler resolves into a concrete `EndOfRound` using
 * whatever round it's actually applied on, not something authored here as an absolute number.
 */
/**
 * [gateIds] (docs/48-gates-and-wander-ai.md): the current map's gates, for the `OpenGate` row's
 * picker — empty for every non-map authoring surface (`ActionPanel`/`StatusPanel`, where "which
 * map's gates" isn't a meaningful question), which falls back to a raw-id text field there instead.
 */
@Composable
fun EffectTemplateListEditor(effects: List<EffectTemplate>, catalog: Catalog, onChange: (List<EffectTemplate>) -> Unit, modifier: Modifier = Modifier, gateIds: List<GateId> = emptyList()) {
    Column(modifier = modifier) {
        effects.forEachIndexed { index, effect ->
            EffectRow(
                value = effect,
                catalog = catalog,
                gateIds = gateIds,
                onChange = { updated -> onChange(effects.toMutableList().also { it[index] = updated }) },
                onRemove = { onChange(effects.filterIndexed { i, _ -> i != index }) },
            )
        }
        InkButton("+ Add Effect", modifier = Modifier.padding(top = 4.dp), onClick = { onChange(effects + defaultFor(EffectKind.DealDamage, catalog)) })
    }
}

@Composable
private fun EffectRow(value: EffectTemplate, catalog: Catalog, gateIds: List<GateId>, onChange: (EffectTemplate) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkSelect(
                selected = value.kind(),
                options = EffectKind.entries,
                label = { it.name },
                onSelect = { onChange(defaultFor(it, catalog, gateIds)) },
                modifier = Modifier.padding(end = 8.dp),
            )
            InkButton("Remove", onClick = onRemove)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            when (value) {
                is EffectTemplate.DealDamage -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    IntField(value.amount, label = "flat") { onChange(value.copy(amount = it)) }
                    // docs/42-status-stack-scaling.md: only meaningful inside a status's own
                    // onTurnStart list — a no-op (multiplies by 0 stacks) everywhere else.
                    IntField(value.perStack, label = "per stack") { onChange(value.copy(perStack = it)) }
                    InkSelect(value.damageType, DamageType.entries, { it.name }, { onChange(value.copy(damageType = it)) }, modifier = Modifier.padding(start = 8.dp))
                }
                is EffectTemplate.ApplyStatus -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    StatusSelect(value.status, catalog) { onChange(value.copy(status = it)) }
                    IntField(value.stacks, label = "stacks") { onChange(value.copy(stacks = it)) }
                    ExpirySelect(value.expiry) { onChange(value.copy(expiry = it)) }
                }
                // docs/41-status-duration-and-ability-mods.md: the healing/cleanse counterpart to ApplyStatus.
                is EffectTemplate.RemoveStatus -> {
                    RefPicker(value.target, { onChange(value.copy(target = it)) }, modifier = Modifier.padding(end = 8.dp))
                    StatusSelect(value.status, catalog) { onChange(value.copy(status = it)) }
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
                // docs/36-map-triggers.md: no Ref — static authored text, nothing to resolve per-target.
                is EffectTemplate.ShowMessage -> InkTextField(value.text, onValueChange = { onChange(value.copy(text = it)) }, modifier = Modifier.width(280.dp))
                // docs/48-gates-and-wander-ai.md: a picker over the current map's gates when known,
                // else (ActionPanel/StatusPanel, no map in scope) a raw-id text field fallback.
                is EffectTemplate.OpenGate -> {
                    if (gateIds.isEmpty()) {
                        InkTextField(value.gate.raw, onValueChange = { onChange(value.copy(gate = GateId(it))) }, modifier = Modifier.width(120.dp))
                    } else {
                        InkSelect(
                            selected = value.gate,
                            options = gateIds,
                            label = { id -> "Gate ${gateIds.indexOf(id) + 1}" },
                            onSelect = { onChange(value.copy(gate = it)) },
                        )
                    }
                }
                // docs/50-terrain-mutation.md: a literal position + the same named TileType presets
                // the Map editor's own terrain tool uses (Floor/Wall/Difficult/Hazard/Chasm).
                is EffectTemplate.SetTerrain -> {
                    InkLabel("col", modifier = Modifier.padding(end = 4.dp))
                    IntField(value.at.col) { onChange(value.copy(at = value.at.copy(col = it))) }
                    InkLabel("row", modifier = Modifier.padding(start = 8.dp, end = 4.dp))
                    IntField(value.at.row) { onChange(value.copy(at = value.at.copy(row = it))) }
                    TerrainKindSelect(value.tile, modifier = Modifier.padding(start = 8.dp)) { onChange(value.copy(tile = it)) }
                }
            }
        }
        if (value is EffectTemplate.RollSave) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                InkLabel("ON SUCCESS")
                EffectTemplateListEditor(value.onSuccess, catalog, onChange = { onChange(value.copy(onSuccess = it)) }, gateIds = gateIds)
                InkLabel("ON FAIL", modifier = Modifier.padding(top = 4.dp))
                EffectTemplateListEditor(value.onFail, catalog, onChange = { onChange(value.copy(onFail = it)) }, gateIds = gateIds)
            }
        }
        if (value is EffectTemplate.Push) {
            // docs/29-push-on-wall-hit.md: same nested-editor shape as RollSave's onSuccess/onFail
            // above — Ref.EachTarget in here means "the entity that hit the wall" (see
            // EffectTemplateInstantiate.kt's per-target ctx scoping for Push.onWallHit).
            Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                InkLabel("ON WALL HIT")
                EffectTemplateListEditor(value.onWallHit, catalog, onChange = { onChange(value.copy(onWallHit = it)) }, gateIds = gateIds)
            }
        }
    }
}

private enum class TerrainKind { Floor, Wall, Difficult, Hazard, Chasm, InvisibleWall }

private fun TileType.terrainKind(): TerrainKind = when (this) {
    TileType.Floor -> TerrainKind.Floor
    TileType.Wall -> TerrainKind.Wall
    TileType.Difficult -> TerrainKind.Difficult
    TileType.Hazard -> TerrainKind.Hazard
    TileType.Chasm -> TerrainKind.Chasm
    TileType.InvisibleWall -> TerrainKind.InvisibleWall
    else -> TerrainKind.Floor
}

private fun TerrainKind.tile(): TileType = when (this) {
    TerrainKind.Floor -> TileType.Floor
    TerrainKind.Wall -> TileType.Wall
    TerrainKind.Difficult -> TileType.Difficult
    TerrainKind.Hazard -> TileType.Hazard
    TerrainKind.Chasm -> TileType.Chasm
    TerrainKind.InvisibleWall -> TileType.InvisibleWall
}

/** Same named presets the Map editor's own terrain-paint tool offers — see `descriptionFor(TileType)` there for the mechanically-exact copy this doesn't duplicate, just the label set. */
@Composable
private fun TerrainKindSelect(selected: TileType, modifier: Modifier = Modifier, onSelect: (TileType) -> Unit) {
    InkSelect(selected.terrainKind(), TerrainKind.entries, { it.name }, { onSelect(it.tile()) }, modifier = modifier)
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

private enum class ExpiryKind { Permanent, OnConcentrationLost, Turns }

/** `EndOfTurnOf`/`StartOfTurnOf`/`EndOfRound` are never authored here — see this file's own doc comment — so they fall back to Permanent for display only; nothing writes them back. */
private fun Expiry.expiryKind(): ExpiryKind = when (this) {
    is Expiry.Permanent -> ExpiryKind.Permanent
    is Expiry.OnConcentrationLost -> ExpiryKind.OnConcentrationLost
    is Expiry.Turns -> ExpiryKind.Turns
    is Expiry.EndOfTurnOf, is Expiry.StartOfTurnOf, is Expiry.EndOfRound -> ExpiryKind.Permanent
}

/** The three authorable-ahead-of-time [Expiry] shapes — see this file's own doc comment. */
@Composable
private fun ExpirySelect(selected: Expiry, onSelect: (Expiry) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(
            selected.expiryKind(),
            ExpiryKind.entries,
            {
                when (it) {
                    ExpiryKind.Permanent -> "Permanent"
                    ExpiryKind.OnConcentrationLost -> "Until concentration lost"
                    ExpiryKind.Turns -> "Turns"
                }
            },
            { kind ->
                onSelect(
                    when (kind) {
                        ExpiryKind.Permanent -> Expiry.Permanent
                        ExpiryKind.OnConcentrationLost -> Expiry.OnConcentrationLost
                        ExpiryKind.Turns -> Expiry.Turns(1)
                    },
                )
            },
            modifier = Modifier.padding(start = 8.dp),
        )
        if (selected is Expiry.Turns) {
            IntField(selected.n, label = "turns") { onSelect(Expiry.Turns(it.coerceAtLeast(1))) }
        }
    }
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
