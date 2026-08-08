package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.WallEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * doc16's reference battlemat draws room dividers as thin lines on a tile edge, not a whole
 * consumed cell — [WallEdge] layers that on top of [de.jackbeback.pocketquest.core.model.TileType]
 * ([TerrainTest] covers the pre-existing whole-cell wall), so both styles stay usable.
 */
class WallEdgeTest {

    @Test
    fun aWallEdgeIsVisibleFromEitherSideWithOnlyOneCanonicalEntry() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 2), Side.East)))
        assertTrue(map.hasWallEdge(GridPos(2, 2), Side.East), "queried from the authored side")
        assertTrue(map.hasWallEdge(GridPos(3, 2), Side.West), "queried from the mirrored neighbour")
        assertFalse(map.hasWallEdge(GridPos(2, 2), Side.North), "a different side of the same cell is unaffected")
    }

    @Test
    fun bothCellsOnAWallEdgeKeepTheirFullFloorUnlikeAWholeCellWall() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 2), Side.East)))
        assertTrue(map.isWalkable(GridPos(2, 2)))
        assertTrue(map.isWalkable(GridPos(3, 2)))
    }

    @Test
    fun canCrossIsFalseOnlyForTheOrthogonalStepThatCrossesTheWall() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 2), Side.East)))
        assertFalse(map.canCross(GridPos(2, 2), GridPos(3, 2)))
        assertFalse(map.canCross(GridPos(3, 2), GridPos(2, 2)))
        assertTrue(map.canCross(GridPos(2, 2), GridPos(2, 1)), "a different direction from the same cell is unaffected")
    }

    @Test
    fun diagonalStepIsBlockedIfEitherFlankingEdgeHasAWall() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 2), Side.East)))
        // (2,2) -> (3,3) is a diagonal step; its flanking edges are East-of-(2,2) and South-of-(2,2).
        assertFalse(map.canCross(GridPos(2, 2), GridPos(3, 3)), "East flank has a wall, so the corner can't be cut")
    }

    @Test
    fun diagonalStepIsOpenWhenNeitherFlankingEdgeHasAWall() {
        val map = BattleMap(10, 10)
        assertTrue(map.canCross(GridPos(2, 2), GridPos(3, 3)))
    }

    @Test
    fun findPathRoutesAroundAWallEdgeRatherThanCrossingIt() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 2), Side.East)))
        val path = findPath(GridPos(2, 2), GridPos(3, 2), map, emptyMap())
        requireNotNull(path)
        // must detour, not step straight across the one-tile gap.
        assertTrue(path.size > 1, "a direct 1-step crossing would be illegal")
    }

    @Test
    fun findPathReturnsNullWhenAWallEdgeFullyEnclosesTheDestination() {
        val map = BattleMap(
            10, 10,
            wallEdges = setOf(
                WallEdge(GridPos(5, 5), Side.North),
                WallEdge(GridPos(5, 5), Side.South),
                WallEdge(GridPos(5, 5), Side.East),
                WallEdge(GridPos(5, 5), Side.West),
            ),
        )
        assertNull(findPath(GridPos(0, 0), GridPos(5, 5), map, emptyMap()))
    }

    @Test
    fun reachableTilesExcludesATileOnlyReachableByCrossingAWallEdge() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(0, 0), Side.East)))
        val reachable = reachableTiles(GridPos(0, 0), maxCost = 1, map = map, occupancy = emptyMap())
        assertFalse(GridPos(1, 0) in reachable, "directly across the wall")
        assertTrue(GridPos(0, 1) in reachable, "a different direction is unaffected")
    }

    @Test
    fun lineOfSightIsBlockedWhenTheSightlineCrossesAWallEdge() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 0), Side.East)))
        assertFalse(hasLineOfSight(GridPos(0, 0), GridPos(5, 0), map))
    }

    @Test
    fun lineOfSightIsUnaffectedByAWallEdgeNotOnTheSightline() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(2, 5), Side.East)))
        assertTrue(hasLineOfSight(GridPos(0, 0), GridPos(5, 0), map))
    }

    @Test
    fun lineOfSightAlongADiagonalIsBlockedByAWallEdgeItCrosses() {
        val map = BattleMap(10, 10, wallEdges = setOf(WallEdge(GridPos(1, 1), Side.East)))
        assertFalse(hasLineOfSight(GridPos(0, 0), GridPos(4, 4), map))
    }
}
