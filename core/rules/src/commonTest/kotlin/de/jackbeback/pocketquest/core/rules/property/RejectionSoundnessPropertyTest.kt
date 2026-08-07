package de.jackbeback.pocketquest.core.rules.property

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.action.canPerform
import de.jackbeback.pocketquest.core.rules.action.perform
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.fixture.walls
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "The highest-value property in the suite" per docs/09-test-plan.md layer
 * 5: if canPerform returns empty, perform must not Reject; and a non-empty
 * rejection list means the state is unchanged. This is what keeps the UI's
 * greyed-out buttons honest. Exercised across many random point/target
 * choices — some in range, some not, some on a wall, some on an ally, some
 * on empty ground — rather than a handful of hand-picked cases.
 */
class RejectionSoundnessPropertyTest {

    private val trials = 500

    private fun scenarioWithBlockedTile() = scenario {
        map(10, 10)
        seed(1)
        archetype("dummy") { hp = 20; ac = 12; ap = 2; mana = 5 }
        entity("hero") { archetype("dummy"); at(5, 5); hp(20); ap(2); mana(5) }
        entity("goblin") { archetype("dummy"); at(6, 5); hp(20) }
        initiative("hero", "goblin")
        actionDef("bolt") {
            cost(ActionCost.Main, mana = 2)
            targeting(TargetMode.SingleEntity, Range.Tiles(3), Shape.Single)
            effect(EffectTemplate.DealDamage(Ref.EachTarget, 5, DamageType.Fire))
        }
    }

    @Test
    fun canPerformAndPerformAgreeAcrossManyRandomTargetChoices() {
        val s = scenarioWithBlockedTile()
        val blockedState = s.state.copy(map = s.state.map.copy(terrain = walls(GridPos(7, 5))))
        val def = s.catalog.actionDef(ActionId("bolt"))
        val rng = Random(42)

        repeat(trials) { trialIndex ->
            // Random points spanning: in range, out of range, off the map entirely, on the blocked
            // tile, on the goblin, on empty ground, and occasionally no point/target at all.
            val point = when (rng.nextInt(6)) {
                0 -> null
                1 -> GridPos(rng.nextInt(-2, 12), rng.nextInt(-2, 12)) // may be off-map
                2 -> GridPos(7, 5) // the blocked tile
                3 -> GridPos(6, 5) // the goblin
                else -> GridPos(5 + rng.nextInt(-4, 5), 5 + rng.nextInt(-4, 5))
            }
            val targets = if (rng.nextBoolean()) listOf(s.id("goblin")) else emptyList()
            val ctx = ActionCtx(s.id("hero"), targets = targets, point = point)

            val rejections = canPerform(blockedState, s.id("hero"), def, ctx, s.catalog)
            val result = perform(blockedState, s.id("hero"), ActionId("bolt"), ctx, s.catalog)

            if (rejections.isEmpty()) {
                assertTrue(result !is StepResult.Rejected, "trial $trialIndex: canPerform empty but perform rejected with point=$point targets=$targets")
            } else {
                val rejected = result as? StepResult.Rejected
                assertTrue(rejected != null, "trial $trialIndex: canPerform found $rejections but perform did not reject (point=$point targets=$targets)")
                assertEquals(blockedState, rejected.resolver.state, "trial $trialIndex: a rejected perform() must never mutate state")
            }
        }
    }
}
