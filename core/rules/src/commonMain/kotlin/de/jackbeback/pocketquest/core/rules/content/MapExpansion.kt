package de.jackbeback.pocketquest.core.rules.content

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.TerrainRun
import de.jackbeback.pocketquest.core.model.TileType

/**
 * Bridges [BattleMapDef] (authored content — what the Map editor paints) and both the flat
 * per-tile representation painting needs and [BattleMap] (the runtime combat grid `TileType`
 * actually attaches to). No `:core:run` exists yet to call [BattleMapDef.toBattleMap] from a real
 * `startEncounter`, same "primitive without the layer that drives it" shape as `RefillMana`
 * (doc17-engine-gaps.md 1.1) before it — the :designer module's Map editor is what exercises the
 * expand/compress pair for now.
 */
fun expandTerrainRuns(runs: List<TerrainRun>): Map<GridPos, TileType> {
    val result = mutableMapOf<GridPos, TileType>()
    for (run in runs) {
        for (i in 0 until run.length) {
            val pos = if (run.horizontal) GridPos(run.start.col + i, run.start.row) else GridPos(run.start.col, run.start.row + i)
            result[pos] = run.tile
        }
    }
    return result
}

/**
 * Row-by-row run-length encoding: Floor is the implicit default (never needs a run, matching
 * [BattleMap]'s own "absent tile is Floor" convention), everything else groups into consecutive
 * same-[TileType] horizontal runs. Not byte-optimal (a large vertical wall becomes one run per
 * row, not one tall run) — correct and simple beats clever for a first Map editor pass; nothing
 * about [TerrainRun]'s shape prevents a smarter compressor replacing this later.
 */
fun compressTerrainToRuns(tiles: Map<GridPos, TileType>, width: Int, height: Int): List<TerrainRun> {
    val runs = mutableListOf<TerrainRun>()
    for (row in 0 until height) {
        var col = 0
        while (col < width) {
            val tile = tiles[GridPos(col, row)] ?: TileType.Floor
            if (tile == TileType.Floor) {
                col++
                continue
            }
            var length = 1
            while (col + length < width && (tiles[GridPos(col + length, row)] ?: TileType.Floor) == tile) length++
            runs += TerrainRun(GridPos(col, row), length, horizontal = true, tile = tile)
            col += length
        }
    }
    return runs
}

/**
 * Expands [BattleMapDef.terrain] into the runtime [BattleMap] the resolver's targeting/pathfinding
 * actually reads. `props`/`floorTexture`/`wallStyle`/`wallHatchOsr`/`backgroundMarginTiles` carry
 * straight across unchanged — pure rendering data `:ui`'s Board reads off `state.map`, never
 * touched by the resolver. `wallHatchOsrSeed` does NOT carry across — it's `:designer`-only
 * bookkeeping for regenerating the bake later, meaningless once the bake result itself has already
 * been copied over. `fogOfWar`/`triggers` also carry straight across, but unlike the rendering-only
 * fields ARE read by the resolver/AI layer (visibility, hidden-enemy skip-turn, docs/36 trigger firing).
 */
fun BattleMapDef.toBattleMap(): BattleMap =
    BattleMap(width, height, expandTerrainRuns(terrain), wallEdges.toSet(), props, floorTexture, wallStyle, fogOfWar, wallHatchOsr, backgroundMarginTiles, triggers)
