package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PerformTest {

    private fun meleeAttackScenario() = scenario {
        map(10, 10)
        seed(42)
        archetype("dummy") { hp = 20; ap = 2; mana = 5 }
        entity("hero") { archetype("dummy"); at(0, 0); hp(20); ap(2); mana(5) }
        entity("goblin") { archetype("dummy"); at(1, 0); hp(20) }
        initiative("hero", "goblin")
        actionDef("stab") {
            cost(ActionCost.Main, mana = 1)
            targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single)
            effect(EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, attackBonus = 20, damage = DiceSpec(1, 6, 3), damageType = DamageType.Slashing))
        }
    }

    @Test
    fun performSpendsCostFirstThenRunsInstantiatedEffects() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val result = perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)

        val completed = assertIs<StepResult.Completed>(result)
        val events = completed.resolver.emitted
        assertIs<GameEvent.ResourcesSpent>(events[0], "cost must be spent before any other effect runs")
        assertTrue(events.any { it is GameEvent.AttackRolled })

        val hero = completed.resolver.state.byId.getValue(s.id("hero"))
        assertEquals(4, hero.resources!!.mana) // 5 - 1
    }

    @Test
    fun performRejectsWithoutMutatingStateWhenCanPerformFails() {
        val s = meleeAttackScenario()
        // point out of melee range
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(9, 9))
        val result = perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)

        val rejected = assertIs<StepResult.Rejected>(result)
        assertTrue(rejected.reasons.isNotEmpty())
        assertEquals(s.state, rejected.resolver.state, "a rejected perform() must not touch state at all")
    }

    @Test
    fun previewNeverTouchesRngStateAndReportsExpectedOutcome() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val result = preview(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)

        assertEquals(s.state.rng, result.state.rng, "preview must never advance RngState")
        assertTrue(result.events.any { it is GameEvent.AttackRolled })
        // attackBonus=20 guarantees a hit in Expected mode regardless of AC, so damage must have landed.
        val goblin = result.state.byId.getValue(s.id("goblin"))
        assertTrue(goblin.health!!.current < 20, "expected-mode preview should still show the hit landing")
    }

    @Test
    fun previewIsPureAndNeverMutatesTheInputState() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val before = s.state
        preview(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)
        assertEquals(before, s.state, "GameState is immutable — preview() must not be able to mutate the caller's reference")
    }

    @Test
    fun livePerformIsDeterministicForTheSameSeed() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val resultA = assertIs<StepResult.Completed>(perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog))
        val resultB = assertIs<StepResult.Completed>(perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog))
        assertEquals(resultA.resolver.emitted, resultB.resolver.emitted)
        assertEquals(resultA.resolver.state, resultB.resolver.state)
    }
}
