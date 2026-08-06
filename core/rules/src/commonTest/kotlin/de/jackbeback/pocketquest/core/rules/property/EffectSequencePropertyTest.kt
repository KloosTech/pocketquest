package de.jackbeback.pocketquest.core.rules.property

import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.checkInvariants
import de.jackbeback.pocketquest.core.rules.fixture.Scenario
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import de.jackbeback.pocketquest.core.rules.resolver.run as runResolver
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Layer 5 from docs/09-test-plan.md. Hand-rolled random trials rather than
 * a property-testing library (per discussion — no KMP-verified framework
 * in the project yet, and this doesn't need shrinking to be useful).
 * kotlin.random.Random is fine here — the "no ambient Random" rule is for
 * :core:rules production code, not test harnesses generating fixtures.
 */
class EffectSequencePropertyTest {

    private val trials = 200
    private val stepsPerTrial = 15

    private fun baseScenario(): Scenario = scenario {
        map(8, 8)
        seed(1)
        archetype("dummy") { hp = 30; ac = 12; ap = 3; mana = 5 }
        entity("a") { archetype("dummy"); at(0, 0); hp(30); ap(3); mana(5); ai() }
        entity("b") { archetype("dummy"); at(2, 2); hp(30); ap(3); mana(5); ai() }
        entity("c") { archetype("dummy"); at(4, 4); hp(30); ap(3); mana(5); ai() }
        initiative("a", "b", "c")
        statusDef("stackable") {}
    }

    private fun randomEffect(rng: Random, state: GameState, ids: List<EntityId>): Effect {
        val target = ids.random(rng)
        return when (rng.nextInt(4)) {
            0 -> Effect.DealDamage(target, rng.nextInt(1, 11), DamageType.entries.toTypedArray().random(rng))
            1 -> {
                val who = state.byId.getValue(target)
                val from = who.pos
                if (from == null) {
                    Effect.SpendCost(target, ap = rng.nextInt(0, 2))
                } else {
                    val dCol = rng.nextInt(-1, 2)
                    val dRow = rng.nextInt(-1, 2)
                    Effect.MoveAlong(target, listOf(GridPos(from.col + dCol, from.row + dRow)))
                }
            }
            2 -> Effect.ApplyStatus(target, StatusId("stackable"), stacks = rng.nextInt(1, 4), expiry = Expiry.Permanent)
            else -> Effect.SpendCost(target, ap = rng.nextInt(0, 3), mana = rng.nextInt(0, 3))
        }
    }

    @Test
    fun resolverNeverThrowsAndPreservesInvariantsUnderRandomEffectSequences() {
        repeat(trials) { trialIndex ->
            val s = baseScenario()
            val ids = s.state.entities.map { it.id }
            val rng = Random(trialIndex.toLong())
            var state = s.state

            repeat(stepsPerTrial) {
                val effect = randomEffect(rng, state, ids)
                val result = runResolver(Resolver(state, stack = listOf(effect)), s.catalog)
                val completed = assertIs<StepResult.Completed>(result, "trial $trialIndex: resolver must terminate, not throw, for a raw effect")
                state = completed.resolver.state

                val violations = checkInvariants(state, s.catalog)
                assertTrue(violations.isEmpty(), "trial $trialIndex: invariants violated after $effect: $violations")
            }
        }
    }

    @Test
    fun sameSeedAndSameEffectSequenceProduceByteIdenticalFinalState() {
        repeat(trials) { trialIndex ->
            val sA = baseScenario()
            val sB = baseScenario()
            assertEquals(sA.state, sB.state, "two freshly-built identical scenarios must start identical")

            val ids = sA.state.entities.map { it.id }
            val genRng = Random(trialIndex.toLong() + 1000) // separate stream, only used to pick WHICH effects to apply
            val effects = (0 until stepsPerTrial).map { randomEffect(genRng, sA.state, ids) }

            var stateA = sA.state
            var stateB = sB.state
            for (effect in effects) {
                stateA = assertIs<StepResult.Completed>(runResolver(Resolver(stateA, stack = listOf(effect)), sA.catalog)).resolver.state
                stateB = assertIs<StepResult.Completed>(runResolver(Resolver(stateB, stack = listOf(effect)), sB.catalog)).resolver.state
            }

            assertEquals(stateA, stateB, "trial $trialIndex: identical seed + identical effect sequence must produce identical final state")
        }
    }
}
