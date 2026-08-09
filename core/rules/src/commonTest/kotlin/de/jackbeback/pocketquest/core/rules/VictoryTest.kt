package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VictoryTest {

    private fun entity(id: Long, faction: Faction, hp: Int) = Entity(
        id = EntityId(id), archetype = ArchetypeId("a"), pos = null,
        health = Health(hp), resources = null, actor = Actor(faction, Controller.Human),
    )

    private fun state(vararg entities: Entity) = GameState(
        entities = entities.toList(), map = BattleMap(3, 3),
        turn = TurnState(round = 1, order = entities.map { it.id }, activeIndex = 0, phase = TurnPhase.Main),
        rng = RngState(seed = 1L),
    )

    @Test
    fun ongoingWhenBothSidesHaveSomeoneStanding() {
        assertNull(state(entity(1, Faction.Player, 10), entity(2, Faction.Enemy, 10)).combatOutcome())
    }

    @Test
    fun playerVictoryWhenEveryEnemyIsAtZero() {
        assertEquals(CombatOutcome.PlayerVictory, state(entity(1, Faction.Player, 10), entity(2, Faction.Enemy, 0)).combatOutcome())
    }

    @Test
    fun playerDefeatWhenEveryPlayerIsAtZero() {
        assertEquals(CombatOutcome.PlayerDefeat, state(entity(1, Faction.Player, 0), entity(2, Faction.Enemy, 10)).combatOutcome())
    }

    @Test
    fun mutualWipeCountsAsPlayerDefeat() {
        assertEquals(CombatOutcome.PlayerDefeat, state(entity(1, Faction.Player, 0), entity(2, Faction.Enemy, 0)).combatOutcome())
    }

    @Test
    fun neutralEntitiesDoNotAffectEitherOutcome() {
        val neutral = entity(3, Faction.Neutral, 10)
        assertEquals(CombatOutcome.PlayerVictory, state(entity(1, Faction.Player, 10), entity(2, Faction.Enemy, 0), neutral).combatOutcome())
    }
}
