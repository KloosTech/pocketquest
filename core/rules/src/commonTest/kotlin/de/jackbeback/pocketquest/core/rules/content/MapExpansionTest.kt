package de.jackbeback.pocketquest.core.rules.content

import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.HatchLine
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.PropId
import de.jackbeback.pocketquest.core.model.PropLayer
import de.jackbeback.pocketquest.core.model.PropPlacement
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.TerrainRun
import de.jackbeback.pocketquest.core.model.TileType
import de.jackbeback.pocketquest.core.model.WallEdge
import de.jackbeback.pocketquest.core.model.WallStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapExpansionTest {

    @Test
    fun expandTerrainRunsExpandsAHorizontalRun() {
        val runs = listOf(TerrainRun(GridPos(1, 2), length = 3, horizontal = true, tile = TileType.Wall))
        val expanded = expandTerrainRuns(runs)
        assertEquals(
            mapOf(GridPos(1, 2) to TileType.Wall, GridPos(2, 2) to TileType.Wall, GridPos(3, 2) to TileType.Wall),
            expanded,
        )
    }

    @Test
    fun expandTerrainRunsExpandsAVerticalRun() {
        val runs = listOf(TerrainRun(GridPos(0, 0), length = 2, horizontal = false, tile = TileType.Hazard))
        val expanded = expandTerrainRuns(runs)
        assertEquals(mapOf(GridPos(0, 0) to TileType.Hazard, GridPos(0, 1) to TileType.Hazard), expanded)
    }

    @Test
    fun compressTerrainToRunsOmitsFloorEntirely() {
        val tiles = mapOf(GridPos(0, 0) to TileType.Floor, GridPos(1, 0) to TileType.Floor)
        assertEquals(emptyList(), compressTerrainToRuns(tiles, width = 5, height = 5))
    }

    @Test
    fun compressTerrainToRunsGroupsConsecutiveSameTileHorizontally() {
        val tiles = mapOf(GridPos(0, 0) to TileType.Wall, GridPos(1, 0) to TileType.Wall, GridPos(2, 0) to TileType.Wall)
        val runs = compressTerrainToRuns(tiles, width = 5, height = 1)
        assertEquals(listOf(TerrainRun(GridPos(0, 0), 3, horizontal = true, TileType.Wall)), runs)
    }

    @Test
    fun compressTerrainToRunsDoesNotMergeDifferentTileTypes() {
        val tiles = mapOf(GridPos(0, 0) to TileType.Wall, GridPos(1, 0) to TileType.Difficult)
        val runs = compressTerrainToRuns(tiles, width = 5, height = 1)
        assertEquals(
            listOf(TerrainRun(GridPos(0, 0), 1, true, TileType.Wall), TerrainRun(GridPos(1, 0), 1, true, TileType.Difficult)),
            runs,
        )
    }

    @Test
    fun compressThenExpandRoundTripsAnArbitraryTileMap() {
        val original = mapOf(
            GridPos(0, 0) to TileType.Wall,
            GridPos(1, 0) to TileType.Wall,
            GridPos(3, 0) to TileType.Hazard,
            GridPos(2, 3) to TileType.Difficult,
        )
        val runs = compressTerrainToRuns(original, width = 6, height = 6)
        assertEquals(original, expandTerrainRuns(runs))
    }

    @Test
    fun battleMapDefToBattleMapExpandsTerrainAndKeepsDimensions() {
        val def = BattleMapDef(
            id = MapId("room"), width = 4, height = 4,
            terrain = listOf(TerrainRun(GridPos(0, 0), 4, horizontal = true, TileType.Wall)),
        )
        val map = def.toBattleMap()
        assertEquals(4, map.width)
        assertEquals(4, map.height)
        assertEquals(TileType.Wall, map.tileAt(GridPos(0, 0)))
        assertEquals(TileType.Floor, map.tileAt(GridPos(0, 1)), "row 1 was never painted, stays implicit Floor")
    }

    @Test
    fun battleMapDefToBattleMapCarriesWallEdgesThrough() {
        val edge = WallEdge(GridPos(1, 1), Side.East)
        val def = BattleMapDef(id = MapId("room"), width = 4, height = 4, wallEdges = listOf(edge))
        val map = def.toBattleMap()
        assertEquals(setOf(edge), map.wallEdges)
        assertTrue(map.hasWallEdge(GridPos(2, 1), Side.West), "mirrored lookup still resolves after the authored->runtime round trip")
    }

    @Test
    fun battleMapDefToBattleMapCarriesDecorationFieldsThrough() {
        val placement = PropPlacement(PropId("chest1x1"), GridPos(1, 1), PropLayer.Object)
        val def = BattleMapDef(
            id = MapId("room"), width = 4, height = 4,
            props = listOf(placement), floorTexture = "stonytile5x5", wallStyle = WallStyle.Flat,
        )
        val map = def.toBattleMap()
        assertEquals(listOf(placement), map.props)
        assertEquals("stonytile5x5", map.floorTexture)
        assertEquals(WallStyle.Flat, map.wallStyle)
    }

    @Test
    fun battleMapDefToBattleMapCarriesBakedOsrHatchGeometryThrough() {
        // docs/33-wall-hatch-osr-packing.md: wallHatchOsr carries across unchanged (:ui only ever
        // renders it), but wallHatchOsrSeed does NOT — it's :designer-only bookkeeping, meaningless
        // once the bake itself has already been copied over.
        val line = HatchLine(1f, 1f, 2f, 1f, 0.03f)
        val def = BattleMapDef(id = MapId("room"), width = 4, height = 4, wallStyle = WallStyle.Osr, wallHatchOsr = listOf(line), wallHatchOsrSeed = 99L)
        assertEquals(listOf(line), def.toBattleMap().wallHatchOsr)
    }

    @Test
    fun battleMapDefToBattleMapDefaultsWallStyleToHatch() {
        // Every map saved before wallStyle existed (or before it was still the Boolean wallHatch)
        // decodes with this default — "on by default, overridable" only works if the fallback here
        // matches BattleMapDef's own.
        val def = BattleMapDef(id = MapId("room"), width = 4, height = 4)
        assertEquals(WallStyle.Hatch, def.toBattleMap().wallStyle)
    }

    @Test
    fun battleMapDefToBattleMapCarriesFogOfWarThroughAndDefaultsOn() {
        val default = BattleMapDef(id = MapId("room"), width = 4, height = 4)
        assertEquals(true, default.toBattleMap().fogOfWar, "every map saved before fogOfWar existed decodes with this default")

        val fogOff = BattleMapDef(id = MapId("room"), width = 4, height = 4, fogOfWar = false)
        assertEquals(false, fogOff.toBattleMap().fogOfWar)
    }
}
