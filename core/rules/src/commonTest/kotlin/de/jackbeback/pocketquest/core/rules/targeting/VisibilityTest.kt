package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.TileType
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.model.WallEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisibilityTest {

    @Test
    fun visibleTilesFromAnOpenRoomIsTheWholeMap() {
        val map = BattleMap(5, 5, fogOfWar = true)
        val visible = visibleTilesFrom(GridPos(2, 2), map)
        assertEquals(25, visible.size)
    }

    @Test
    fun visibleTilesFromStopsAtAWall() {
        // Strict LoS — no penetration. The wall's own tile is visible (target-endpoint exemption:
        // hasLineOfSight never checks the target's own blocksLoS, only intermediate cells), but
        // nothing beyond it is; that's handled separately by updateRevealedTiles' adjacency rule.
        val map = BattleMap(5, 1, terrain = mapOf(GridPos(2, 0) to TileType.Wall), fogOfWar = true)
        val visible = visibleTilesFrom(GridPos(0, 0), map)
        assertTrue(GridPos(1, 0) in visible)
        assertTrue(GridPos(2, 0) in visible, "the wall's own tile is visible once you're facing it")
        assertFalse(GridPos(3, 0) in visible, "the wall at col 2 should block sight past it")
        assertFalse(GridPos(4, 0) in visible)
    }

    @Test
    fun visibleTilesFromIsEmptyWhenFogOfWarIsOff() {
        // BattleMap's own bare-constructor default (unlike BattleMapDef's) — this is redundant with
        // it, kept explicit so the test still documents the behavior even if that default ever changes.
        val map = BattleMap(5, 5, fogOfWar = false)
        assertEquals(emptySet(), visibleTilesFrom(GridPos(2, 2), map))
    }

    private fun player(id: Long, pos: GridPos, hp: Int = 10) = Entity(
        id = EntityId(id), archetype = ArchetypeId("hero"), pos = pos,
        health = Health(hp), resources = null, actor = Actor(Faction.Player, Controller.Human),
    )

    private fun enemy(id: Long, pos: GridPos, hp: Int = 10) = Entity(
        id = EntityId(id), archetype = ArchetypeId("goblin"), pos = pos,
        health = Health(hp), resources = null, actor = Actor(Faction.Enemy, Controller.Ai(de.jackbeback.pocketquest.core.model.AiProfileId("standard"))),
    )

    private fun state(map: BattleMap, vararg entities: Entity) = GameState(
        entities = entities.toList(), map = map,
        turn = TurnState(round = 1, order = entities.map { it.id }, activeIndex = 0, phase = TurnPhase.Main),
        rng = RngState(seed = 1L),
    )

    @Test
    fun updateRevealedTilesUnionsEveryLivingPlayersVisibility() {
        val map = BattleMap(5, 1, terrain = mapOf(GridPos(2, 0) to TileType.Wall), fogOfWar = true)
        val before = state(map, player(1, GridPos(0, 0)))
        val after = updateRevealedTiles(before)
        assertTrue(GridPos(1, 0) in after.revealedTiles)
        assertFalse(GridPos(3, 0) in after.revealedTiles, "still blocked past the wall — no LoS penetration")
    }

    @Test
    fun updateRevealedTilesRevealsAWallTileEvenWhenACoincidentWallEdgeBlocksDirectLineOfSightToIt() {
        // Mirrors a real map where a solid Wall cell also carries a WallEdge on its near face —
        // the WallEdge sits exactly on the sightline to the wall's own tile, so strict LoS to it
        // fails outright (a real bug found live). The adjacency rule sidesteps this entirely: it
        // never raycasts into the wall at all, so a self-blocking edge on the wall can't matter.
        val map = BattleMap(2, 1, terrain = mapOf(GridPos(1, 0) to TileType.Wall), wallEdges = setOf(WallEdge(GridPos(1, 0), Side.West)), fogOfWar = true)
        val before = state(map, player(1, GridPos(0, 0)))
        val after = updateRevealedTiles(before)
        assertTrue(GridPos(1, 0) in after.revealedTiles)
    }

    @Test
    fun updateRevealedTilesRevealsACornerWallThatOnlyTouchesRevealedFloorDiagonally() {
        // (1,1) is a wall whose only floor neighbor, (0,0), touches it purely diagonally — the
        // usual shape of a room's four corner wall cells. 8-directional adjacency catches this;
        // 4-directional would leave every room corner permanently dark.
        val map = BattleMap(
            2, 2,
            terrain = mapOf(GridPos(1, 0) to TileType.Wall, GridPos(0, 1) to TileType.Wall, GridPos(1, 1) to TileType.Wall),
            fogOfWar = true,
        )
        val before = state(map, player(1, GridPos(0, 0)))
        val after = updateRevealedTiles(before)
        assertTrue(GridPos(1, 1) in after.revealedTiles)
    }

    @Test
    fun updateRevealedTilesDoesNotChainThroughMultipleWallsDeep() {
        // Only an open, revealed tile triggers the adjacency reveal — a wall revealed BY that rule
        // must not itself trigger revealing the next wall behind it, or fog would leak arbitrarily
        // deep into solid rock one cell at a time.
        val map = BattleMap(
            4, 1,
            terrain = mapOf(GridPos(1, 0) to TileType.Wall, GridPos(2, 0) to TileType.Wall),
            wallEdges = setOf(WallEdge(GridPos(1, 0), Side.West)),
            fogOfWar = true,
        )
        val before = state(map, player(1, GridPos(0, 0)))
        val after = updateRevealedTiles(before)
        assertTrue(GridPos(1, 0) in after.revealedTiles, "directly adjacent to the revealed origin tile")
        assertFalse(GridPos(2, 0) in after.revealedTiles, "only adjacent to another wall, not to any open revealed tile")
    }

    @Test
    fun inCombatIsFalseWhileNoEnemyIsOnARevealedTile() {
        // A 3-thick wall (col 2..4) genuinely blocks strict LoS past it (no penetration, no
        // adjacency reveal reaching that far) — an open room would reveal the whole map trivially
        // and defeat the point of this test.
        val map = BattleMap(6, 1, terrain = (2..4).associate { GridPos(it, 0) to TileType.Wall }, fogOfWar = true)
        val before = updateRevealedTiles(state(map, player(1, GridPos(0, 0)), enemy(2, GridPos(5, 0))))
        assertFalse(updateEngagedEnemies(before).inCombat, "the enemy is behind a wall the party hasn't seen past")
    }

    @Test
    fun inCombatFlipsTrueTheMomentAnEnemyIsOnARevealedTile() {
        val map = BattleMap(3, 1, fogOfWar = true)
        val before = updateRevealedTiles(state(map, player(1, GridPos(0, 0)), enemy(2, GridPos(1, 0))))
        assertTrue(updateEngagedEnemies(before).inCombat)
    }

    @Test
    fun inCombatSurvivesAnEngagedEnemyRetreatingIntoShadow() {
        val map = BattleMap(3, 1, fogOfWar = true)
        val spotted = updateEngagedEnemies(updateRevealedTiles(state(map, player(1, GridPos(0, 0)), enemy(2, GridPos(1, 0)))))
        assertTrue(spotted.inCombat)
        // The enemy id is now permanently in engagedEnemies (revealedTiles/engagedEnemies are both
        // monotonic) — even if it later stood somewhere never revealed, it's still ALIVE, so combat
        // must not drop back to exploration just because it ducked out of sight mid-fight.
        val retreated = spotted.copy(entities = spotted.entities.map { if (it.id == EntityId(2)) it.copy(pos = GridPos(2, 0)) else it })
        assertTrue(updateEngagedEnemies(retreated).inCombat, "a live engaged enemy hiding again must not end combat")
    }

    @Test
    fun inCombatDropsBackToFalseOnceEveryEngagedEnemyIsDeadAndNothingNewIsSpotted() {
        // The actual bug report: explore -> spot and kill the only enemy -> should return to
        // exploration mode, not stay stuck in normal turn-based combat forever.
        val map = BattleMap(3, 1, fogOfWar = true)
        val spotted = updateEngagedEnemies(updateRevealedTiles(state(map, player(1, GridPos(0, 0)), enemy(2, GridPos(1, 0)))))
        assertTrue(spotted.inCombat)
        val defeated = spotted.copy(entities = spotted.entities.map { if (it.id == EntityId(2)) it.copy(health = it.health?.copy(current = 0)) else it })
        assertFalse(updateEngagedEnemies(defeated).inCombat, "the only engaged enemy is dead and nothing new has been spotted")
    }

    @Test
    fun inCombatStaysTrueIfOneEngagedEnemyDiesButAnotherIsStillAlive() {
        val map = BattleMap(3, 1, fogOfWar = true)
        val spotted = updateEngagedEnemies(updateRevealedTiles(state(map, player(1, GridPos(0, 0)), enemy(2, GridPos(1, 0)), enemy(3, GridPos(2, 0)))))
        assertTrue(spotted.inCombat)
        val oneDead = spotted.copy(entities = spotted.entities.map { if (it.id == EntityId(2)) it.copy(health = it.health?.copy(current = 0)) else it })
        assertTrue(updateEngagedEnemies(oneDead).inCombat, "enemy 3 is still alive and engaged")
    }

    @Test
    fun inCombatIsImmediatelyTrueWhenTheMapHasNoFogOfWar() {
        val map = BattleMap(3, 1, fogOfWar = false)
        val before = state(map, player(1, GridPos(0, 0)))
        assertTrue(updateEngagedEnemies(before).inCombat, "no fog means no exploration phase at all")
    }

    @Test
    fun updateRevealedTilesIsMonotonicAcrossCalls() {
        val map = BattleMap(5, 1, terrain = mapOf(GridPos(2, 0) to TileType.Wall), fogOfWar = true)
        val afterFirst = updateRevealedTiles(state(map, player(1, GridPos(0, 0))))
        // Party moves past the wall — previously-revealed tiles must still be revealed even though
        // the party is no longer there to currently see them.
        val movedState = afterFirst.copy(entities = listOf(player(1, GridPos(4, 0))))
        val afterSecond = updateRevealedTiles(movedState)
        assertTrue(GridPos(1, 0) in afterSecond.revealedTiles, "earlier reveal must persist")
        assertTrue(GridPos(3, 0) in afterSecond.revealedTiles, "now visible from the new position")
    }

    @Test
    fun updateRevealedTilesIgnoresADownedPlayer() {
        val map = BattleMap(3, 1, fogOfWar = true)
        val before = state(map, player(1, GridPos(0, 0), hp = 0))
        val after = updateRevealedTiles(before)
        assertEquals(emptySet(), after.revealedTiles)
    }

    @Test
    fun updateRevealedTilesIgnoresEnemies() {
        val map = BattleMap(3, 1, fogOfWar = true)
        val before = state(map, enemy(1, GridPos(0, 0)))
        val after = updateRevealedTiles(before)
        assertEquals(emptySet(), after.revealedTiles)
    }

    @Test
    fun updateRevealedTilesIsANoOpWhenFogOfWarIsOff() {
        val map = BattleMap(3, 1, fogOfWar = false)
        val before = state(map, player(1, GridPos(0, 0)))
        val after = updateRevealedTiles(before)
        assertEquals(before, after)
    }

    @Test
    fun updateRevealedTilesReturnsTheSameInstanceWhenNothingNewIsVisible() {
        val map = BattleMap(3, 1, fogOfWar = true)
        val first = updateRevealedTiles(state(map, player(1, GridPos(0, 0))))
        val second = updateRevealedTiles(first)
        assertTrue(first === second, "no newly-visible tiles should skip the copy() entirely")
    }
}
