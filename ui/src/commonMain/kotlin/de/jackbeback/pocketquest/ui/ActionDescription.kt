package de.jackbeback.pocketquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.Targeting
import de.jackbeback.pocketquest.core.rules.targeting.tilesInShape
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.PAPER

/**
 * docs/25-action-selection-ui.md: mechanical, auto-generated text for the Details view's right
 * panel — walks [effects] and produces one sentence per top-level template, recursing into
 * [EffectTemplate.RollSave]'s on-success/on-fail branches. Deliberately doesn't mention AP/mana
 * cost — the grid card and Peek header already show that.
 */
fun describeEffects(effects: List<EffectTemplate>, catalog: Catalog): String =
    effects.joinToString(" ") { describeEffect(it, catalog) }

private fun describeEffect(effect: EffectTemplate, catalog: Catalog): String = when (effect) {
    is EffectTemplate.RollAttack ->
        "Does [${effect.ability.name}] Attack roll which does ${diceText(effect.damage)} ${effect.damageType.name} damage."
    is EffectTemplate.RollSave -> buildString {
        append("Causes [${effect.ability.name}] Save roll vs DC ${effect.dc}.")
        if (effect.onSuccess.isNotEmpty()) append(" On success: ${describeEffects(effect.onSuccess, catalog)}")
        if (effect.onFail.isNotEmpty()) append(" On fail: ${describeEffects(effect.onFail, catalog)}")
    }
    is EffectTemplate.DealDamage -> "Deals ${effect.amount} ${effect.damageType.name} damage."
    is EffectTemplate.Push -> "Pushes the target ${effect.distance} tiles away."
    is EffectTemplate.Heal -> "Heals ${effect.amount} HP."
    is EffectTemplate.ApplyStatus -> "Applies ${catalog.statusDef(effect.status).name} (${effect.stacks}x)."
    is EffectTemplate.Teleport -> "Teleports the target."
    is EffectTemplate.SpawnEntity -> "Summons ${catalog.archetype(effect.archetype).name}."
    is EffectTemplate.DestroyEntity -> "Destroys the target."
}

private fun diceText(spec: DiceSpec): String {
    val mod = when {
        spec.modifier > 0 -> "+${spec.modifier}"
        spec.modifier < 0 -> "${spec.modifier}"
        else -> ""
    }
    return "${spec.count}d${spec.sides}$mod"
}

/**
 * docs/25's abstract shape preview — no real map/LoS, same "no map/party needed" approach
 * `:designer`'s `PreviewPanel` already uses for `preview()`. [origin] is the caster's cell,
 * [affected] the tiles [Shape] would hit, both in a small synthetic grid built just large enough
 * to fit — never the real battle map's dimensions.
 */
data class ShapePreview(val origin: GridPos, val affected: Set<GridPos>)

fun previewShape(targeting: Targeting): ShapePreview {
    val range = when (val r = targeting.range) {
        Range.Melee -> 1
        Range.SelfRange -> 0
        is Range.Tiles -> r.n
    }
    val extent = when (val s = targeting.shape) {
        Shape.Single -> 1
        is Shape.Sphere -> s.radius
        is Shape.Cone -> s.length
        is Shape.Line -> s.length
        is Shape.Rect -> maxOf(s.width, s.height)
    }
    val half = maxOf(range + extent, 3)
    val size = half * 2 + 1
    val map = BattleMap(size, size)
    val origin = GridPos(0, half)
    val point = GridPos(minOf(range, size - 1), half)
    val affected = tilesInShape(origin, point, targeting.shape, map)
    return ShapePreview(origin, affected)
}

private const val SHAPE_PREVIEW_CELL_DP = 20

/**
 * docs/25-action-selection-ui.md: the abstract shape/AoE grid — [ShapePreview.origin] highlighted
 * as the caster, [ShapePreview.affected] as what the shape would hit. No real map/LoS, purely
 * illustrative. Public: shared by the in-game Details view (`App.kt`'s `ActionDetailsPanel`) and
 * `:designer`'s `ActionPanel.kt`, which shows the same live preview while authoring.
 */
@Composable
fun ShapePreviewGrid(preview: ShapePreview, modifier: Modifier = Modifier) {
    val cells = preview.affected + preview.origin
    val minCol = cells.minOf { it.col }
    val maxCol = cells.maxOf { it.col }
    val minRow = cells.minOf { it.row }
    val maxRow = cells.maxOf { it.row }
    Column(modifier = modifier) {
        for (row in minRow..maxRow) {
            Row {
                for (col in minCol..maxCol) {
                    val pos = GridPos(col, row)
                    val color = when {
                        pos == preview.origin -> Color(0xFF2E7D32)
                        pos in preview.affected -> Color(0xFFB71C1C)
                        else -> PAPER
                    }
                    Box(modifier = Modifier.size(SHAPE_PREVIEW_CELL_DP.dp).border(1.dp, INK_FAINT).background(color))
                }
            }
        }
    }
}
