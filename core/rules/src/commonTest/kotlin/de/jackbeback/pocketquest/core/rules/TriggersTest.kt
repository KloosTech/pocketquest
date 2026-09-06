package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GateId
import de.jackbeback.pocketquest.core.model.GatePlacement
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.TriggerId
import de.jackbeback.pocketquest.core.model.TriggerPlacement
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.model.WallEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** docs/36-map-triggers.md / docs/48-gates-and-wander-ai.md's multi-trigger unlock amendment. */
class TriggersTest {

    private fun stateWith(triggers: List<TriggerPlacement>, gates: List<GatePlacement> = emptyList()): GameState {
        val hero = Entity(
            id = EntityId(0),
            archetype = ArchetypeId("hero"),
            pos = GridPos(0, 0),
            health = Health(10),
            resources = Resources(ap = 2, mana = 0),
            actor = Actor(Faction.Player, Controller.Human),
        )
        return GameState(
            entities = listOf(hero),
            map = BattleMap(5, 5, triggers = triggers, gates = gates),
            turn = TurnState(round = 1, order = listOf(hero.id), activeIndex = 0, phase = TurnPhase.Start),
            rng = RngState(seed = 0),
        )
    }

    @Test
    fun firingATriggerMarksItFiredAndNeverFiresItAgain() {
        val trigger = TriggerPlacement(TriggerId("t1"), GridPos(0, 0))
        val state = stateWith(listOf(trigger))
        val (fired, _) = fireTriggerIfAny(state, EntityId(0), GridPos(0, 0), Catalog())!!
        assertTrue(TriggerId("t1") in fired.firedTriggers)
        assertEquals(null, fireTriggerIfAny(fired, EntityId(0), GridPos(0, 0), Catalog()), "already fired — never fires twice")
    }

    @Test
    fun aGateRequiringOneTriggerSynthesizesOpenGateOnThatTriggersFirstFire() {
        val trigger = TriggerPlacement(TriggerId("t1"), GridPos(0, 0))
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(1, 1), Side.East)), requiredTriggers = setOf(TriggerId("t1")))
        val state = stateWith(listOf(trigger), listOf(gate))
        val (_, effects) = fireTriggerIfAny(state, EntityId(0), GridPos(0, 0), Catalog())!!
        assertTrue(Effect.OpenGate(GateId("g1")) in effects, "the trigger that completes a gate's requirement synthesizes its OpenGate")
    }

    @Test
    fun aGateRequiringTwoTriggersOnlySynthesizesOpenGateOnceBothHaveFired() {
        val t1 = TriggerPlacement(TriggerId("t1"), GridPos(0, 0))
        val t2 = TriggerPlacement(TriggerId("t2"), GridPos(1, 0))
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(2, 2), Side.East)), requiredTriggers = setOf(TriggerId("t1"), TriggerId("t2")))
        val state = stateWith(listOf(t1, t2), listOf(gate))

        val (afterFirst, firstEffects) = fireTriggerIfAny(state, EntityId(0), GridPos(0, 0), Catalog())!!
        assertFalse(Effect.OpenGate(GateId("g1")) in firstEffects, "only one of two required triggers has fired so far")

        val (_, secondEffects) = fireTriggerIfAny(afterFirst, EntityId(0), GridPos(1, 0), Catalog())!!
        assertTrue(Effect.OpenGate(GateId("g1")) in secondEffects, "the second (completing) trigger synthesizes the gate's OpenGate")
    }

    @Test
    fun aGateWithNoRequiredTriggersIsUnaffectedByAnyTriggerFiring() {
        val trigger = TriggerPlacement(TriggerId("t1"), GridPos(0, 0))
        val gate = GatePlacement(GateId("g1"), edges = listOf(WallEdge(GridPos(2, 2), Side.East)))
        val state = stateWith(listOf(trigger), listOf(gate))
        val (_, effects) = fireTriggerIfAny(state, EntityId(0), GridPos(0, 0), Catalog())!!
        assertFalse(effects.any { it is Effect.OpenGate }, "empty requiredTriggers means only an authored OpenGate effect opens this gate")
    }
}
