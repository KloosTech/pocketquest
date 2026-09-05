package de.jackbeback.pocketquest.core.progression

import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.ChampionRecord
import de.jackbeback.pocketquest.core.meta.ChampionStatus
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.meta.Unlock
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Equipment
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.run.GraphNode
import de.jackbeback.pocketquest.core.run.MemberId
import de.jackbeback.pocketquest.core.run.NodeGraph
import de.jackbeback.pocketquest.core.run.NodeId
import de.jackbeback.pocketquest.core.run.PartyMember
import de.jackbeback.pocketquest.core.run.RunId
import de.jackbeback.pocketquest.core.run.RunOutcome
import de.jackbeback.pocketquest.core.run.RunState
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.Inventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunResolutionTest {

    private fun record(id: String, status: ChampionStatus = ChampionStatus.OnRun) = ChampionRecord(
        id = ChampionId(id), name = id, archetype = ArchetypeId("hero"), status = status,
    )

    private fun run(vararg memberIds: String, outcome: RunOutcome?, gold: Int = 0, equipment: Equipment = Equipment.EMPTY, inventory: Inventory = Inventory()) = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Combat)), start = NodeId("n1")),
        position = NodeId("n1"),
        party = memberIds.map { PartyMember(MemberId(it), it, ArchetypeId("hero"), hp = 20, mana = 5, equipment = equipment, controller = Controller.Human) },
        gold = gold, outcome = outcome, inventory = inventory,
    )

    @Test
    fun resolveRunOutcomeRequiresAnOutcomeToBeSet() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")))
        assertFailsWith<IllegalArgumentException> { resolveRunOutcome(meta, run("m1", outcome = null)) }
    }

    @Test
    fun successDepositsGoldAndWritesBackEquipmentAndAvailableStatus() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")), bank = 10)
        val newEquipment = Equipment(mapOf(Slot.MainHand to ItemInstance(ItemId("sword"))))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Success, gold = 25, equipment = newEquipment))

        val updated = result.roster.getValue(ChampionId("m1"))
        assertEquals(ChampionStatus.Available, updated.status)
        assertEquals(newEquipment, updated.equipment)
        assertEquals(35, result.bank)
    }

    @Test
    fun successGrantsPartyModeUnlockWhenNotAlreadyGranted() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Success))
        assertTrue(Unlock.PartyMode in result.unlocks)
    }

    @Test
    fun successIsIdempotentWhenPartyModeAlreadyGranted() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")), unlocks = setOf(Unlock.PartyMode))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Success))
        assertEquals(setOf(Unlock.PartyMode), result.unlocks)
    }

    @Test
    fun failureRemovesOnlyThisRunsChampionsFromTheRoster() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1"), ChampionId("m2") to record("m2", ChampionStatus.Available)))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Failure))
        assertFalse(ChampionId("m1") in result.roster)
        assertTrue(ChampionId("m2") in result.roster)
    }

    @Test
    fun failureThatEmptiesTheRosterRevokesPartyMode() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")), unlocks = setOf(Unlock.PartyMode))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Failure))
        assertTrue(result.roster.isEmpty())
        assertFalse(Unlock.PartyMode in result.unlocks)
    }

    @Test
    fun failureThatDoesNotEmptyTheRosterKeepsPartyModeGranted() {
        val meta = MetaState(
            roster = mapOf(ChampionId("m1") to record("m1"), ChampionId("m2") to record("m2", ChampionStatus.Available)),
            unlocks = setOf(Unlock.PartyMode),
        )
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Failure))
        assertTrue(Unlock.PartyMode in result.unlocks)
    }

    @Test
    fun successBanksTheRunsUnequippedItemsIntoTheStash() {
        // docs/47-inventory-screen.md: same "gains bank only on survival" rule gold already follows.
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")), stash = Inventory(listOf(ItemId("oldPotion"))))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Success, inventory = Inventory(listOf(ItemId("newPotion")))))
        assertEquals(listOf(ItemId("oldPotion"), ItemId("newPotion")), result.stash.items)
    }

    @Test
    fun failureForfeitsTheRunsUnequippedItems() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Failure, inventory = Inventory(listOf(ItemId("lostPotion")))))
        assertTrue(result.stash.items.isEmpty())
    }

    @Test
    fun successDoesNotTouchChampionsNotInTheParty() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1"), ChampionId("m2") to record("m2", ChampionStatus.OnMission)))
        val result = resolveRunOutcome(meta, run("m1", outcome = RunOutcome.Success))
        assertEquals(ChampionStatus.OnMission, result.roster.getValue(ChampionId("m2")).status)
    }
}
