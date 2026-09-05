package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.run.PendingLoot
import de.jackbeback.pocketquest.core.run.RunState
import de.jackbeback.pocketquest.core.run.revealLoot
import de.jackbeback.pocketquest.core.run.skipAllLootReveals
import de.jackbeback.pocketquest.ui.LootReel
import de.jackbeback.pocketquest.ui.assets.GameAssetManifest
import de.jackbeback.pocketquest.ui.assets.GameSpriteLoader
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET
import kotlin.math.roundToInt

private data class LootRevealAssets(val chests: Map<GridPos, Pair<ImageBitmap?, ImageBitmap?>>, val itemIcons: Map<ItemId, ImageBitmap>)

/**
 * Every image a chest row or its reel needs — chest closed/open sprite per placement, plus every
 * item icon reachable from any pending container's table (so `LootReel` never has to load mid-spin).
 * Keyed on `run.pendingLootReveal` itself (stable for the life of one reveal, only cleared once —
 * see `RunApp.kt`'s `RunScreen`), same "load once per screen, not per frame" discipline `:ui`'s
 * `loadMapAssets` already established.
 */
private suspend fun loadLootRevealAssets(pending: List<PendingLoot>, catalog: Catalog): LootRevealAssets {
    val manifest = GameAssetManifest.load()
    val chests = pending.associate { p ->
        val def = catalog.lootDef(p.loot)
        val closed = def.closedSprite?.let { manifest.prop(it) }?.let { GameSpriteLoader.load(it.file) }
        val open = def.openSprite?.let { manifest.prop(it) }?.let { GameSpriteLoader.load(it.file) }
        p.at to (closed to open)
    }
    val itemIds = pending.flatMap { catalog.lootDef(it.loot).table.map { entry -> entry.item } }.distinct()
    val itemIcons = itemIds.mapNotNull { id ->
        val icon = catalog.items[id]?.icon ?: return@mapNotNull null
        val bitmap = manifest.prop(icon)?.let { GameSpriteLoader.load(it.file) } ?: return@mapNotNull null
        id to bitmap
    }.toMap()
    return LootRevealAssets(chests, itemIcons)
}

/**
 * docs/38-loot-reveal-screen.md: [run.pendingLootReveal] stacked top to bottom, in placement order.
 * Tapping an unrevealed row spins its own [LootReel]; only one spins at a time ([spinningAt]) —
 * the reel's `onSettled` is what actually calls [revealLoot], granting the item exactly once the
 * animation finishes. "Skip All" resolves every remaining entry instantly via
 * [skipAllLootReveals]. "Continue" (shown once every entry is revealed) just clears the list — every
 * grant/loss already happened by then, this is purely "stop showing the screen."
 */
@Composable
fun LootRevealScreen(run: RunState, catalog: Catalog, onRunUpdated: (RunState) -> Unit) {
    val assets by produceState<LootRevealAssets?>(initialValue = null, run.pendingLootReveal) {
        value = loadLootRevealAssets(run.pendingLootReveal, catalog)
    }
    var spinningAt by remember { mutableStateOf<GridPos?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Loot", style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(run.pendingLootReveal) { pending ->
                LootRow(
                    pending = pending,
                    catalog = catalog,
                    chestSprites = assets?.chests?.get(pending.at),
                    itemIcons = assets?.itemIcons ?: emptyMap(),
                    spinning = spinningAt == pending.at,
                    onTap = { if (spinningAt == null) spinningAt = pending.at },
                    onSettled = {
                        onRunUpdated(revealLoot(run, pending.at, catalog))
                        spinningAt = null
                    },
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        Row {
            if (run.pendingLootReveal.any { !it.revealed }) {
                InkButton("Skip All", modifier = Modifier.padding(end = 8.dp), onClick = { onRunUpdated(skipAllLootReveals(run, catalog)) })
            }
            if (run.pendingLootReveal.all { it.revealed }) {
                InkButton("Continue", onClick = { onRunUpdated(run.copy(pendingLootReveal = emptyList())) })
            }
        }
    }
}

@Composable
private fun LootRow(
    pending: PendingLoot,
    catalog: Catalog,
    chestSprites: Pair<ImageBitmap?, ImageBitmap?>?,
    itemIcons: Map<ItemId, ImageBitmap>,
    spinning: Boolean,
    onTap: () -> Unit,
    onSettled: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).background(PAPER_SHEET).border(1.dp, INK)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !pending.revealed && !spinning, onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            spinning -> {
                val table = catalog.lootDef(pending.loot).table
                LootReel(table, pending.item, trigger = pending.at, catalog = catalog, itemIcons = itemIcons, onSettled = onSettled)
            }
            pending.revealed -> {
                val icon = pending.item?.let { itemIcons[it] }
                if (icon != null) {
                    Canvas(modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                        drawImage(icon, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    }
                }
                val name = pending.item?.let { catalog.items[it]?.name ?: it.raw } ?: "Nothing"
                Column {
                    BasicText(name, style = TextStyle(color = INK, fontSize = 15.sp))
                    if (pending.lost) {
                        BasicText("Bag full — lost", style = TextStyle(color = INK_FAINT, fontSize = 12.sp))
                    }
                }
            }
            else -> {
                val closed = chestSprites?.first
                if (closed != null) {
                    Canvas(modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                        drawImage(closed, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    }
                }
                BasicText(catalog.loot[pending.loot]?.name?.ifBlank { pending.loot.raw } ?: pending.loot.raw, style = TextStyle(color = INK, fontSize = 15.sp))
                BasicText(" — tap to open", style = TextStyle(color = INK_FAINT, fontSize = 12.sp))
            }
        }
    }
}
