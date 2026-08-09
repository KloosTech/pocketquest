package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExplorationTest {

    private fun player(id: Long, pos: GridPos, ap: Int = 0, mana: Int = 3, quickUsed: Boolean = false) = Entity(
        id = EntityId(id), archetype = ArchetypeId("hero"), pos = pos,
        health = Health(10), resources = Resources(ap = ap, mana = mana, quickUsed = quickUsed), actor = Actor(Faction.Player, Controller.Human),
    )

    private fun state(vararg entities: Entity) = GameState(
        entities = entities.toList(), map = BattleMap(5, 5),
        turn = TurnState(round = 3, order = entities.map { it.id }, activeIndex = 1, phase = TurnPhase.Main),
        rng = RngState(seed = 1L),
    )

    private val hero = Archetype(
        id = ArchetypeId("hero"), name = "hero",
        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
        baseMaxHp = 10, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )
    private val catalog = Catalog(archetypes = mapOf(hero.id to hero))

    @Test
    fun moveEntityToRepositionsOnlyTheNamedEntity() {
        val heroEntity = player(1, GridPos(0, 0))
        val ally = player(2, GridPos(4, 4))
        val after = moveEntityTo(state(heroEntity, ally), heroEntity.id, GridPos(1, 0))
        assertEquals(GridPos(1, 0), after.byId.getValue(heroEntity.id).pos)
        assertEquals(GridPos(4, 4), after.byId.getValue(ally.id).pos, "an unrelated entity's position must be untouched")
    }

    @Test
    fun beginCombatRefillsApAndResetsTurnOrderButLeavesManaAlone() {
        val heroEntity = player(1, GridPos(0, 0), ap = 0, mana = 1, quickUsed = true)
        val after = beginCombat(state(heroEntity), catalog)
        val resources = after.byId.getValue(heroEntity.id).resources!!
        assertEquals(2, resources.ap, "AP refilled to the archetype's max")
        assertEquals(1, resources.mana, "mana is a per-encounter pool, untouched by this reset")
        assertFalse(resources.quickUsed)
        assertEquals(0, after.turn.activeIndex)
        assertEquals(1, after.turn.round)
    }
}
