package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.model.NodeGraph
import de.jackbeback.pocketquest.core.model.NodeId

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Inventory
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.LootId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docs/38-loot-reveal-screen.md: [revealLoot]/[skipAllLootReveals] — granting deferred out of `finishEncounter`, see `EncounterHandoffTest`. */
class LootRevealTest {

    private val hero = Archetype(
        id = ArchetypeId("hero"), name = "Hero",
        abilities = AbilityScores(1, 10, 10, 10, 10, 10), // STR 1 -> carryCapacity == 1
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private val cat = Catalog(archetypes = mapOf(hero.id to hero))

    private fun run(pending: List<PendingLoot>, items: List<ItemId> = emptyList()) = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Combat)), start = NodeId("n1")),
        position = NodeId("n1"),
        party = listOf(PartyMember(MemberId("m1"), name = "Lyra", archetype = hero.id, hp = 20, mana = 5, controller = Controller.Human)),
        inventory = Inventory(items),
        pendingLootReveal = pending,
    )

    @Test
    fun revealLootGrantsTheItemAndMarksRevealed() {
        val pending = PendingLoot(at = GridPos(0, 0), loot = LootId("chest1"), item = ItemId("potion"))
        val result = revealLoot(run(listOf(pending)), pending.at, cat)

        assertEquals(listOf(ItemId("potion")), result.inventory.items)
        val updated = result.pendingLootReveal.single()
        assertTrue(updated.revealed)
        assertEquals(false, updated.lost)
    }

    @Test
    fun revealLootOnNothingMarksRevealedWithoutTouchingInventory() {
        val pending = PendingLoot(at = GridPos(0, 0), loot = LootId("chest1"), item = null)
        val result = revealLoot(run(listOf(pending)), pending.at, cat)

        assertEquals(emptyList(), result.inventory.items)
        val updated = result.pendingLootReveal.single()
        assertTrue(updated.revealed)
        assertEquals(false, updated.lost)
    }

    @Test
    fun revealLootMarksLostWhenCapacityIsAlreadyFull() {
        val pending = PendingLoot(at = GridPos(0, 0), loot = LootId("chest1"), item = ItemId("sword"))
        // STR 1 -> capacity 1, already carrying one item.
        val result = revealLoot(run(listOf(pending), items = listOf(ItemId("already-carried"))), pending.at, cat)

        assertEquals(listOf(ItemId("already-carried")), result.inventory.items, "the sword shouldn't fit")
        val updated = result.pendingLootReveal.single()
        assertTrue(updated.revealed)
        assertTrue(updated.lost)
    }

    @Test
    fun revealLootIsANoOpForAnAlreadyRevealedEntry() {
        val pending = PendingLoot(at = GridPos(0, 0), loot = LootId("chest1"), item = ItemId("potion"), revealed = true)
        val original = run(listOf(pending))

        val result = revealLoot(original, pending.at, cat)

        assertEquals(original, result, "a repeat tap on a resolved chest must change nothing")
    }

    @Test
    fun revealLootIsANoOpForAnUnknownPosition() {
        val original = run(emptyList())
        val result = revealLoot(original, GridPos(9, 9), cat)
        assertEquals(original, result)
    }

    @Test
    fun skipAllLootRevealsResolvesEveryEntryInOrderRespectingCapacityAsItGoes() {
        // STR 1 -> capacity 1 — the first pending entry fits, the second (revealed second, same
        // order as the list) doesn't.
        val first = PendingLoot(at = GridPos(0, 0), loot = LootId("chest1"), item = ItemId("potion"))
        val second = PendingLoot(at = GridPos(1, 0), loot = LootId("chest2"), item = ItemId("sword"))

        val result = skipAllLootReveals(run(listOf(first, second)), cat)

        assertEquals(listOf(ItemId("potion")), result.inventory.items)
        assertTrue(result.pendingLootReveal.all { it.revealed })
        assertEquals(false, result.pendingLootReveal.single { it.at == first.at }.lost)
        assertTrue(result.pendingLootReveal.single { it.at == second.at }.lost)
    }
}
