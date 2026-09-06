package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.model.NodeGraph
import de.jackbeback.pocketquest.core.model.NodeId

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Equipment
import de.jackbeback.pocketquest.core.model.Inventory
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.RunEffect
import de.jackbeback.pocketquest.core.model.RunEffectTarget
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.rules.equipment.EquipRejection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EquipmentTest {

    private fun archetype(id: String, str: Int = 12) = Archetype(
        id = ArchetypeId(id), name = id,
        abilities = AbilityScores(str, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private fun member(id: String, archetype: ArchetypeId, equipment: Equipment = Equipment.EMPTY) =
        PartyMember(MemberId(id), name = id, archetype = archetype, hp = 20, mana = 5, equipment = equipment, controller = Controller.Human)

    private fun run(party: List<PartyMember>, inventory: Inventory = Inventory()) = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Shop)), start = NodeId("n1")),
        position = NodeId("n1"), party = party, inventory = inventory,
    )

    private fun catalogWithHero(str: Int = 12, items: Map<ItemId, ItemDef> = emptyMap()) = Catalog(
        archetypes = mapOf(ArchetypeId("hero") to archetype("hero", str)),
        items = items,
    )

    @Test
    fun equipFromInventoryMovesTheItemIntoTheSlotAndOutOfTheBag() {
        val sword = ItemDef(id = ItemId("sword"), name = "Sword", validSlots = setOf(Slot.MainHand))
        val cat = catalogWithHero(items = mapOf(sword.id to sword))
        val before = run(listOf(member("m1", ArchetypeId("hero"))), inventory = Inventory(listOf(sword.id)))

        val result = equipFromInventory(before, MemberId("m1"), Slot.MainHand, sword.id, cat) as EquipmentTransactionResult.Applied
        val updatedMember = result.run.party.single()
        assertEquals(ItemInstance(sword.id), updatedMember.equipment.slots[Slot.MainHand])
        assertTrue(result.run.inventory.items.isEmpty(), "the sword left the bag once equipped")
    }

    @Test
    fun equipFromInventoryRejectsAWrongSlot() {
        val ring = ItemDef(id = ItemId("ring"), name = "Ring", validSlots = setOf(Slot.Ring1, Slot.Ring2))
        val cat = catalogWithHero(items = mapOf(ring.id to ring))
        val before = run(listOf(member("m1", ArchetypeId("hero"))), inventory = Inventory(listOf(ring.id)))

        val result = equipFromInventory(before, MemberId("m1"), Slot.MainHand, ring.id, cat)
        check(result is EquipmentTransactionResult.Rejected)
        val slotRejected = result.reasons.single() as EquipmentTransactionRejection.SlotRejected
        assertTrue(slotRejected.reasons.any { it is EquipRejection.SlotNotValidForItem })
        // Rejected means unchanged — the item never left the bag.
        assertEquals(listOf(ring.id), before.inventory.items)
    }

    @Test
    fun unequipToInventoryMovesTheItemBackIntoTheBag() {
        val sword = ItemDef(id = ItemId("sword"), name = "Sword", validSlots = setOf(Slot.MainHand))
        val cat = catalogWithHero(items = mapOf(sword.id to sword))
        val equipped = Equipment(mapOf(Slot.MainHand to ItemInstance(sword.id)))
        val before = run(listOf(member("m1", ArchetypeId("hero"), equipment = equipped)))

        val result = unequipToInventory(before, MemberId("m1"), Slot.MainHand, cat) as EquipmentTransactionResult.Applied
        assertTrue(result.run.party.single().equipment.slots.isEmpty())
        assertEquals(listOf(sword.id), result.run.inventory.items)
    }

    @Test
    fun unequipToInventoryIsRejectedWhenTheBagIsFull() {
        // capacity 1 (str=1), bag already has 1 item — no room for the unequipped sword to land.
        val sword = ItemDef(id = ItemId("sword"), name = "Sword", validSlots = setOf(Slot.MainHand))
        val cat = catalogWithHero(str = 1, items = mapOf(sword.id to sword))
        val equipped = Equipment(mapOf(Slot.MainHand to ItemInstance(sword.id)))
        val before = run(listOf(member("m1", ArchetypeId("hero"), equipment = equipped)), inventory = Inventory(listOf(ItemId("junk"))))

        val result = unequipToInventory(before, MemberId("m1"), Slot.MainHand, cat)
        check(result is EquipmentTransactionResult.Rejected)
        assertTrue(result.reasons.any { it is EquipmentTransactionRejection.CarryCapacityExceeded })
    }

    @Test
    fun useItemFromInventoryAppliesEffectsAndRemovesOneCopy() {
        val potion = ItemDef(
            id = ItemId("potion"), name = "Potion",
            useEffects = listOf(RunEffect.HealParty(10, RunEffectTarget.LowestHpMember)),
        )
        val cat = catalogWithHero(items = mapOf(potion.id to potion))
        val hurt = member("m1", ArchetypeId("hero")).copy(hp = 5)
        val before = run(listOf(hurt), inventory = Inventory(listOf(potion.id, potion.id)))

        val after = useItemFromInventory(before, potion.id, cat)
        assertEquals(15, after.party.single().hp)
        assertEquals(listOf(potion.id), after.inventory.items, "only one copy consumed")
    }
}
