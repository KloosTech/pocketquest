package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Shape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {

    private val openMap = BattleMap(20, 20)

    // --- Line of sight ---

    @Test
    fun sameTileAlwaysHasLineOfSight() {
        assertTrue(hasLineOfSight(GridPos(3, 3), GridPos(3, 3), openMap))
    }

    @Test
    fun clearStraightLineHasLineOfSight() {
        assertTrue(hasLineOfSight(GridPos(0, 0), GridPos(5, 0), openMap))
        assertTrue(hasLineOfSight(GridPos(0, 0), GridPos(5, 5), openMap))
    }

    @Test
    fun wallBetweenTwoPointsBlocksLineOfSight() {
        val map = BattleMap(10, 10, blockedTiles = setOf(GridPos(2, 0)))
        assertFalse(hasLineOfSight(GridPos(0, 0), GridPos(4, 0), map))
    }

    @Test
    fun diagonalStepBlockedByEitherSharedCardinalNeighbourWall() {
        // Moving from (0,0) to (1,1) diagonally: Bresenham visits no intermediate cell,
        // so the corner-cut check must catch a wall at (1,0) or (0,1) explicitly.
        val mapBlockedRight = BattleMap(10, 10, blockedTiles = setOf(GridPos(1, 0)))
        assertFalse(hasLineOfSight(GridPos(0, 0), GridPos(1, 1), mapBlockedRight))

        val mapBlockedBelow = BattleMap(10, 10, blockedTiles = setOf(GridPos(0, 1)))
        assertFalse(hasLineOfSight(GridPos(0, 0), GridPos(1, 1), mapBlockedBelow))

        assertTrue(hasLineOfSight(GridPos(0, 0), GridPos(1, 1), openMap))
    }

    @Test
    fun targetTileItselfIsNeverCheckedForBlocking() {
        // A blocked target tile is still "visible" up to it — legalTargets/canPerform decide
        // whether standing on a wall is a valid target, not hasLineOfSight.
        val map = BattleMap(10, 10, blockedTiles = setOf(GridPos(4, 0)))
        assertTrue(hasLineOfSight(GridPos(0, 0), GridPos(4, 0), map))
    }

    // --- Shapes ---

    @Test
    fun singleShapeIsExactlyTheAimedTile() {
        assertEquals(setOf(GridPos(5, 5)), tilesInShape(GridPos(0, 0), GridPos(5, 5), Shape.Single, openMap))
    }

    @Test
    fun sphereRadiusOneIsAPlusShapeNotASquare() {
        // Euclidean distance <= 1: center + 4 orthogonal neighbours; diagonals are sqrt(2) > 1.
        val tiles = tilesInShape(GridPos(5, 5), GridPos(5, 5), Shape.Sphere(1), openMap)
        val expected = setOf(GridPos(5, 5), GridPos(4, 5), GridPos(6, 5), GridPos(5, 4), GridPos(5, 6))
        assertEquals(expected, tiles)
    }

    @Test
    fun coneFacingEastIncludesForwardTilesExcludesBehind() {
        // Origin kept away from the map edge (0,0 would clip negative-row tiles via
        // bounds-checking before the angle math even runs, breaking symmetry).
        // degrees=100 (half-spread 50) keeps every check comfortably inside/outside its
        // boundary rather than exactly on it, avoiding floating-point edge flakiness.
        val origin = GridPos(5, 5)
        val tiles = tilesInShape(origin, GridPos(6, 5), Shape.Cone(length = 2, degrees = 100), openMap)
        assertTrue(GridPos(6, 5) in tiles, "directly ahead must be included")
        assertTrue(GridPos(7, 5) in tiles, "ahead at max length must be included")
        assertTrue(GridPos(6, 6) in tiles, "45 degrees off-axis, within a 50-degree half-spread, must be included")
        assertTrue(GridPos(6, 4) in tiles, "symmetric side must be included")
        assertFalse(GridPos(5, 6) in tiles, "directly to the side (90 degrees off) must be excluded")
        assertFalse(GridPos(4, 5) in tiles, "directly behind must be excluded")
        assertFalse(origin in tiles, "the caster's own tile is never included")
    }

    @Test
    fun lineSnapsToCompassDirectionAndStopsAtLength() {
        val tiles = tilesInShape(GridPos(0, 0), GridPos(5, 0), Shape.Line(length = 3), openMap)
        assertEquals(setOf(GridPos(1, 0), GridPos(2, 0), GridPos(3, 0)), tiles)
    }

    @Test
    fun rectIsAxisAlignedCenteredOnAimPoint() {
        val tiles = tilesInShape(GridPos(0, 0), GridPos(5, 5), Shape.Rect(width = 3, height = 1), openMap)
        assertEquals(setOf(GridPos(4, 5), GridPos(5, 5), GridPos(6, 5)), tiles)
    }

    @Test
    fun shapesNeverIncludeOutOfBoundsTiles() {
        val small = BattleMap(3, 3)
        val tiles = tilesInShape(GridPos(0, 0), GridPos(0, 0), Shape.Sphere(5), small)
        assertTrue(tiles.all { small.inBounds(it) })
    }
}
