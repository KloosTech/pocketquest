package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.rules.fixture.walls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathfindingTest {

    @Test
    fun straightLineOnAnOpenMapCostsExactlyTheChebyshevDistance() {
        val map = BattleMap(10, 10)
        val path = findPath(GridPos(0, 0), GridPos(3, 0), map, emptyMap())!!
        assertEquals(listOf(GridPos(1, 0), GridPos(2, 0), GridPos(3, 0)), path)
    }

    @Test
    fun diagonalStepCostsTheSameAsCardinalMatchingChebyshevDistance() {
        val map = BattleMap(10, 10)
        // (0,0) -> (3,3) is a pure diagonal: 3 steps, not 6, since diagonal == cardinal cost here.
        val path = findPath(GridPos(0, 0), GridPos(3, 3), map, emptyMap())!!
        assertEquals(3, path.size)
        assertEquals(GridPos(3, 3), path.last())
    }

    @Test
    fun fromEqualsToReturnsAnEmptyPath() {
        val map = BattleMap(10, 10)
        assertEquals(emptyList(), findPath(GridPos(2, 2), GridPos(2, 2), map, emptyMap()))
    }

    @Test
    fun routesAroundAWallRatherThanFailing() {
        val map = BattleMap(10, 10, terrain = walls(GridPos(1, 0), GridPos(1, 1), GridPos(1, 2)))
        val path = findPath(GridPos(0, 1), GridPos(2, 1), map, emptyMap())
        requireNotNull(path)
        assertEquals(GridPos(2, 1), path.last())
        // must actually detour, not walk through the wall.
        assertEquals(emptyList<GridPos>(), path.filter { it.col == 1 && it.row in 0..2 })
    }

    @Test
    fun destinationBlockedByTerrainReturnsNull() {
        val map = BattleMap(10, 10, terrain = walls(GridPos(3, 0)))
        assertNull(findPath(GridPos(0, 0), GridPos(3, 0), map, emptyMap()))
    }

    @Test
    fun destinationOccupiedByAnotherEntityReturnsNull() {
        val map = BattleMap(10, 10)
        val occupancy = mapOf(GridPos(3, 0) to EntityId(1))
        assertNull(findPath(GridPos(0, 0), GridPos(3, 0), map, occupancy))
    }

    @Test
    fun fullyEnclosedDestinationIsUnreachable() {
        val map = BattleMap(10, 10, terrain = walls(GridPos(4, 3), GridPos(5, 3), GridPos(6, 3), GridPos(4, 4), GridPos(6, 4), GridPos(4, 5), GridPos(5, 5), GridPos(6, 5)))
        assertNull(findPath(GridPos(0, 0), GridPos(5, 4), map, emptyMap()))
    }

    @Test
    fun aCostBudgetPrunesPathsThatWouldExceedIt() {
        val map = BattleMap(10, 10)
        assertNull(findPath(GridPos(0, 0), GridPos(5, 0), map, emptyMap(), maxCost = 4))
        assertEquals(5, findPath(GridPos(0, 0), GridPos(5, 0), map, emptyMap(), maxCost = 5)!!.size)
    }

    @Test
    fun aCustomMoveCostIsHonoured() {
        val map = BattleMap(10, 10)
        // every tile costs 2 — a straight 3-tile path should total g=6, still returned correctly.
        val path = findPath(GridPos(0, 0), GridPos(3, 0), map, emptyMap(), moveCost = { 2 })!!
        assertEquals(3, path.size)
    }

    @Test
    fun anOccupiedTileMidRouteIsRoutedAroundNotThrough() {
        val map = BattleMap(10, 10)
        val occupancy = mapOf(GridPos(2, 0) to EntityId(1))
        val path = findPath(GridPos(0, 0), GridPos(4, 0), map, occupancy)
        requireNotNull(path)
        assertEquals(emptyList<GridPos>(), path.filter { it == GridPos(2, 0) })
        assertEquals(GridPos(4, 0), path.last())
    }
}
