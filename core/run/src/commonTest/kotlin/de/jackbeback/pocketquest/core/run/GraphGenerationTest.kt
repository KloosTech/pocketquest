package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.RngState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphGenerationTest {

    @Test
    fun sameSeedProducesTheSameGraph() {
        val (a, _) = generateGraph(RngState(seed = 42L))
        val (b, _) = generateGraph(RngState(seed = 42L))
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsCanProduceDifferentGraphs() {
        val (a, _) = generateGraph(RngState(seed = 1L))
        val (b, _) = generateGraph(RngState(seed = 2L))
        assertTrue(a != b, "vanishingly unlikely two different seeds land on identical node-type assignments")
    }

    @Test
    fun everyActIsRepresented() {
        val (graph, _) = generateGraph(RngState(seed = 1L), acts = 3, lanes = 2)
        val actsSeen = graph.nodes.values.map { it.act }.toSet()
        assertEquals(setOf(1, 2, 3), actsSeen)
    }

    @Test
    fun eachNonFinalActHasExactlyLanesNodes() {
        val (graph, _) = generateGraph(RngState(seed = 1L), acts = 3, lanes = 3)
        assertEquals(3, graph.nodes.values.count { it.act == 1 })
        assertEquals(3, graph.nodes.values.count { it.act == 2 })
        assertEquals(1, graph.nodes.values.count { it.act == 3 })
    }

    @Test
    fun exactlyOneBossNodeAndItHasNoNext() {
        val (graph, _) = generateGraph(RngState(seed = 1L))
        val bosses = graph.nodes.values.filter { it.type == NodeType.Boss }
        assertEquals(1, bosses.size)
        assertEquals(emptyList(), bosses.single().next)
    }

    @Test
    fun everyNonBossNodesNextPointsAtRealNodes() {
        val (graph, _) = generateGraph(RngState(seed = 7L))
        for (node in graph.nodes.values) {
            if (node.type == NodeType.Boss) continue
            assertTrue(node.next.isNotEmpty(), "non-boss node ${node.id.raw} must lead somewhere")
            for (next in node.next) {
                assertTrue(next in graph.nodes, "node ${node.id.raw} points at unknown node ${next.raw}")
            }
        }
    }

    @Test
    fun startIsARealNodeInAct1() {
        val (graph, _) = generateGraph(RngState(seed = 3L))
        val startNode = graph.nodes.getValue(graph.start)
        assertEquals(1, startNode.act)
    }

    @Test
    fun aSingleActCollapsesStraightToTheBoss() {
        val (graph, _) = generateGraph(RngState(seed = 1L), acts = 1, lanes = 2)
        assertEquals(1, graph.nodes.size)
        assertEquals(NodeType.Boss, graph.nodes.getValue(graph.start).type)
    }

    @Test
    fun createRunBuildsAPlayableRunFromASeed() {
        val party = listOf(
            PartyMember(MemberId("m1"), "Lyra", de.jackbeback.pocketquest.core.model.ArchetypeId("hero"), hp = 20, mana = 5, controller = de.jackbeback.pocketquest.core.model.Controller.Human),
        )
        val run = createRun(RunId("run1"), seed = 99L, party = party)
        assertEquals(run.position, run.graph.start)
        assertEquals(1, run.act)
        assertTrue(run.rng.calls > 0, "graph generation should have consumed at least one roll")
    }
}
