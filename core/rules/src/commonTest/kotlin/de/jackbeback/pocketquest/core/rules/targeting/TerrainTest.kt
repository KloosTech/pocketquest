package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.TileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** docs/17-engine-gaps.md 1.4: terrain carries walkable/moveCost/blocksLoS/hazard as independent axes. */
class TerrainTest {

    @Test
    fun aTileAbsentFromTerrainIsPlainFloor() {
        val map = BattleMap(10, 10)
        assertEquals(TileType.Floor, map.tileAt(GridPos(3, 3)))
        assertTrue(map.isWalkable(GridPos(3, 3)))
        assertEquals(1, map.moveCost(GridPos(3, 3)))
        assertFalse(map.blocksLoS(GridPos(3, 3)))
    }

    @Test
    fun wallsComputedPropertyReturnsExactlyTheUnwalkableTiles() {
        val map = BattleMap(
            10, 10,
            terrain = mapOf(
                GridPos(1, 0) to TileType.Wall,
                GridPos(2, 0) to TileType.Difficult, // walkable, must NOT count as a wall
            ),
        )
        assertEquals(setOf(GridPos(1, 0)), map.walls)
    }

    @Test
    fun difficultTerrainCostsMoreButRemainsWalkableAndSeeThrough() {
        val map = BattleMap(10, 10, terrain = mapOf(GridPos(2, 0) to TileType.Difficult))
        assertTrue(map.isWalkable(GridPos(2, 0)))
        assertEquals(2, map.moveCost(GridPos(2, 0)))
        assertFalse(map.blocksLoS(GridPos(2, 0)))
    }

    // --- The actual point of 1.4: walkable and blocksLoS are independent axes now ---

    @Test
    fun aTileCanBlockSightWithoutBlockingMovement() {
        // e.g. tall grass: you can walk through it, but not see (or be targeted) through it.
        val map = BattleMap(10, 10, terrain = mapOf(GridPos(2, 0) to TileType(walkable = true, blocksLoS = true)))
        assertTrue(map.isWalkable(GridPos(2, 0)))
        assertFalse(hasLineOfSight(GridPos(0, 0), GridPos(4, 0), map))
        // and a real path can still cross straight through it, unlike a wall.
        val path = findPath(GridPos(0, 0), GridPos(4, 0), map, emptyMap())
        assertEquals(listOf(GridPos(1, 0), GridPos(2, 0), GridPos(3, 0), GridPos(4, 0)), path)
    }

    @Test
    fun aTileCanBlockMovementWithoutBlockingSight() {
        // e.g. a low fence: you can see and target over it, but not walk through it.
        val map = BattleMap(10, 10, terrain = mapOf(GridPos(2, 0) to TileType(walkable = false, blocksLoS = false)))
        assertFalse(map.isWalkable(GridPos(2, 0)))
        assertTrue(hasLineOfSight(GridPos(0, 0), GridPos(4, 0), map))
        // no straight path through the fence, but a detour around it must still exist and avoid it.
        val path = findPath(GridPos(0, 0), GridPos(4, 0), map, emptyMap())
        requireNotNull(path)
        assertTrue(GridPos(2, 0) !in path)
    }

    // --- Cost-aware pathfinding / reachability, now that terrain cost is real ---

    @Test
    fun findPathTotalsRealTerrainCostNotJustStepCount() {
        // Walls above/below the difficult tile close off any cheaper diagonal detour, so the
        // route must actually cross it — proving pathCost sums real per-tile cost, not tile count.
        val map = BattleMap(
            10, 10,
            terrain = mapOf(GridPos(1, 4) to TileType.Wall, GridPos(1, 5) to TileType.Difficult, GridPos(1, 6) to TileType.Wall),
        )
        val path = findPath(GridPos(0, 5), GridPos(2, 5), map, emptyMap())!!
        assertEquals(2, path.size, "still 2 steps")
        assertEquals(3, path.pathCost(map), "but 1(floor) + 2(difficult) = 3, not 2")
    }

    @Test
    fun reachableTilesExcludesATileThatFitsByStepCountButNotByCost() {
        // A full column of Difficult terrain at col=1 forces every route from col=0 to col=2 to
        // spend 2 just entering col=1, leaving nothing for the last step into col=2 within budget 2
        // — the old step-count BFS (2 steps away) would have wrongly allowed this.
        val map = BattleMap(
            10, 10,
            terrain = mapOf(GridPos(1, 0) to TileType.Difficult, GridPos(1, 1) to TileType.Difficult, GridPos(1, 2) to TileType.Difficult),
        )
        val reachable = reachableTiles(GridPos(0, 1), maxCost = 2, map = map, occupancy = emptyMap())
        assertTrue(GridPos(1, 1) in reachable, "entering the difficult tile itself costs exactly the budget")
        assertFalse(GridPos(2, 1) in reachable, "reaching past it costs 2(difficult) + 1(floor) = 3, over budget")
    }

    @Test
    fun reachableTilesStillFindsACheaperDetourAroundDifficultTerrain() {
        // Difficult only at (1,1); the straight line (0,1)->(1,1)->(2,1) costs 2+1=3, over budget 2,
        // but detouring through the plain-floor row above/below costs only 1+1=2.
        val map = BattleMap(10, 10, terrain = mapOf(GridPos(1, 1) to TileType.Difficult))
        val reachable = reachableTiles(GridPos(0, 1), maxCost = 2, map = map, occupancy = emptyMap())
        assertTrue(GridPos(2, 1) in reachable, "reachable via a cheaper detour, not the expensive straight line")
    }
}
