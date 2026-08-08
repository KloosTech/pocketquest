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
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Cost
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.ReactionTrigger
import de.jackbeback.pocketquest.core.model.ReactionTriggerKind
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.StatusDef
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.Targeting
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.rules.action.preview
import de.jackbeback.pocketquest.core.rules.stat.stats
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
 * doc20's Action editor — the highest-leverage remaining editor: only "move" exists in any catalog
 * without this, every archetype is combat-dead. Cost/Targeting/Effects match [ActionDef]'s real
 * shape field-for-field (verified against `core/model/.../ActionDef.kt`, not guessed). The preview
 * panel is doc16's stated payoff for `:designer` depending on `:core:rules` — a real `preview()`
 * call against a throwaway 2-entity scenario, not `startEncounter` (no map/party needed for this).
 */
@Composable
fun ActionPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<ActionId?>(catalog.actions.keys.firstOrNull()) }

    fun updateAction(id: ActionId, transform: (ActionDef) -> ActionDef) {
        val current = catalog.actions[id] ?: return
        onCatalogChange(catalog.copy(actions = catalog.actions + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("ACTIONS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.actions.values.toList()) { action ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = action.id }
                            .background(if (action.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(action.name, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Action",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.actions.size + 1
                    while (ActionId("action$n") in catalog.actions) n++
                    val id = ActionId("action$n")
                    val action = ActionDef(
                        id = id, name = "New Action $n",
                        cost = Cost(action = ActionCost.Main),
                        targeting = Targeting(mode = TargetMode.SingleEntity, range = Range.Melee, shape = Shape.Single),
                        effects = emptyList(),
                    )
                    onCatalogChange(catalog.copy(actions = catalog.actions + (id to action)))
                    selectedId = id
                },
            )
        }

        val action = selectedId?.let { catalog.actions[it] }
        if (action != null) {
            ActionEditor(
                action = action,
                catalog = catalog,
                onChange = { updated -> updateAction(action.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(actions = catalog.actions - action.id))
                    selectedId = catalog.actions.keys.firstOrNull { it != action.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No action selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun ActionEditor(action: ActionDef, catalog: Catalog, onChange: (ActionDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove Action", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(action.name, onValueChange = { onChange(action.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("COST") }
        CostEditor(action.cost, catalog) { onChange(action.copy(cost = it)) }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("TARGETING") }
        TargetingEditor(action.targeting, catalog) { onChange(action.copy(targeting = it)) }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("EFFECTS") }
        EffectTemplateListEditor(action.effects, catalog, onChange = { onChange(action.copy(effects = it)) })

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("REACTION TRIGGER") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val options: List<ReactionTriggerKind?> = listOf(null) + ReactionTriggerKind.entries
            InkSelect(
                selected = action.reactionTrigger?.kind,
                options = options,
                label = { it?.name ?: "None (not a reaction)" },
                onSelect = { kind -> onChange(action.copy(reactionTrigger = kind?.let(::ReactionTrigger))) },
            )
        }

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("PREVIEW") }
        PreviewPanel(action, catalog)
    }
}

@Composable
private fun CostEditor(cost: Cost, catalog: Catalog, onChange: (Cost) -> Unit) {
    // Same fix as TargetFilterEditor/TargetingEditor — two sibling Rows need an explicit Column.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkSelect(
                selected = cost.action.kindLabel(),
                options = listOf("Main", "Quick", "Reaction", "Movement", "Free"),
                label = { it },
                onSelect = { kind ->
                    val newAction = when (kind) {
                        "Main" -> ActionCost.Main
                        "Quick" -> ActionCost.Quick
                        "Reaction" -> ActionCost.Reaction
                        "Movement" -> ActionCost.Movement(6)
                        else -> ActionCost.Free
                    }
                    onChange(cost.copy(action = newAction))
                },
                modifier = Modifier.padding(end = 8.dp),
            )
            val movement = cost.action as? ActionCost.Movement
            if (movement != null) {
                InkLabel("tiles", modifier = Modifier.padding(end = 4.dp))
                InkStepper(movement.tiles, min = 1, onValueChange = { onChange(cost.copy(action = ActionCost.Movement(it))) })
            } else {
                // Movement prices itself off tiles/the resolved route instead (Cost.apCost's own doc
                // comment) — showing this stepper too would let content double-charge the same AP pool.
                InkLabel("AP", modifier = Modifier.padding(end = 4.dp))
                InkStepper(cost.apCost, onValueChange = { onChange(cost.copy(apCost = it)) })
            }
            InkLabel("mana", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            InkStepper(cost.mana, onValueChange = { onChange(cost.copy(mana = it)) })
            InkLabel("hpCost", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            InkStepper(cost.hpCost, onValueChange = { onChange(cost.copy(hpCost = it)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("charges", modifier = Modifier.padding(end = 8.dp))
            val items = catalog.items.values.toList()
            if (items.isEmpty()) {
                InkLabel("no items yet")
            } else {
                InkSelect(
                    cost.charges?.let { id -> items.find { it.id == id } },
                    listOf<ItemDef?>(null) + items,
                    { it?.name ?: "None" },
                    { onChange(cost.copy(charges = it?.id)) },
                )
            }
        }
    }
}

private fun ActionCost.kindLabel(): String = when (this) {
    ActionCost.Main -> "Main"
    ActionCost.Quick -> "Quick"
    ActionCost.Reaction -> "Reaction"
    is ActionCost.Movement -> "Movement"
    ActionCost.Free -> "Free"
}

@Composable
private fun TargetingEditor(targeting: Targeting, catalog: Catalog, onChange: (Targeting) -> Unit) {
    // See TargetFilterEditor's comment — same fix, same reason: multiple sibling emits need an
    // explicit Column, not an incidental one inherited from whatever the caller happens to be.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkSelect(targeting.mode, TargetMode.entries, { it.name }, { onChange(targeting.copy(mode = it)) }, modifier = Modifier.padding(end = 8.dp))
            RangeSelect(targeting.range) { onChange(targeting.copy(range = it)) }
            Box(modifier = Modifier.padding(start = 8.dp)) { ShapeEditor(targeting.shape) { onChange(targeting.copy(shape = it)) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            ActionToggleLabel("requires LoS", targeting.requiresLoS) { onChange(targeting.copy(requiresLoS = it)) }
            InkLabel("max targets", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            InkStepper(targeting.maxTargets, min = 1, onValueChange = { onChange(targeting.copy(maxTargets = it)) })
        }
        Box(modifier = Modifier.padding(top = 4.dp)) {
            TargetFilterEditor(targeting.filter, catalog) { onChange(targeting.copy(filter = it)) }
        }
    }
}

@Composable
private fun RangeSelect(range: Range, onChange: (Range) -> Unit) {
    val label = when (range) {
        Range.Melee -> "Melee"
        Range.SelfRange -> "Self"
        is Range.Tiles -> "Tiles"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(
            selected = label,
            options = listOf("Melee", "Tiles", "Self"),
            label = { it },
            onSelect = { onChange(if (it == "Melee") Range.Melee else if (it == "Self") Range.SelfRange else Range.Tiles(4)) },
            modifier = Modifier.padding(end = 4.dp),
        )
        (range as? Range.Tiles)?.let { InkStepper(it.n, min = 1, onValueChange = { n -> onChange(Range.Tiles(n)) }) }
    }
}

@Composable
private fun ShapeEditor(shape: Shape, onChange: (Shape) -> Unit) {
    val label = when (shape) {
        Shape.Single -> "Single"
        is Shape.Sphere -> "Sphere"
        is Shape.Cone -> "Cone"
        is Shape.Line -> "Line"
        is Shape.Rect -> "Rect"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkSelect(
            selected = label,
            options = listOf("Single", "Sphere", "Cone", "Line", "Rect"),
            label = { it },
            onSelect = {
                onChange(
                    when (it) {
                        "Single" -> Shape.Single
                        "Sphere" -> Shape.Sphere(2)
                        "Cone" -> Shape.Cone(3, 60)
                        "Line" -> Shape.Line(3)
                        else -> Shape.Rect(2, 2)
                    },
                )
            },
            modifier = Modifier.padding(end = 4.dp),
        )
        when (shape) {
            is Shape.Sphere -> InkStepper(shape.radius, min = 1, onValueChange = { onChange(Shape.Sphere(it)) })
            is Shape.Cone -> {
                InkStepper(shape.length, min = 1, onValueChange = { onChange(shape.copy(length = it)) })
                InkStepper(shape.degrees, min = 1, onValueChange = { onChange(shape.copy(degrees = it)) })
            }
            is Shape.Line -> InkStepper(shape.length, min = 1, onValueChange = { onChange(Shape.Line(it)) })
            is Shape.Rect -> {
                InkStepper(shape.width, min = 1, onValueChange = { onChange(shape.copy(width = it)) })
                InkStepper(shape.height, min = 1, onValueChange = { onChange(shape.copy(height = it)) })
            }
            Shape.Single -> Unit
        }
    }
}

@Composable
private fun TargetFilterEditor(filter: TargetFilter, catalog: Catalog, onChange: (TargetFilter) -> Unit) {
    // Both rows MUST be inside one Column here — a bare `Row{}; Row{}` with no wrapping layout
    // isn't "one composable" to whatever Compose node actually receives them. The previous version
    // had exactly two Rows at this function's top level, and its call site wrapped the whole call
    // in a Box — Box overlays multiple children at the same origin instead of stacking them, which
    // is what rendered as garbled overlapping text.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("faction", modifier = Modifier.padding(end = 4.dp))
            InkSelect(filter.faction, listOf<Faction?>(null) + Faction.entries, { it?.name ?: "Any" }, { onChange(filter.copy(faction = it)) }, modifier = Modifier.padding(end = 12.dp))
            ActionToggleLabel("require alive", filter.requireAlive) { onChange(filter.copy(requireAlive = it)) }
            ActionToggleLabel("exclude self", filter.excludeSelf, modifier = Modifier.padding(start = 12.dp)) { onChange(filter.copy(excludeSelf = it)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("has status", modifier = Modifier.padding(end = 4.dp))
            val statuses = catalog.statuses.values.toList()
            if (statuses.isEmpty()) {
                InkLabel("no statuses yet")
            } else {
                InkSelect(
                    filter.hasStatus?.let { id -> statuses.find { it.id == id } },
                    listOf<StatusDef?>(null) + statuses,
                    { it?.name ?: "Any" },
                    { onChange(filter.copy(hasStatus = it?.id)) },
                )
            }
        }
    }
}

@Composable
private fun ActionToggleLabel(label: String, value: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(!value) },
    ) {
        BasicText(if (value) "☑" else "☐", style = TextStyle(color = INK, fontSize = 14.sp), modifier = Modifier.padding(end = 4.dp))
        BasicText(label, style = TextStyle(color = INK, fontSize = 12.sp))
    }
}

/**
 * doc16's stated payoff for `:designer` depending on `:core:rules`: a real numeric preview against
 * the actual engine, not a mock. Builds a throwaway 2-entity scenario (no map/party needed — just
 * enough state for `preview()`'s `Expected`-mode resolver run) rather than `startEncounter`, which
 * needs an `EncounterSpec`/`BattleMapDef` this panel has no reason to require.
 */
@Composable
private fun PreviewPanel(action: ActionDef, catalog: Catalog) {
    val archetypes = catalog.archetypes.values.toList()
    if (archetypes.isEmpty()) {
        InkLabel("Add an archetype first to preview against.")
        return
    }
    var casterId by remember(archetypes) { mutableStateOf(archetypes.first().id) }
    var targetId by remember(archetypes) { mutableStateOf(archetypes.first().id) }

    // Same fix as CostEditor/TargetingEditor/TargetFilterEditor — an explicit Column, not an
    // incidental one inherited from whatever the caller happens to be.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("caster", modifier = Modifier.padding(end = 4.dp))
            InkSelect(
                archetypes.find { it.id == casterId } ?: archetypes.first(),
                archetypes,
                { it.name },
                { casterId = it.id },
                modifier = Modifier.padding(end = 12.dp),
            )
            InkLabel("target", modifier = Modifier.padding(end = 4.dp))
            InkSelect(
                archetypes.find { it.id == targetId } ?: archetypes.first(),
                archetypes,
                { it.name },
                { targetId = it.id },
            )
        }

        val result = runCatching { previewAction(catalog, action, casterId, targetId) }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            result.fold(
                onSuccess = { events -> events.forEach { BasicText("• $it", style = TextStyle(color = INK, fontSize = 12.sp)) } },
                onFailure = { e -> BasicText("Can't preview: ${e.message}", style = TextStyle(color = DANGER, fontSize = 12.sp)) },
            )
        }
    }
}

private fun previewAction(catalog: Catalog, action: ActionDef, casterArchetype: ArchetypeId, targetArchetype: ArchetypeId): List<String> {
    fun spawn(id: Long, archetype: ArchetypeId, pos: GridPos, faction: Faction): Entity {
        val preliminary = Entity(EntityId(id), archetype, pos, health = null, resources = null, actor = Actor(faction, Controller.Human))
        val stats = preliminary.stats(catalog)
        return preliminary.copy(health = Health(stats.maxHp), resources = Resources(ap = stats.maxAp, mana = stats.maxMana))
    }
    val caster = spawn(0, casterArchetype, GridPos(0, 0), Faction.Player)
    val target = spawn(1, targetArchetype, GridPos(1, 0), Faction.Enemy)
    val state = GameState(
        entities = listOf(caster, target),
        map = BattleMap(width = 5, height = 5),
        turn = TurnState(round = 1, order = listOf(caster.id, target.id), activeIndex = 0, phase = TurnPhase.Start),
        rng = RngState(seed = 0),
        nextEntityId = 2,
    )
    val ctx = ActionCtx(caster = caster.id, targets = listOf(target.id), point = target.pos)
    val result = preview(state, caster.id, action.id, ctx, catalog.copy(actions = catalog.actions + (action.id to action)))
    return result.events.map { it.toString() }
}
