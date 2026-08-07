package de.jackbeback.pocketquest.ui

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
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val TILE_PX = 48f
private val deadId = EntityId(0)
private val aliveId = EntityId(1)
private val reserveId = EntityId(2)

private fun entity(id: EntityId, pos: GridPos?, hp: Int) = Entity(
    id = id, archetype = ArchetypeId("dummy"), pos = pos,
    health = Health(hp), resources = null, actor = Actor(Faction.Player, Controller.Human),
)

private fun state(vararg entities: Entity) = GameState(
    entities = entities.toList(),
    map = BattleMap(10, 10),
    turn = TurnState(round = 1, order = entities.map { it.id }, activeIndex = 0, phase = TurnPhase.Main),
    rng = RngState(seed = 1, calls = 0),
)

class ReconciliationTest {

    /**
     * KNOWN_ISSUES.md #2a — settle() used to snapTo(1f) unconditionally, resurrecting a dead
     * entity's alpha every single drain regardless of whether its Died beat ever ran. Pinned at
     * [DOWNED_ALPHA], not 0 — doc15/doc10's "Downed, not dead": still on the board, revivable, not
     * fully invisible (pass 34, once Downed got its own visual).
     */
    @Test
    fun settleFadesADeadEntityEvenIfNoDeathBeatEverRan() = runTest {
        val s = state(entity(deadId, GridPos(0, 0), hp = 0))
        val world = VisualWorld(s, TILE_PX)
        world.settle(s)
        assertEquals(DOWNED_ALPHA, world.entities.getValue(deadId).alpha.value)
    }

    @Test
    fun settleRestoresAliveAlphaEvenIfLeftMidFade() = runTest {
        val s = state(entity(aliveId, GridPos(0, 0), hp = 10))
        val world = VisualWorld(s, TILE_PX)
        world.entities.getValue(aliveId).alpha.snapTo(0.4f) // pretend a beat left it mid-fade
        world.settle(s)
        assertEquals(1f, world.entities.getValue(aliveId).alpha.value)
    }

    /** KNOWN_ISSUES.md #2b — doc02: pos == null is reserve/dead, not "render at Offset.Zero". */
    @Test
    fun reserveEntityWithNullPosGetsNoVisualEntity() = runTest {
        val s = state(entity(reserveId, pos = null, hp = 10))
        val world = VisualWorld(s, TILE_PX)
        assertFalse(world.entities.containsKey(reserveId))
        world.settle(s)
        assertFalse(world.entities.containsKey(reserveId))
    }

    @Test
    fun settleRemovesAVisualEntityThatMovedToReserve() = runTest {
        val onMap = entity(aliveId, GridPos(0, 0), hp = 10)
        val world = VisualWorld(state(onMap), TILE_PX)
        world.settle(state(onMap.copy(pos = null)))
        assertFalse(world.entities.containsKey(aliveId))
    }
}
