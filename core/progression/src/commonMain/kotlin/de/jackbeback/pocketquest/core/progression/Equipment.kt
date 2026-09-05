package de.jackbeback.pocketquest.core.progression

import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.rules.equipment.EquipResult
import de.jackbeback.pocketquest.core.rules.equipment.equip
import de.jackbeback.pocketquest.core.rules.equipment.unequip
import de.jackbeback.pocketquest.core.run.EquipmentTransactionRejection

/**
 * docs/47-inventory-screen.md: the Hub-side counterpart to `:core:run`'s `EquipmentTransactionResult`
 * — same shape, `MetaState` in place of `RunState` (the Hub has no active run to hold one).
 */
sealed interface MetaEquipmentTransactionResult {
    data class Applied(val meta: MetaState) : MetaEquipmentTransactionResult
    data class Rejected(val reasons: List<EquipmentTransactionRejection>) : MetaEquipmentTransactionResult
}

/**
 * The Hub-side counterpart to `:core:run`'s `equipFromInventory`/`unequipToInventory` — same
 * "borrow a throwaway `Entity` to reuse `:core:rules`' equip logic" bridge, one level up
 * (`MetaState.stash` + `ChampionRecord.equipment` instead of `RunState.inventory` +
 * `PartyMember.equipment`). Only `archetype`/`equipment` matter to `equip()`/`unequip()`/
 * `canEquip()` themselves — id/pos/health/actor are irrelevant filler. No carry-capacity check on
 * unequip: the stash is unbounded home storage, unlike the run-scoped `Inventory`.
 */
fun equipFromStash(meta: MetaState, championId: ChampionId, slot: Slot, item: ItemId, cat: Catalog): MetaEquipmentTransactionResult {
    val record = meta.roster.getValue(championId)
    val entity = Entity(EntityId(0), record.archetype, pos = null, health = null, resources = null, actor = null, equipment = record.equipment)
    return when (val result = equip(entity, slot, ItemInstance(item), cat)) {
        is EquipResult.Rejected -> MetaEquipmentTransactionResult.Rejected(listOf(EquipmentTransactionRejection.SlotRejected(result.reasons)))
        is EquipResult.Equipped -> {
            val updatedRoster = meta.roster + (championId to record.copy(equipment = result.entity.equipment))
            val updatedStash = meta.stash.copy(items = meta.stash.items - item)
            MetaEquipmentTransactionResult.Applied(meta.copy(roster = updatedRoster, stash = updatedStash))
        }
    }
}

fun unequipToStash(meta: MetaState, championId: ChampionId, slot: Slot, cat: Catalog): MetaEquipmentTransactionResult {
    val record = meta.roster.getValue(championId)
    val equipped = record.equipment.slots[slot] ?: return MetaEquipmentTransactionResult.Applied(meta)
    val entity = Entity(EntityId(0), record.archetype, pos = null, health = null, resources = null, actor = null, equipment = record.equipment)
    val unequipped = unequip(entity, slot)
    val updatedRoster = meta.roster + (championId to record.copy(equipment = unequipped.equipment))
    val updatedStash = meta.stash.copy(items = meta.stash.items + equipped.def)
    return MetaEquipmentTransactionResult.Applied(meta.copy(roster = updatedRoster, stash = updatedStash))
}
