package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.LootEntry
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.SpawnZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * docs/11-run-state.md's "The encounter handoff" — scenario-level tests, not the resolver's own
 * turn loop: [finishEncounter] takes an already-resolved `GameState` as an opaque input, so these
 * build the "after combat" state by hand instead of actually running AI turns.
 */
class EncounterHandoffTest {

    private val hero = Archetype(
        id = ArchetypeId("hero"), name = "Hero",
        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private val map = BattleMapDef(
        id = MapId("room"), width = 3, height = 3,
        spawns = listOf(SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0), GridPos(1, 0)))),
    )

    private fun spec(goldMin: Int = 5, goldMax: Int = 5, loot: List<LootEntry> = emptyList()) = EncounterSpec(
        id = EncounterId("e1"), name = "E1", mapId = map.id, goldMin = goldMin, goldMax = goldMax, loot = loot,
    )

    private fun catalog() = Catalog(archetypes = mapOf(hero.id to hero), maps = mapOf(map.id to map))

    private fun run(hp: Int = 20, mana: Int = 5, condition: MemberCondition = MemberCondition.Healthy) = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Combat)), start = NodeId("n1")),
        position = NodeId("n1"),
        party = listOf(
            PartyMember(MemberId("m1"), name = "Lyra", archetype = hero.id, hp = hp, mana = mana, controller = Controller.Human, condition = condition),
        ),
    )

    @Test
    fun startEncounterSpawnsTheActivePartyAndStoresTheHandle() {
        val started = startEncounter(run(), spec(), catalog())
        val handle = started.encounter
        checkNotNull(handle)
        assertEquals(setOf(MemberId("m1")), handle.memberToEntity.keys)
        val entity = handle.resolver.state.entities.single()
        assertEquals(20, entity.health?.current)
        assertEquals(5, entity.resources?.mana)
    }

    @Test
    fun downedMembersAreNotSpawnedIntoTheEncounter() {
        val started = startEncounter(run(hp = 0, condition = MemberCondition.Downed), spec(), catalog())
        assertEquals(emptyMap(), started.encounter?.memberToEntity)
        assertEquals(0, started.encounter?.resolver?.state?.entities?.size)
    }

    @Test
    fun winningWritesBackHpManaLootAndGold() {
        val cat = catalog()
        val started = startEncounter(run(), spec(loot = listOf(LootEntry(ItemId("potion"), chance = 1.0))), cat)
        val handle = checkNotNull(started.encounter)
        val entityId = handle.memberToEntity.getValue(MemberId("m1"))
        val state = handle.resolver.state
        val damaged = state.entities.single { it.id == entityId }.copy(health = Health(current = 7))
        val final = state.copy(entities = listOf(damaged))

        val finished = finishEncounter(started, final, cat)

        val member = finished.party.single()
        assertEquals(7, member.hp)
        assertEquals(5, member.mana)
        assertEquals(MemberCondition.Healthy, member.condition)
        assertEquals(listOf(ItemId("potion")), finished.inventory.items)
        assertEquals(5, finished.gold)
        assertNull(finished.encounter)
        assertNull(finished.outcome)
    }

    @Test
    fun aDownedSurvivorGetsUpAtOneHpWhenNotEveryoneWiped() {
        val cat = catalog()
        // Two active fighters, only one goes down — not a wipe, so the down member revives at 1 HP
        // instead of triggering RunOutcome.Failure.
        val twoMemberRun = run().copy(
            party = run().party + PartyMember(MemberId("m2"), "Kael", hero.id, hp = 20, mana = 5, controller = Controller.Human),
        )
        val started = startEncounter(twoMemberRun, spec(), cat)
        val handle = checkNotNull(started.encounter)
        val state = handle.resolver.state
        val downedId = handle.memberToEntity.getValue(MemberId("m1"))
        val survivorId = handle.memberToEntity.getValue(MemberId("m2"))
        val downed = state.entities.single { it.id == downedId }.copy(health = Health(current = -3))
        val survivor = state.entities.single { it.id == survivorId }.copy(health = Health(current = 12))
        val final = state.copy(entities = listOf(downed, survivor))

        val finished = finishEncounter(started, final, cat)

        val revived = finished.party.single { it.memberId == MemberId("m1") }
        assertEquals(1, revived.hp)
        assertEquals(MemberCondition.Healthy, revived.condition)
        val kept = finished.party.single { it.memberId == MemberId("m2") }
        assertEquals(12, kept.hp)
        assertNull(finished.outcome)
    }

    @Test
    fun aFullPartyWipeSetsFailureAndSkipsWriteBack() {
        val cat = catalog()
        val originalRun = run()
        val started = startEncounter(originalRun, spec(loot = listOf(LootEntry(ItemId("potion"), chance = 1.0))), cat)
        val handle = checkNotNull(started.encounter)
        val entityId = handle.memberToEntity.getValue(MemberId("m1"))
        val state = handle.resolver.state
        val downed = state.entities.single { it.id == entityId }.copy(health = Health(current = 0))
        val final = state.copy(entities = listOf(downed))

        val finished = finishEncounter(started, final, cat)

        assertEquals(RunOutcome.Failure, finished.outcome)
        assertNull(finished.encounter)
        assertEquals(originalRun.party, finished.party)
        assertEquals(originalRun.gold, finished.gold)
        assertEquals(originalRun.inventory, finished.inventory)
    }
}
