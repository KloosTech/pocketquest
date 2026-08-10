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
 * Adds every currently-alive, currently-revealed [Faction.Enemy] to [GameState.engagedEnemies] —
 * monotonic (ids are never removed, even once that enemy dies or retreats out of sight), same
 * "safe to call after every step" contract as [updateRevealedTiles]. A no-op on a map with
 * [BattleMap.fogOfWar] off: [inCombat] is unconditionally true there regardless of this set, so
 * there's nothing for it to track.
 */
fun updateEngagedEnemies(state: GameState): GameState {
    if (!state.map.fogOfWar) return state
    val newlyEngaged = state.entities
        .asSequence()
        .filter { it.actor?.faction == Faction.Enemy }
        .filter { (it.health?.current ?: 1) > 0 }
        .filter { it.pos != null && it.pos in state.revealedTiles }
        .map { it.id }
        .toSet()
    val merged = state.engagedEnemies + newlyEngaged
    return if (merged == state.engagedEnemies) state else state.copy(engagedEnemies = merged)
}

/**
 * Whether combat is currently active, derived fresh every time rather than a one-way latch: true
 * while any [GameState.engagedEnemies] entry is still alive (so one engaged enemy retreating into
 * shadow doesn't end the fight while another is still a live threat elsewhere), or unconditionally
 * true when the map has [BattleMap.fogOfWar] off (no exploration phase at all). Flips back to false
 * — and `:ui` drops back into free-roam exploration — the moment every engaged enemy is dead and
 * nothing new has been [updateEngagedEnemies]'d in, until the next fresh sighting.
 */
val GameState.inCombat: Boolean
    get() = !map.fogOfWar || engagedEnemies.any { id -> byId[id]?.let { (it.health?.current ?: 1) > 0 } == true }
