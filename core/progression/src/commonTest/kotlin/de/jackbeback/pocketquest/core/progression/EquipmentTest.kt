package de.jackbeback.pocketquest.core.progression

import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.ChampionRecord
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Equipment
import de.jackbeback.pocketquest.core.model.Inventory
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.Slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EquipmentTest {

    private fun record(id: String, equipment: Equipment = Equipment.EMPTY) =
        ChampionRecord(id = ChampionId(id), name = id, archetype = ArchetypeId("hero"), equipment = equipment)

    @Test
    fun equipFromStashMovesTheItemIntoTheSlotAndOutOfTheStash() {
        val sword = ItemDef(id = ItemId("sword"), name = "Sword", validSlots = setOf(Slot.MainHand))
        val cat = Catalog(items = mapOf(sword.id to sword))
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")), stash = Inventory(listOf(sword.id)))

        val result = equipFromStash(meta, ChampionId("m1"), Slot.MainHand, sword.id, cat) as MetaEquipmentTransactionResult.Applied
        val updated = result.meta.roster.getValue(ChampionId("m1"))
        assertEquals(ItemInstance(sword.id), updated.equipment.slots[Slot.MainHand])
        assertTrue(result.meta.stash.items.isEmpty())
    }

    @Test
    fun unequipToStashHasNoCarryCapacityLimit() {
        // Unlike the run-scoped Inventory, the Hub stash is unbounded home storage.
        val sword = ItemDef(id = ItemId("sword"), name = "Sword", validSlots = setOf(Slot.MainHand))
        val cat = Catalog(items = mapOf(sword.id to sword))
        val equipped = Equipment(mapOf(Slot.MainHand to ItemInstance(sword.id)))
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1", equipment = equipped)), stash = Inventory(List(50) { ItemId("junk") }))

        val result = unequipToStash(meta, ChampionId("m1"), Slot.MainHand, cat) as MetaEquipmentTransactionResult.Applied
        assertTrue(result.meta.roster.getValue(ChampionId("m1")).equipment.slots.isEmpty())
        assertEquals(51, result.meta.stash.items.size)
    }
}
