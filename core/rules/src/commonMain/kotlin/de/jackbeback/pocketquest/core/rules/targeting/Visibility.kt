package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos

/**
 * Fog of war — unlimited range, LoS-blocked only: every tile [origin] has [hasLineOfSight] to,
 * across the whole map. A room lights up entirely the instant it's visible at all, no sight-radius
 * tuning constant. No-op (`emptySet()`) when [BattleMap.fogOfWar] is off, matching
 * [updateRevealedTiles]'s own early-out — a map with fog off never needs a visible-tiles computation
 * at all, callers don't need to check the flag themselves first.
 */
fun visibleTilesFrom(origin: GridPos, map: BattleMap): Set<GridPos> {
    if (!map.fogOfWar) return emptySet()
    val visible = mutableSetOf<GridPos>()
    for (col in 0 until map.width) {
        for (row in 0 until map.height) {
            val pos = GridPos(col, row)
            if (hasLineOfSight(origin, pos, map)) visible += pos
        }
    }
    return visible
}

/**
 * A wall tile touching (8-directionally, so room corners aren't left permanently dark — their only
 * floor neighbor is diagonal) an already-revealed open tile gets revealed too. Deliberately NOT a
 * LoS/raycast rule (an earlier "let sight penetrate N tiles of wall" approach produced a
 * checkerboard — different wall cells accumulate a different number of blocked units depending on
 * the exact angle, and coincident [de.jackbeback.pocketquest.core.model.WallEdge]s duplicated
 * around a solid wall cell could self-block even the cell's own tile) — a flat adjacency check is
 * immune to both. Only an OPEN revealed tile triggers this (never a wall revealed by this same
 * rule), so it can't chain deeper into solid rock one wall-cell at a time.
 */
private fun revealAdjacentWalls(map: BattleMap, revealed: Set<GridPos>): Set<GridPos> {
    val extra = mutableSetOf<GridPos>()
    for (pos in revealed) {
        if (map.blocksLoS(pos)) continue
        for (dc in -1..1) {
            for (dr in -1..1) {
                if (dc == 0 && dr == 0) continue
                val neighbor = GridPos(pos.col + dc, pos.row + dr)
                if (map.inBounds(neighbor) && map.blocksLoS(neighbor)) extra += neighbor
            }
        }
    }
    return extra
}

/**
 * Unions every living [Faction.Player] entity's current [visibleTilesFrom] into
 * [GameState.revealedTiles], then applies [revealAdjacentWalls] on top — monotonic, only ever
 * grows (docs: "once a tile is revealed, keep it revealed"), so this is safe to call after every
 * completed step regardless of whether anyone actually moved. A no-op (returns [state] unchanged)
 * when fog is off for this map, or when nothing new became visible, so callers can call this
 * unconditionally without needing to reason about whether the map even has fog of war.
 */
fun updateRevealedTiles(state: GameState): GameState {
    if (!state.map.fogOfWar) return state
    val newlyVisible = state.entities
        .asSequence()
        .filter { it.actor?.faction == Faction.Player }
        .filter { (it.health?.current ?: 1) > 0 }
        .mapNotNull { it.pos }
        .flatMap { visibleTilesFrom(it, state.map) }
        .toSet()
    val merged = state.revealedTiles + newlyVisible
    val withWalls = merged + revealAdjacentWalls(state.map, merged)
    return if (withWalls == state.revealedTiles) state else state.copy(revealedTiles = withWalls)
}

/**
 * One-way latch — [GameState.combatStarted] flips true the moment a living [Faction.Enemy] stands
 * on a tile in [GameState.revealedTiles], and never reverts even if that enemy later retreats into
 * unrevealed territory (once you've been spotted, you've been spotted). A map with
 * [BattleMap.fogOfWar] off has no exploration phase at all — this latches true for it immediately,
 * the first time this runs, rather than ever inspecting [GameState.revealedTiles] (which stays
 * permanently empty on such a map, since [updateRevealedTiles] never populates it there). A no-op
 * once already true, so callers (same as [updateRevealedTiles]) can call this unconditionally after
 * every step without checking first.
 */
fun checkCombatStart(state: GameState): GameState {
    if (state.combatStarted) return state
    if (!state.map.fogOfWar) return state.copy(combatStarted = true)
    val spotted = state.entities.any {
        it.actor?.faction == Faction.Enemy && (it.health?.current ?: 1) > 0 && it.pos != null && it.pos in state.revealedTiles
    }
    return if (spotted) state.copy(combatStarted = true) else state
}
