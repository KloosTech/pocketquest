package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.model.NodeGraph
import de.jackbeback.pocketquest.core.model.NodeId

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import kotlin.test.Test
import kotlin.test.assertEquals

class RestTest {

    private val hero = Archetype(
        id = ArchetypeId("hero"), name = "Hero",
        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private val cat = Catalog(archetypes = mapOf(hero.id to hero))

    private fun run(hp: Int, condition: MemberCondition = MemberCondition.Healthy) = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Rest)), start = NodeId("n1")),
        position = NodeId("n1"),
        party = listOf(PartyMember(MemberId("m1"), "Lyra", hero.id, hp = hp, mana = 5, controller = Controller.Human, condition = condition)),
    )

    @Test
    fun restHealsHalfOfMaxHpByDefault() {
        val result = applyRest(run(hp = 5), cat)
        assertEquals(15, result.party.single().hp) // 5 + 50% of 20
    }

    @Test
    fun restNeverExceedsMaxHp() {
        val result = applyRest(run(hp = 18), cat)
        assertEquals(20, result.party.single().hp)
    }

    @Test
    fun restCanReviveADownedMember() {
        val result = applyRest(run(hp = 0, condition = MemberCondition.Downed), cat)
        val member = result.party.single()
        assertEquals(10, member.hp)
        assertEquals(MemberCondition.Healthy, member.condition)
    }

    @Test
    fun customFractionIsRespected() {
        val result = applyRest(run(hp = 0), cat, fraction = 1.0)
        assertEquals(20, result.party.single().hp)
    }

    @Test
    fun markVisitedAddsCurrentPositionToVisited() {
        val marked = run(hp = 20).markVisited()
        assertEquals(setOf(NodeId("n1")), marked.visited)
    }
}
