package de.jackbeback.pocketquest.core.rules.equipment

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.Slot

sealed interface EquipRejection {
    /** More than 3 attuned items would result — see docs/03-modifiers-and-status.md. */
    data object AttunementLimitReached : EquipRejection

    /** A two-handed item can only go in MainHand; OffHand comes along with it, not as a separate equip(). */
    data object TwoHandedRequiresMainHand : EquipRejection

    /** Equipping a two-handed item into MainHand, but OffHand is already occupied. */
    data class OffHandMustBeEmptyForTwoHanded(val occupiedBy: ItemId) : EquipRejection

    /** Equipping anything into OffHand while MainHand holds a two-handed weapon. */
    data class MainHandHoldsTwoHanded(val weapon: ItemId) : EquipRejection

    /** doc20: `ItemDef.validSlots` — empty means unconstrained, so this only ever fires for an item that names a restricted slot set the target isn't in. */
    data class SlotNotValidForItem(val item: ItemId, val slot: Slot) : EquipRejection
}

sealed interface EquipResult {
    data class Equipped(val entity: Entity) : EquipResult
    data class Rejected(val reasons: List<EquipRejection>) : EquipResult
}

/**
 * Same "one function, three consumers" shape as `canPerform`/`perform` in
 * docs/05-actions-and-effects.md, applied to equipment instead of actions.
 *
 * A two-handed item is stored ONLY in `MainHand` — never mirrored into
 * `OffHand` too. `stats()` in StatsDerivation.kt applies one `addAll` per
 * *occupied slot*; storing the same item twice would double its modifiers.
 * "Occupies MainHand and OffHand" is enforced here procedurally instead:
 * equipping a two-handed item requires OffHand to already be empty, and
 * equipping anything into OffHand is rejected while MainHand holds one.
 */
fun canEquip(entity: Entity, slot: Slot, item: ItemInstance, cat: Catalog): List<EquipRejection> {
    val rejections = mutableListOf<EquipRejection>()
    val itemDef = cat.itemDef(item.def)

    if (itemDef.validSlots.isNotEmpty() && slot !in itemDef.validSlots) {
        rejections += EquipRejection.SlotNotValidForItem(item.def, slot)
    }

    if (item.attuned) {
        val attunedElsewhere = entity.equipment.slots.entries.count { (s, i) -> s != slot && i.attuned }
        if (attunedElsewhere >= 3) rejections += EquipRejection.AttunementLimitReached
    }

    if (itemDef.twoHanded) {
        if (slot != Slot.MainHand) {
            rejections += EquipRejection.TwoHandedRequiresMainHand
        } else {
            entity.equipment.slots[Slot.OffHand]?.let { rejections += EquipRejection.OffHandMustBeEmptyForTwoHanded(it.def) }
        }
    } else if (slot == Slot.OffHand) {
        entity.equipment.slots[Slot.MainHand]?.let { mainHand ->
            if (cat.itemDef(mainHand.def).twoHanded) rejections += EquipRejection.MainHandHoldsTwoHanded(mainHand.def)
        }
    }

    return rejections
}

fun equip(entity: Entity, slot: Slot, item: ItemInstance, cat: Catalog): EquipResult {
    val rejections = canEquip(entity, slot, item, cat)
    if (rejections.isNotEmpty()) return EquipResult.Rejected(rejections)
    return EquipResult.Equipped(entity.copy(equipment = entity.equipment.copy(slots = entity.equipment.slots + (slot to item))))
}

fun unequip(entity: Entity, slot: Slot): Entity =
    entity.copy(equipment = entity.equipment.copy(slots = entity.equipment.slots - slot))
