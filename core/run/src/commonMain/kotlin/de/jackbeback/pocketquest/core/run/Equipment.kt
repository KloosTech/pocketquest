package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.rules.equipment.EquipRejection
import de.jackbeback.pocketquest.core.rules.equipment.EquipResult
import de.jackbeback.pocketquest.core.rules.equipment.canEquip
import de.jackbeback.pocketquest.core.rules.equipment.equip
import de.jackbeback.pocketquest.core.rules.equipment.unequip

/**
 * docs/47-inventory-screen.md: `:core:rules`' `equip()`/`unequip()`/`canEquip()` operate on a
 * combat `Entity`, not a `RunState`-level `PartyMember` — this bridges that gap the same way
 * `atFullHealth`/`toEntity` already do to borrow `Entity`-level `stats()` outside a real battle.
 */
sealed interface EquipmentTransactionRejection {
    data class SlotRejected(val reasons: List<EquipRejection>) : EquipmentTransactionRejection
    data class CarryCapacityExceeded(val capacity: Int, val current: Int) : EquipmentTransactionRejection
}

sealed interface EquipmentTransactionResult {
    data class Applied(val run: RunState) : EquipmentTransactionResult
    data class Rejected(val reasons: List<EquipmentTransactionRejection>) : EquipmentTransactionResult
}

/**
 * Moves [item] out of [RunState.inventory] into [memberId]'s [slot] — builds a throwaway `Entity`
 * from the `PartyMember` purely to reuse `:core:rules`' slot-validity/attunement/two-handed checks,
 * then writes the resulting equipment back onto the real `PartyMember` in [run]. Mid-fight, this
 * only ever touches `RunState`/`PartyMember` — never the live battle `Entity` (docs/47: equip
 * changes apply next fight, not the current one).
 */
fun equipFromInventory(run: RunState, memberId: MemberId, slot: Slot, item: ItemId, cat: Catalog): EquipmentTransactionResult {
    val member = run.party.first { it.memberId == memberId }
    val entity = member.toEntity(cat)
    return when (val result = equip(entity, slot, ItemInstance(item), cat)) {
        is EquipResult.Rejected -> EquipmentTransactionResult.Rejected(listOf(EquipmentTransactionRejection.SlotRejected(result.reasons)))
        is EquipResult.Equipped -> {
            val updatedParty = run.party.map { if (it.memberId == memberId) it.copy(equipment = result.entity.equipment) else it }
            val updatedInventory = run.inventory.copy(items = run.inventory.items - item)
            EquipmentTransactionResult.Applied(run.copy(party = updatedParty, inventory = updatedInventory))
        }
    }
}

/**
 * Moves whatever's in [memberId]'s [slot] back into [RunState.inventory] — capacity-checked
 * BEFORE committing, same question `canBuy` (`Shop.kt`) already asks of a purchase: does this item
 * fit in the run's bag. `unequip()` itself is unconditional (no `EquipRejection` of its own).
 */
fun unequipToInventory(run: RunState, memberId: MemberId, slot: Slot, cat: Catalog): EquipmentTransactionResult {
    val member = run.party.first { it.memberId == memberId }
    val equipped = member.equipment.slots[slot] ?: return EquipmentTransactionResult.Applied(run)
    val capacity = carryCapacity(run.party, cat)
    if (run.inventory.items.size >= capacity) {
        return EquipmentTransactionResult.Rejected(listOf(EquipmentTransactionRejection.CarryCapacityExceeded(capacity, run.inventory.items.size)))
    }
    val entity = member.toEntity(cat)
    val unequipped = unequip(entity, slot)
    val updatedParty = run.party.map { if (it.memberId == memberId) it.copy(equipment = unequipped.equipment) else it }
    val updatedInventory = run.inventory.copy(items = run.inventory.items + equipped.def)
    return EquipmentTransactionResult.Applied(run.copy(party = updatedParty, inventory = updatedInventory))
}
