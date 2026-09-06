package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GateId
import de.jackbeback.pocketquest.core.model.GatePlacement
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.WallEdge
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/48-gates-and-wander-ai.md: a [GatePlacement]'s edges block [BattleMap.canCross] while its id
 * is absent from the caller's `openGates` set, but are never checked by [hasLineOfSight] at all —
 * the whole reason a gate is a separate list from [WallEdge] rather than a flag on it.
 */
class GateTest {

    @Test
    fun aClosedGateBlocksCrossingJustLikeAWallEdge() {
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(2, 2), Side.East)))
        val map = BattleMap(10, 10, gates = listOf(gate))
        assertFalse(map.canCross(GridPos(2, 2), GridPos(3, 2), openGates = emptySet()))
    }

    @Test
    fun anOpenGateAllowsCrossing() {
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(2, 2), Side.East)))
        val map = BattleMap(10, 10, gates = listOf(gate))
        assertTrue(map.canCross(GridPos(2, 2), GridPos(3, 2), openGates = setOf(GateId("g1"))))
    }

    @Test
    fun aGateDefaultsClosedWhenNoOpenGatesSetIsPassedAtAll() {
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(2, 2), Side.East)))
        val map = BattleMap(10, 10, gates = listOf(gate))
        assertFalse(map.canCross(GridPos(2, 2), GridPos(3, 2)), "default parameter treats every gate as closed")
    }

    @Test
    fun aGateEdgeNeverBlocksLineOfSightEvenWhenClosed() {
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(2, 0), Side.East)))
        val map = BattleMap(10, 10, gates = listOf(gate))
        assertTrue(hasLineOfSight(GridPos(0, 0), GridPos(5, 0), map), "bars, not a solid door — you can see through a closed gate")
    }

    @Test
    fun aMapWithNoGatesAtAllIsUnaffectedByTheOpenGatesParameter() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 2), Side.East)))
        // Passing a non-empty openGates set that names no real gate must not accidentally open
        // an ordinary WallEdge — the two lists are disjoint by construction.
        assertFalse(map.canCross(GridPos(2, 2), GridPos(3, 2), openGates = setOf(GateId("nonexistent"))))
    }

    @Test
    fun findPathRoutesAroundAClosedGateButStraightThroughOnceItsOpen() {
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(2, 2), Side.East)))
        val map = BattleMap(10, 10, gates = listOf(gate))
        val closedPath = findPath(GridPos(2, 2), GridPos(3, 2), map, emptyMap(), openGates = emptySet())
        requireNotNull(closedPath)
        assertTrue(closedPath.size > 1, "closed gate forces a detour")

        val openPath = findPath(GridPos(2, 2), GridPos(3, 2), map, emptyMap(), openGates = setOf(GateId("g1")))
        requireNotNull(openPath)
        assertTrue(openPath.size == 1, "open gate allows the direct 1-step crossing")
    }

    @Test
    fun aTwoEdgeGateOpensBothEdgesTogetherFromOneId() {
        val gate = GatePlacement(
            GateId("wide"),
            edges = listOf(WallEdge(GridPos(2, 2), Side.East), WallEdge(GridPos(2, 3), Side.East)),
        )
        val map = BattleMap(10, 10, gates = listOf(gate))
        val open = setOf(GateId("wide"))
        assertTrue(map.canCross(GridPos(2, 2), GridPos(3, 2), open))
        assertTrue(map.canCross(GridPos(2, 3), GridPos(3, 3), open))
    }
}
