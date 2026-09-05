package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.progression.MetaEquipmentTransactionResult
import de.jackbeback.pocketquest.core.progression.equipFromStash
import de.jackbeback.pocketquest.core.progression.unequipToStash
import de.jackbeback.pocketquest.core.rules.equipment.EquipRejection
import de.jackbeback.pocketquest.core.run.EquipmentTransactionRejection
import de.jackbeback.pocketquest.core.run.EquipmentTransactionResult
import de.jackbeback.pocketquest.core.run.RunState
import de.jackbeback.pocketquest.core.run.equipFromInventory
import de.jackbeback.pocketquest.core.run.unequipToInventory
import de.jackbeback.pocketquest.core.run.useItemFromInventory
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.PAPER

/**
 * docs/47-inventory-screen.md — reachable from both the in-run battle menu ([run] non-null: reads/
 * writes [RunState.inventory] + `PartyMember.equipment`) and the Hub ([run] null: reads/writes
 * [MetaState.stash] + `ChampionRecord.equipment`). The two pools never cross at runtime — this
 * composable just picks which one to render, the underlying screens are otherwise independent.
 */
@Composable
fun InventoryScreen(run: RunState?, meta: MetaState, catalog: Catalog, onRunUpdated: (RunState) -> Unit, onMetaUpdated: (MetaState) -> Unit, onBack: () -> Unit) {
    if (run != null) {
        RunInventoryScreen(run, catalog, onRunUpdated, onBack)
    } else {
        StashInventoryScreen(meta, catalog, onMetaUpdated, onBack)
    }
}

@Composable
private fun RunInventoryScreen(run: RunState, catalog: Catalog, onRunUpdated: (RunState) -> Unit, onBack: () -> Unit) {
    var selectedId by remember(run.runId) { mutableStateOf(run.party.firstOrNull()?.memberId) }
    var message by remember { mutableStateOf<String?>(null) }
    val selected = run.party.firstOrNull { it.memberId == selectedId }

    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Inventory", style = TextStyle(color = INK, fontSize = 20.sp), modifier = Modifier.weight(1f))
            InkButton("Back", onClick = onBack)
        }
        Spacer(modifier = Modifier.size(16.dp))

        InkLabel("PARTY")
        Row {
            run.party.forEach { member ->
                InkButton(
                    member.name,
                    modifier = Modifier.padding(end = 8.dp),
                    emphasized = member.memberId == selectedId,
                    onClick = { selectedId = member.memberId; message = null },
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        if (selected != null) {
            InkLabel("EQUIPMENT")
            Slot.entries.forEach { slot ->
                EquipmentSlotRow(slot, selected.equipment.slots[slot]?.def, catalog, run.inventory.items) { picked ->
                    message = null
                    val result = if (picked == null) {
                        unequipToInventory(run, selected.memberId, slot, catalog)
                    } else {
                        equipFromInventory(run, selected.memberId, slot, picked, catalog)
                    }
                    when (result) {
                        is EquipmentTransactionResult.Applied -> onRunUpdated(result.run)
                        is EquipmentTransactionResult.Rejected -> message = describeRejection(result.reasons)
                    }
                }
            }
        }
        message?.let {
            Spacer(modifier = Modifier.size(8.dp))
            BasicText(it, style = TextStyle(color = DANGER, fontSize = 12.sp))
        }

        Spacer(modifier = Modifier.size(16.dp))
        InkLabel("ITEMS")
        val counts = run.inventory.items.groupingBy { it }.eachCount()
        if (counts.isEmpty()) {
            BasicText("Bag is empty.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
        } else {
            counts.forEach { (itemId, count) ->
                val def = catalog.itemDef(itemId)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    BasicText("${def.name} x$count", style = TextStyle(color = INK, fontSize = 13.sp), modifier = Modifier.weight(1f))
                    // docs/47: Use is mid-run only — RunEffect.HealParty/DamageParty target
                    // run.party, which doesn't exist at the Hub (StashInventoryScreen below never
                    // renders a Use button at all, not because of a state.inCombat check here).
                    if (def.useEffects.isNotEmpty()) {
                        InkButton("Use", onClick = { onRunUpdated(useItemFromInventory(run, itemId, catalog)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StashInventoryScreen(meta: MetaState, catalog: Catalog, onMetaUpdated: (MetaState) -> Unit, onBack: () -> Unit) {
    var selectedId by remember { mutableStateOf(meta.roster.keys.firstOrNull()) }
    var message by remember { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { meta.roster[it] }

    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Inventory", style = TextStyle(color = INK, fontSize = 20.sp), modifier = Modifier.weight(1f))
            InkButton("Back", onClick = onBack)
        }
        Spacer(modifier = Modifier.size(16.dp))

        InkLabel("ROSTER")
        Row {
            meta.roster.values.forEach { record ->
                InkButton(
                    record.name,
                    modifier = Modifier.padding(end = 8.dp),
                    emphasized = record.id == selectedId,
                    onClick = { selectedId = record.id; message = null },
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        if (selected != null) {
            InkLabel("EQUIPMENT")
            val championId = selected.id
            Slot.entries.forEach { slot ->
                EquipmentSlotRow(slot, selected.equipment.slots[slot]?.def, catalog, meta.stash.items) { picked ->
                    message = null
                    val result = if (picked == null) {
                        unequipToStash(meta, championId, slot, catalog)
                    } else {
                        equipFromStash(meta, championId, slot, picked, catalog)
                    }
                    when (result) {
                        is MetaEquipmentTransactionResult.Applied -> onMetaUpdated(result.meta)
                        is MetaEquipmentTransactionResult.Rejected -> message = describeRejection(result.reasons)
                    }
                }
            }
        }
        message?.let {
            Spacer(modifier = Modifier.size(8.dp))
            BasicText(it, style = TextStyle(color = DANGER, fontSize = 12.sp))
        }

        Spacer(modifier = Modifier.size(16.dp))
        InkLabel("STASH")
        val counts = meta.stash.items.groupingBy { it }.eachCount()
        if (counts.isEmpty()) {
            BasicText("Stash is empty.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
        } else {
            counts.forEach { (itemId, count) ->
                BasicText("${catalog.itemDef(itemId).name} x$count", style = TextStyle(color = INK, fontSize = 13.sp), modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** One [Slot] row, shared by both screens — the closed-state label IS the equip/unequip control: picking "— empty —" unequips, picking an item equips it, no separate button needed. */
@Composable
private fun EquipmentSlotRow(slot: Slot, equippedItem: ItemId?, catalog: Catalog, pool: List<ItemId>, onPick: (ItemId?) -> Unit) {
    val eligible = pool.distinct().filter { id -> catalog.itemDef(id).validSlots.let { it.isEmpty() || slot in it } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        BasicText(slot.name, style = TextStyle(color = INK, fontSize = 12.sp), modifier = Modifier.width(90.dp))
        InkSelect(
            selected = equippedItem,
            options = listOf<ItemId?>(null) + eligible,
            label = { it?.let { id -> catalog.itemDef(id).name } ?: "— empty —" },
            onSelect = onPick,
        )
    }
}

private fun describeRejection(reasons: List<EquipmentTransactionRejection>): String =
    reasons.joinToString("; ") { reason ->
        when (reason) {
            is EquipmentTransactionRejection.CarryCapacityExceeded -> "Bag full (${reason.current}/${reason.capacity})"
            is EquipmentTransactionRejection.SlotRejected -> reason.reasons.joinToString(", ") { describeEquipRejection(it) }
        }
    }

private fun describeEquipRejection(reason: EquipRejection): String = when (reason) {
    EquipRejection.AttunementLimitReached -> "attunement limit reached (3 items)"
    EquipRejection.TwoHandedRequiresMainHand -> "two-handed items must go in Main Hand"
    is EquipRejection.OffHandMustBeEmptyForTwoHanded -> "Off Hand must be empty for a two-handed weapon"
    is EquipRejection.MainHandHoldsTwoHanded -> "Main Hand holds a two-handed weapon"
    is EquipRejection.SlotNotValidForItem -> "that item can't go in this slot"
}
