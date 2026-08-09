package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json

private fun dummyArchetype() = Archetype(
    id = ArchetypeId("dummy"), name = "Dummy",
    abilities = AbilityScores(10, 10, 10, 10, 10, 10),
    baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
)

private fun catalog() = Catalog(archetypes = mapOf(ArchetypeId("dummy") to dummyArchetype()))

private fun member(id: String, hp: Int = 20, mana: Int = 5, condition: MemberCondition = MemberCondition.Healthy) = PartyMember(
    memberId = MemberId(id), name = id, archetype = ArchetypeId("dummy"), hp = hp, mana = mana,
    controller = Controller.Human, condition = condition,
)

private fun encounterSpec() = EncounterSpec(id = EncounterId("e1"), name = "Test Encounter", mapId = MapId("m1"))

private fun graph(vararg ids: String) = NodeGraph(
    nodes = ids.associate { NodeId(it) to GraphNode(NodeId(it), act = 1, type = NodeType.Combat) },
    start = NodeId(ids.first()),
)

private fun runState(
    party: List<PartyMember> = listOf(member("m1")),
    graph: NodeGraph = graph("n1", "n2"),
    position: NodeId = NodeId("n1"),
    visited: Set<NodeId> = emptySet(),
    encounter: EncounterHandle? = null,
    outcome: RunOutcome? = null,
) = RunState(
    runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
    graph = graph, position = position, visited = visited, party = party,
    encounter = encounter, outcome = outcome,
)

class RunStateTest {

    @Test
    fun runStateRoundTripsThroughJson() {
        val state = runState()
        val encoded = json.encodeToString(RunState.serializer(), state)
        val decoded = json.decodeFromString(RunState.serializer(), encoded)
        assertEquals(state, decoded)
    }

    @Test
    fun aWellFormedRunHasNoViolations() {
        assertEquals(emptyList(), checkRunInvariants(runState(), catalog()))
    }

    @Test
    fun emptyPartyIsAViolation() {
        val violations = checkRunInvariants(runState(party = emptyList()), catalog())
        assertTrue(violations.any { it.contains("party is empty") })
    }

    @Test
    fun aPartyOfFourExceedsTheCap() {
        val party = listOf(member("m1"), member("m2"), member("m3"), member("m4"))
        val violations = checkRunInvariants(runState(party = party), catalog())
        assertTrue(violations.any { it.contains("more than the max of 3") })
    }

    @Test
    fun duplicateMemberIdsAreAViolation() {
        val party = listOf(member("m1"), member("m1"))
        val violations = checkRunInvariants(runState(party = party), catalog())
        assertTrue(violations.any { it.contains("m1") && it.contains("more than once") })
    }

    @Test
    fun hpAboveDerivedMaxIsAViolation() {
        val violations = checkRunInvariants(runState(party = listOf(member("m1", hp = 999))), catalog())
        assertTrue(violations.any { it.contains("hp 999 outside 0..20") })
    }

    @Test
    fun zeroHpNotMarkedDownedIsAViolation() {
        val violations = checkRunInvariants(runState(party = listOf(member("m1", hp = 0, condition = MemberCondition.Healthy))), catalog())
        assertTrue(violations.any { it.contains("condition Healthy disagrees with hp 0") })
    }

    @Test
    fun downedAtNonzeroHpIsAViolation() {
        val violations = checkRunInvariants(runState(party = listOf(member("m1", hp = 5, condition = MemberCondition.Downed))), catalog())
        assertTrue(violations.any { it.contains("condition Downed disagrees with hp 5") })
    }

    @Test
    fun positionOutsideGraphIsAViolation() {
        val violations = checkRunInvariants(runState(position = NodeId("ghost")), catalog())
        assertTrue(violations.any { it.contains("position ghost is not a node in graph") })
    }

    @Test
    fun visitedNodeOutsideGraphIsAViolation() {
        val violations = checkRunInvariants(runState(visited = setOf(NodeId("ghost"))), catalog())
        assertTrue(violations.any { it.contains("visited node ghost is not a node in graph") })
    }

    @Test
    fun activeEncounterMissingAMappedMemberIsAViolation() {
        val handle = EncounterHandle(
            resolver = de.jackbeback.pocketquest.core.rules.resolver.Resolver(
                state = de.jackbeback.pocketquest.core.model.GameState(
                    entities = emptyList(),
                    map = de.jackbeback.pocketquest.core.model.BattleMap(5, 5),
                    turn = de.jackbeback.pocketquest.core.model.TurnState(round = 1, order = emptyList(), activeIndex = 0, phase = de.jackbeback.pocketquest.core.model.TurnPhase.Main),
                    rng = RngState(seed = 1L),
                ),
            ),
            memberToEntity = emptyMap(),
            spec = encounterSpec(),
        )
        val violations = checkRunInvariants(runState(encounter = handle), catalog())
        assertTrue(violations.any { it.contains("m1") && it.contains("no mapped EntityId") })
    }

    @Test
    fun activeEncounterWithEveryMemberMappedHasNoViolation() {
        val handle = EncounterHandle(
            resolver = de.jackbeback.pocketquest.core.rules.resolver.Resolver(
                state = de.jackbeback.pocketquest.core.model.GameState(
                    entities = emptyList(),
                    map = de.jackbeback.pocketquest.core.model.BattleMap(5, 5),
                    turn = de.jackbeback.pocketquest.core.model.TurnState(round = 1, order = emptyList(), activeIndex = 0, phase = de.jackbeback.pocketquest.core.model.TurnPhase.Main),
                    rng = RngState(seed = 1L),
                ),
            ),
            memberToEntity = mapOf(MemberId("m1") to EntityId(0)),
            spec = encounterSpec(),
        )
        assertEquals(emptyList(), checkRunInvariants(runState(encounter = handle), catalog()))
    }

    @Test
    fun outcomeSetWhileEncounterStillActiveIsAViolation() {
        val handle = EncounterHandle(
            resolver = de.jackbeback.pocketquest.core.rules.resolver.Resolver(
                state = de.jackbeback.pocketquest.core.model.GameState(
                    entities = emptyList(),
                    map = de.jackbeback.pocketquest.core.model.BattleMap(5, 5),
                    turn = de.jackbeback.pocketquest.core.model.TurnState(round = 1, order = emptyList(), activeIndex = 0, phase = de.jackbeback.pocketquest.core.model.TurnPhase.Main),
                    rng = RngState(seed = 1L),
                ),
            ),
            memberToEntity = mapOf(MemberId("m1") to EntityId(0)),
            spec = encounterSpec(),
        )
        val violations = checkRunInvariants(runState(encounter = handle, outcome = RunOutcome.Success), catalog())
        assertTrue(violations.any { it.contains("outcome Success is set but encounter is still active") })
    }
}
