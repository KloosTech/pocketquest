package de.jackbeback.pocketquest.core.rules.equipment

import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.stat.stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EquipTest {

    @Test
    fun equippingIntoAnEmptySlotSucceeds() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("ring") { modifier(Modifier.Add(Stat.MaxHp, 2)) }
            entity("hero") { archetype("hero"); at(0, 0) }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.Ring1, ItemInstance(ItemId("ring")), s.catalog)
        val equipped = assertIs<EquipResult.Equipped>(result)
        assertEquals(ItemId("ring"), equipped.entity.equipment.slots[Slot.Ring1]?.def)
    }

    @Test
    fun twoHandedWeaponCanOnlyGoInMainHand() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("greatsword") { twoHanded = true }
            entity("hero") { archetype("hero"); at(0, 0) }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.OffHand, ItemInstance(ItemId("greatsword")), s.catalog)
        val rejected = assertIs<EquipResult.Rejected>(result)
        assertEquals(listOf(EquipRejection.TwoHandedRequiresMainHand), rejected.reasons)
    }

    @Test
    fun twoHandedWeaponRequiresOffHandToBeEmpty() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("greatsword") { twoHanded = true }
            itemDef("shield") {}
            entity("hero") { archetype("hero"); at(0, 0); equip(Slot.OffHand, "shield") }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.MainHand, ItemInstance(ItemId("greatsword")), s.catalog)
        val rejected = assertIs<EquipResult.Rejected>(result)
        assertEquals(listOf(EquipRejection.OffHandMustBeEmptyForTwoHanded(ItemId("shield"))), rejected.reasons)
    }

    @Test
    fun equippingATwoHandedWeaponThenBlocksOffHand() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("greatsword") { twoHanded = true }
            itemDef("shield") {}
            entity("hero") { archetype("hero"); at(0, 0); equip(Slot.MainHand, "greatsword") }
        }
        val hero = s.entity("hero")
        // Sanity: the two-handed weapon is stored ONLY in MainHand, never mirrored into OffHand.
        assertNull(hero.equipment.slots[Slot.OffHand])

        val result = equip(hero, Slot.OffHand, ItemInstance(ItemId("shield")), s.catalog)
        val rejected = assertIs<EquipResult.Rejected>(result)
        assertEquals(listOf(EquipRejection.MainHandHoldsTwoHanded(ItemId("greatsword"))), rejected.reasons)
    }

    @Test
    fun aTwoHandedWeaponsModifiersApplyExactlyOnce() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("greatsword") { twoHanded = true; modifier(Modifier.Add(Stat.MaxHp, 10)) }
            entity("hero") { archetype("hero"); at(0, 0); equip(Slot.MainHand, "greatsword") }
        }
        val hero = s.entity("hero")
        assertEquals(30, hero.stats(s.catalog).maxHp, "20 base + 10 from the greatsword, not 20 from a double-counted second copy")
    }

    @Test
    fun attunementLimitRejectsAFourthAttunedItem() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("ring1") {}
            itemDef("ring2") {}
            itemDef("amulet") {}
            itemDef("helm") {}
            entity("hero") {
                archetype("hero"); at(0, 0)
                equip(Slot.Ring1, "ring1", attuned = true)
                equip(Slot.Ring2, "ring2", attuned = true)
                equip(Slot.Amulet, "amulet", attuned = true)
            }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.Helm, ItemInstance(ItemId("helm"), attuned = true), s.catalog)
        val rejected = assertIs<EquipResult.Rejected>(result)
        assertEquals(listOf(EquipRejection.AttunementLimitReached), rejected.reasons)
    }

    @Test
    fun aNonAttunedFourthItemIsUnaffectedByTheAttunementLimit() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("ring1") {}
            itemDef("ring2") {}
            itemDef("amulet") {}
            itemDef("helm") {}
            entity("hero") {
                archetype("hero"); at(0, 0)
                equip(Slot.Ring1, "ring1", attuned = true)
                equip(Slot.Ring2, "ring2", attuned = true)
                equip(Slot.Amulet, "amulet", attuned = true)
            }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.Helm, ItemInstance(ItemId("helm"), attuned = false), s.catalog)
        assertIs<EquipResult.Equipped>(result)
    }

    @Test
    fun replacingAnAlreadyAttunedItemInTheSameSlotDoesNotCountAgainstItself() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("ring1") {}
            itemDef("ring2") {}
            itemDef("amulet") {}
            itemDef("betterAmulet") {}
            entity("hero") {
                archetype("hero"); at(0, 0)
                equip(Slot.Ring1, "ring1", attuned = true)
                equip(Slot.Ring2, "ring2", attuned = true)
                equip(Slot.Amulet, "amulet", attuned = true)
            }
        }
        val hero = s.entity("hero")
        // Already at the limit (3), but swapping the item IN Slot.Amulet itself must not count
        // the old amulet against the new one — it's being replaced, not added alongside.
        val result = equip(hero, Slot.Amulet, ItemInstance(ItemId("betterAmulet"), attuned = true), s.catalog)
        assertIs<EquipResult.Equipped>(result)
    }

    @Test
    fun validSlotsRejectsEquippingIntoADisallowedSlot() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("ring") { validSlots(Slot.Ring1, Slot.Ring2) }
            entity("hero") { archetype("hero"); at(0, 0) }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.Helm, ItemInstance(ItemId("ring")), s.catalog)
        val rejected = assertIs<EquipResult.Rejected>(result)
        assertEquals(listOf(EquipRejection.SlotNotValidForItem(ItemId("ring"), Slot.Helm)), rejected.reasons)
    }

    @Test
    fun validSlotsAllowsEquippingIntoAnAllowedSlot() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("ring") { validSlots(Slot.Ring1, Slot.Ring2) }
            entity("hero") { archetype("hero"); at(0, 0) }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.Ring2, ItemInstance(ItemId("ring")), s.catalog)
        assertIs<EquipResult.Equipped>(result)
    }

    @Test
    fun emptyValidSlotsMeansUnconstrained() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("ring") {}
            entity("hero") { archetype("hero"); at(0, 0) }
        }
        val hero = s.entity("hero")
        val result = equip(hero, Slot.Helm, ItemInstance(ItemId("ring")), s.catalog)
        assertIs<EquipResult.Equipped>(result, "no validSlots set at all — every pre-existing ItemDef must keep working unchanged")
    }

    @Test
    fun unequippingATwoHandedWeaponFreesOffHand() {
        val s = scenario {
            map(5, 5)
            archetype("hero") { hp = 20 }
            itemDef("greatsword") { twoHanded = true }
            itemDef("shield") {}
            entity("hero") { archetype("hero"); at(0, 0); equip(Slot.MainHand, "greatsword") }
        }
        val hero = unequip(s.entity("hero"), Slot.MainHand)
        val result = equip(hero, Slot.OffHand, ItemInstance(ItemId("shield")), s.catalog)
        assertTrue(result is EquipResult.Equipped)
    }
}
