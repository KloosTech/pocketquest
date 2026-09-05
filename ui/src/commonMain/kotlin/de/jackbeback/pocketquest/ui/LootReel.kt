package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.LootEntry
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private const val REEL_CYCLES = 5
private const val SYMBOL_HEIGHT_DP = 56
private const val WINDOW_SYMBOLS = 3
private const val SPIN_MS = 1400

/** One reel position — a real item, or "nothing" (the table's own unclaimed probability mass — see [LootEntry]'s doc comment). */
private data class ReelSymbol(val item: ItemId?)

/**
 * docs/38-loot-reveal-screen.md: the reel is built from [table] itself (one symbol per entry, plus a
 * "Nothing" symbol only if the weights leave headroom) so what's visually possible to land on always
 * matches what was actually possible to roll — never a generic/unrelated spinner.
 *
 * Landing on the very last symbol of the very last cycle left nothing after it for the window to
 * show once centered (found live: a single-item table settled on blank rows, since the window needs
 * [WINDOW_SYMBOLS]/2 rows of real content on BOTH sides of the landing row, and there was no "both
 * sides" once it landed on the tail end of the whole list) — fixed by landing on the second-to-last
 * cycle instead, guaranteeing at least one full trailing cycle as padding regardless of how short
 * [table] is (even a single-entry table still gets real repeated content on every side).
 */
private fun buildReel(table: List<LootEntry>, result: ItemId?): Pair<List<ReelSymbol>, Int> {
    val sumWeight = table.sumOf { it.weight }
    val base = (table.map { ReelSymbol(it.item) } + if (sumWeight < 1.0) listOf(ReelSymbol(null)) else emptyList())
        .ifEmpty { listOf(ReelSymbol(null)) }
    val totalCycles = REEL_CYCLES + 2
    val symbols = List(totalCycles) { base }.flatten()
    val landingCycle = totalCycles - 2
    val indexWithinCycle = base.indexOfLast { it.item == result }.let { if (it < 0) base.size - 1 else it }
    val resultIndex = landingCycle * base.size + indexWithinCycle
    return symbols to resultIndex
}

/**
 * A vertical slot-machine reel windowed to [WINDOW_SYMBOLS] rows, spinning from the top of a
 * [buildReel] population down to the pre-decided [result] — mirrors `Dice3D.kt`'s `DiceRoll`
 * pattern (`Animatable` + `CubicBezierEasing` overshoot-then-settle, restarted on a fresh [trigger])
 * for an "animate toward an already-known outcome" beat, just a 2D scroll instead of a 3D tumble.
 * Calls [onSettled] once the spin finishes — the caller is what actually grants the item
 * (`revealLoot`), this composable is purely the visual.
 */
@Composable
fun LootReel(
    table: List<LootEntry>,
    result: ItemId?,
    trigger: Any,
    catalog: Catalog,
    itemIcons: Map<ItemId, ImageBitmap>,
    onSettled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (symbols, resultIndex) = remember(table, result) { buildReel(table, result) }
    val offset = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        offset.snapTo(0f)
        // Randomized duration seeded off the trigger, same "never look like the same repeated clip"
        // reasoning Dice3D's own tumble uses — only the landing spot is fixed.
        val jitterMs = SPIN_MS + Random(trigger.hashCode()).nextInt(-150, 150)
        offset.animateTo(
            resultIndex * SYMBOL_HEIGHT_DP.toFloat(),
            tween(jitterMs, easing = CubicBezierEasing(0.15f, 0.85f, 0.25f, 1f)),
        )
        onSettled()
    }

    // docs/38-loot-reveal-screen.md: found live — clipping a single huge (14+ row) Column scrolled
    // via a large offset never actually rendered anything visible in this environment (same class of
    // Compose Desktop quirk this project has hit before, e.g. detectTapGestures never firing
    // reliably). Sidestepped entirely: only ever render the ~4 rows that could possibly be visible,
    // shifted by at most one row's height, recomputed each frame from `offset.value` — no large
    // offset, no clipping a giant strip, nothing to go wrong at that scale.
    val centerIndexFloat = offset.value / SYMBOL_HEIGHT_DP
    val baseIndex = floor(centerIndexFloat).toInt()
    val fracDp = (centerIndexFloat - baseIndex) * SYMBOL_HEIGHT_DP

    Box(
        modifier = modifier.width(180.dp).height((SYMBOL_HEIGHT_DP * WINDOW_SYMBOLS).dp).background(PAPER_SHEET).border(1.dp, INK).clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.offset(y = (-fracDp).dp)) {
            // baseIndex-1 lands in the window's top row once fracDp settles to 0 — see the doc
            // comment on [buildReel] for why baseIndex+2 is always a real (padded) symbol too.
            for (i in (baseIndex - 1)..(baseIndex + 2)) {
                val symbol = symbols.getOrNull(i)
                if (symbol != null) {
                    ReelSymbolRow(symbol, catalog, itemIcons)
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(SYMBOL_HEIGHT_DP.dp))
                }
            }
        }
        // Window indicator — the center row is what's "landed on."
        Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(SYMBOL_HEIGHT_DP.dp).border(2.dp, DANGER))
    }
}

@Composable
private fun ReelSymbolRow(symbol: ReelSymbol, catalog: Catalog, itemIcons: Map<ItemId, ImageBitmap>) {
    Row(
        modifier = Modifier.fillMaxWidth().height(SYMBOL_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = symbol.item?.let { itemIcons[it] }
        if (icon != null) {
            Canvas(modifier = Modifier.size(32.dp).padding(end = 6.dp)) {
                drawImage(icon, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            }
        }
        val label = symbol.item?.let { catalog.items[it]?.name ?: it.raw } ?: "Nothing"
        BasicText(label, style = TextStyle(color = if (symbol.item == null) INK_FAINT else INK, fontSize = 13.sp))
    }
}
